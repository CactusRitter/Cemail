// Cemail push relay (Cloudflare Worker, free plan).
//
// Gmail publishes "something changed" to a Google Pub/Sub topic; Pub/Sub calls POST /pubsub/<PUBSUB_SECRET> here; this
// worker looks up which phones follow that address and wakes each one with a Firebase Cloud Messaging data message.
// The phone then syncs by itself. No mail content ever passes through here: only the address, a history number and
// the phone's FCM token.
//
// Settings (Worker → Settings → Variables and Secrets / Bindings):
//   KV namespace binding  KV            address → list of FCM tokens
//   Secret                APP_KEY       shared with the app (registration)
//   Secret                PUBSUB_SECRET random text that is part of the address Pub/Sub calls
//   Secret                FCM_SA        the Firebase service account JSON (whole file)

const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const MAX_TOKENS_PER_ADDRESS = 10;

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    try {
      if (request.method === "POST" && url.pathname === "/register") return await register(request, env, true);
      if (request.method === "POST" && url.pathname === "/unregister") return await register(request, env, false);
      if (request.method === "POST" && url.pathname === `/pubsub/${env.PUBSUB_SECRET}`) return await fromPubSub(request, env);
      if (request.method === "GET" && url.pathname === "/") return new Response("Cemail push relay", { status: 200 });
    } catch (e) {
      console.log("error", String(e && e.stack || e));
      return new Response("error", { status: 500 });
    }
    return new Response("not found", { status: 404 });
  },
};

const key = (address) => `em:${address.toLowerCase()}`;

async function readTokens(env, address) {
  const raw = await env.KV.get(key(address));
  return raw ? JSON.parse(raw) : [];
}

async function writeTokens(env, address, tokens) {
  if (tokens.length === 0) await env.KV.delete(key(address));
  else await env.KV.put(key(address), JSON.stringify(tokens.slice(-MAX_TOKENS_PER_ADDRESS)));
}

/** The app says "this phone (token) follows these addresses". [on] false removes it. */
async function register(request, env, on) {
  if (request.headers.get("X-Cemail-Key") !== env.APP_KEY) return new Response("forbidden", { status: 403 });
  const body = await request.json();
  const token = String(body.token || "");
  const emails = Array.isArray(body.emails) ? body.emails.map(String) : [];
  if (token.length < 20 || emails.length > 20) return new Response("bad request", { status: 400 });
  // A token follows exactly the addresses of this call: drop it from the ones that are no longer listed.
  const known = Array.isArray(body.previous) ? body.previous.map(String) : [];
  for (const address of new Set([...emails, ...known])) {
    const list = (await readTokens(env, address)).filter((t) => t !== token);
    if (on && emails.some((e) => e.toLowerCase() === address.toLowerCase())) list.push(token);
    await writeTokens(env, address, list);
  }
  return new Response("ok");
}

/** Pub/Sub push: {"message":{"data":"base64({emailAddress,historyId})"},...}. */
async function fromPubSub(request, env) {
  const envelope = await request.json();
  const data = envelope && envelope.message && envelope.message.data;
  if (!data) return new Response("ok"); // nothing to do; answering 2xx stops Pub/Sub from retrying
  const note = JSON.parse(atob(data));
  const address = String(note.emailAddress || "");
  if (!address) return new Response("ok");
  const tokens = await readTokens(env, address);
  if (tokens.length === 0) return new Response("ok");
  const access = await accessToken(env);
  const sa = JSON.parse(env.FCM_SA);
  const dead = [];
  await Promise.all(tokens.map(async (token) => {
    const res = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
      method: "POST",
      headers: { Authorization: `Bearer ${access}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        message: {
          token,
          data: { type: "gmail", email: address, historyId: String(note.historyId || "") },
          android: { priority: "HIGH" },
        },
      }),
    });
    if (res.status === 404 || res.status === 400) dead.push(token); // phone gone or token expired
    else if (!res.ok) console.log("fcm", res.status, await res.text());
  }));
  if (dead.length) await writeTokens(env, address, tokens.filter((t) => !dead.includes(t)));
  return new Response("ok");
}

// --- Google OAuth for FCM: a signed JWT exchanged for an access token (kept for ~50 minutes) ---

let cached = { token: null, until: 0 };

async function accessToken(env) {
  const now = Math.floor(Date.now() / 1000);
  if (cached.token && cached.until > now + 60) return cached.token;
  const sa = JSON.parse(env.FCM_SA);
  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = b64url(JSON.stringify({
    iss: sa.client_email, scope: FCM_SCOPE, aud: "https://oauth2.googleapis.com/token", iat: now, exp: now + 3600,
  }));
  const signingKey = await crypto.subtle.importKey(
    "pkcs8", pem(sa.private_key), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"],
  );
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", signingKey, new TextEncoder().encode(`${header}.${claims}`));
  const jwt = `${header}.${claims}.${b64url(signature)}`;
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: jwt }),
  });
  if (!res.ok) throw new Error(`token ${res.status} ${await res.text()}`);
  const json = await res.json();
  cached = { token: json.access_token, until: now + (json.expires_in || 3000) };
  return cached.token;
}

function b64url(input) {
  const bytes = typeof input === "string" ? new TextEncoder().encode(input) : new Uint8Array(input);
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function pem(text) {
  const body = text.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const raw = atob(body);
  const bytes = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
  return bytes.buffer;
}
