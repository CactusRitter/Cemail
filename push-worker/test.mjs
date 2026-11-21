// Offline tests for worker.js: `node push-worker/test.mjs` (Node 18+). KV and FCM are faked; nothing leaves the machine.
import assert from "node:assert/strict";
import { generateKeyPairSync } from "node:crypto";
import worker from "./worker.js";

const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
const SA = JSON.stringify({
  project_id: "p", client_email: "sa@p.iam", private_key: privateKey.export({ type: "pkcs8", format: "pem" }),
});

const store = new Map();
const KV = {
  get: async (k) => store.get(k) ?? null,
  put: async (k, v) => void store.set(k, v),
  delete: async (k) => void store.delete(k),
};
const env = { KV, APP_KEY: "mail-key", CAL_APP_KEY: "cal-key", PUBSUB_SECRET: "ps", FCM_SA: SA };

let sent = [];
globalThis.fetch = async (url, init) => {
  if (String(url).includes("oauth2.googleapis.com")) return Response.json({ access_token: "at", expires_in: 3000 });
  sent.push(JSON.parse(init.body).message);
  return new Response("{}");
};

const pending = [];
const ctx = { waitUntil: (p) => pending.push(p) };
const call = async (path, { method = "POST", headers = {}, body } = {}) => {
  const res = await worker.fetch(new Request("https://w" + path, {
    method, headers: { "Content-Type": "application/json", ...headers }, body: body === undefined ? undefined : JSON.stringify(body),
  }), env, ctx);
  await Promise.all(pending.splice(0));
  return res;
};
const calHdr = { "X-Celander-Key": "cal-key" };
const TOKEN = "t".repeat(30);
const reg = (extra = {}) => call("/cal/register", {
  headers: calHdr,
  body: {
    token: TOKEN,
    accounts: [
      { email: "A@x.com", provider: "google", channels: [{ id: "chan-12345", token: "secret-123" }] },
      { email: "b@y.com", provider: "microsoft", subscription: { id: "sub-12345", clientState: "state-1234" } },
    ],
    ...extra,
  },
});
const gcal = (h) => call("/gcal", { headers: h });

// registration
assert.equal((await call("/cal/register", { body: { token: TOKEN, accounts: [] } })).status, 403);
assert.equal((await call("/cal/register", { headers: { "X-Cemail-Key": "mail-key" }, body: { token: TOKEN, accounts: [] } })).status, 403);
assert.equal((await call("/cal/register", { headers: calHdr, body: { token: "short", accounts: [] } })).status, 400);
assert.equal((await reg()).status, 200);
assert.deepEqual(JSON.parse(store.get("cal:em:a@x.com")), [TOKEN]);
assert.ok(store.has("cal:g:chan-12345") && store.has("cal:ms:sub-12345"));
assert.ok(![...store.keys()].some((k) => k.startsWith("em:") || k.startsWith("ms:")), "mail keys untouched");

// Google webhook
const good = { "X-Goog-Channel-ID": "chan-12345", "X-Goog-Channel-Token": "secret-123", "X-Goog-Resource-State": "exists" };
assert.equal((await gcal({ ...good, "X-Goog-Resource-State": "sync" })).status, 200);
assert.equal((await gcal({ ...good, "X-Goog-Channel-Token": "wrong" })).status, 200);
assert.equal((await gcal({ ...good, "X-Goog-Channel-ID": "unknown-id" })).status, 200);
assert.equal(sent.length, 0, "no push for sync / bad token / unknown channel");
assert.equal((await gcal(good)).status, 200);
assert.equal(sent.length, 1);
assert.deepEqual(sent[0].data, { type: "gcal", email: "a@x.com", channelId: "chan-12345" });
assert.equal(sent[0].android.priority, "HIGH");

// Graph: calendar subscription -> mscal, mail subscription -> microsoft
store.set("ms:mailsub-1", JSON.stringify({ email: "m@z.com", state: "mailstate1" }));
store.set("em:m@z.com", JSON.stringify(["m".repeat(30)]));
sent = [];
const graph = (v) => call("/graph", { body: { value: v } });
assert.equal(await (await call("/graph?validationToken=abc")).text(), "abc");
assert.equal((await graph([{ subscriptionId: "sub-12345", clientState: "bad" }])).status, 202);
assert.equal(sent.length, 0);
await graph([{ subscriptionId: "sub-12345", clientState: "state-1234" }, { subscriptionId: "sub-12345", clientState: "state-1234" }]);
assert.equal(sent.length, 1);
assert.deepEqual(sent[0].data, { type: "mscal", email: "b@y.com" });
sent = [];
await graph([{ subscriptionId: "mailsub-1", clientState: "mailstate1" }]);
assert.deepEqual(sent.map((m) => m.data), [{ type: "microsoft", email: "m@z.com" }]);

// a token follows exactly the accounts of its last call; unregister removes it
await call("/cal/register", { headers: calHdr, body: { token: TOKEN, accounts: [{ email: "c@x.com", provider: "google" }], previous: ["a@x.com", "b@y.com"] } });
assert.equal(store.has("cal:em:a@x.com"), false);
assert.deepEqual(JSON.parse(store.get("cal:em:c@x.com")), [TOKEN]);
assert.equal((await call("/cal/unregister", { headers: calHdr, body: { token: TOKEN, emails: ["c@x.com"] } })).status, 200);
assert.equal(store.has("cal:em:c@x.com"), false);

// Cemail's own routes keep working and stay isolated from cal:
assert.equal((await call("/register", { headers: { "X-Cemail-Key": "mail-key" }, body: { token: TOKEN, emails: ["q@x.com"] } })).status, 200);
assert.deepEqual(JSON.parse(store.get("em:q@x.com")), [TOKEN]);
assert.equal((await call("/register", { headers: calHdr, body: { token: TOKEN, emails: ["q@x.com"] } })).status, 403);
assert.equal((await call("/", { method: "GET" })).status, 200);

console.log("push-worker tests ok");
