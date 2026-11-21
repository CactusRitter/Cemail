// Cemail push relay (Cloudflare Worker, free plan).
//
// Gmail publishes "something changed" to a Google Pub/Sub topic; Pub/Sub calls POST /pubsub/<PUBSUB_SECRET> here.
// Microsoft Graph calls POST /graph when a message arrives in a subscribed Inbox. In both cases this worker looks up
// which phones follow that address and wakes each one with a Firebase Cloud Messaging data message; the phone then
// syncs by itself. No mail content ever passes through here: only the address, a history number or message id, and
// the phone's FCM token.
//
// Settings (Worker → Settings → Variables and Secrets / Bindings):
//   KV namespace binding  KV            address → list of FCM tokens, and Graph subscription → address
//   Secret                APP_KEY       shared with the app (registration)
//   Secret                PUBSUB_SECRET random text that is part of the address Pub/Sub calls
//   Secret                FCM_SA        the Firebase service account JSON (whole file)
//   Secret                CAL_APP_KEY   shared with Celander (calendar registration, header X-Celander-Key)
//
// Celander calendar push uses its own KV prefixes ("cal:") and routes; Cemail's are untouched:
//   POST /cal/register    {token, accounts:[{email, provider, channels:[{id,token}] | subscription:{id,clientState}}], previous?:[email]}
//   POST /cal/unregister  {token, emails:[email]}
//   POST /gcal            Google Calendar webhook (X-Goog-Channel-ID / -Token / Resource-State) -> FCM type=gcal
//   POST /graph           shared with mail; a subscription found under cal:ms:<id> -> FCM type=mscal

const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const MAX_TOKENS_PER_ADDRESS = 10;
const SUBSCRIPTION_TTL_SECONDS = 4 * 24 * 3600; // Graph subscriptions last under 3 days; the app registers again daily
const CHANNEL_TTL_SECONDS = 8 * 24 * 3600; // Google Calendar channels last about a week; the app renews them
const MAX_CAL_ACCOUNTS = 20;
const MAX_CAL_CHANNELS = 50;

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    try {
      if (request.method === "POST" && url.pathname === "/register") return await register(request, env, true);
      if (request.method === "POST" && url.pathname === "/unregister") return await register(request, env, false);
      if (request.method === "POST" && url.pathname === "/register-ms") return await registerSubscription(request, env);
      if (request.method === "POST" && url.pathname === `/pubsub/${env.PUBSUB_SECRET}`) return await fromPubSub(request, env);
      if (request.method === "POST" && url.pathname === "/cal/register") return await calRegister(request, env, true);
      if (request.method === "POST" && url.pathname === "/cal/unregister") return await calRegister(request, env, false);
      if (request.method === "POST" && url.pathname === "/gcal") return await fromGoogleCalendar(request, env, ctx);
      if (request.method === "POST" && url.pathname === "/graph") return await fromGraph(request, env, ctx, url);
      if (request.method === "GET" && url.pathname === "/") return new Response("Cemail push relay", { status: 200 });
    } catch (e) {
      console.log("error", String(e && e.stack || e));
      return new Response("error", { status: 500 });
    }
    return new Response("not found", { status: 404 });
  },
};

const key = (address) => `em:${address.toLowerCase()}`;
const calKey = (address) => `cal:em:${address.toLowerCase()}`;

async function readTokens(env, address, keyOf = key) {
  const raw = await env.KV.get(keyOf(address));
  return raw ? JSON.parse(raw) : [];
}

async function writeTokens(env, address, tokens, keyOf = key) {
  if (tokens.length === 0) await env.KV.delete(keyOf(address));
  else await env.KV.put(keyOf(address), JSON.stringify(tokens.slice(-MAX_TOKENS_PER_ADDRESS)));
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

/** A Microsoft account's Graph subscription: which address it belongs to, and the secret Microsoft sends back with it. */
async function registerSubscription(request, env) {
  if (request.headers.get("X-Cemail-Key") !== env.APP_KEY) return new Response("forbidden", { status: 403 });
  const body = await request.json();
  const id = String(body.subscriptionId || "");
  const email = String(body.email || "");
  const state = String(body.clientState || "");
  if (id.length < 8 || id.length > 200 || !email || state.length < 8) return new Response("bad request", { status: 400 });
  await env.KV.put(`ms:${id}`, JSON.stringify({ email: email.toLowerCase(), state }), { expirationTtl: SUBSCRIPTION_TTL_SECONDS });
  return new Response("ok");
}

/**
 * Celander: "this phone (token) follows these calendar accounts", plus each account's push channels (Google) or
 * subscription (Microsoft) with the secret the provider sends back. [on] false removes the token from [emails].
 */
async function calRegister(request, env, on) {
  if (!env.CAL_APP_KEY || request.headers.get("X-Celander-Key") !== env.CAL_APP_KEY) return new Response("forbidden", { status: 403 });
  const body = await request.json();
  const token = String(body.token || "");
  const accounts = on && Array.isArray(body.accounts) ? body.accounts : [];
  const emails = accounts.map((a) => String((a && a.email) || "").toLowerCase());
  const listed = !on && Array.isArray(body.emails) ? body.emails.map(String) : [];
  if (token.length < 20 || accounts.length > MAX_CAL_ACCOUNTS || listed.length > MAX_CAL_ACCOUNTS || emails.some((e) => !e)) {
    return new Response("bad request", { status: 400 });
  }
  const previous = Array.isArray(body.previous) ? body.previous.map(String) : [];
  // A token follows exactly the accounts of this call: drop it from the ones that are no longer listed.
  for (const address of new Set([...emails, ...previous, ...listed])) {
    const list = (await readTokens(env, address, calKey)).filter((t) => t !== token);
    if (emails.includes(address.toLowerCase())) list.push(token);
    await writeTokens(env, address, list, calKey);
  }
  for (const a of accounts) {
    const email = String(a.email).toLowerCase();
    if (a.provider === "google") {
      const channels = Array.isArray(a.channels) ? a.channels : [];
      if (channels.length > MAX_CAL_CHANNELS) return new Response("bad request", { status: 400 });
      for (const c of channels) {
        const id = String((c && c.id) || "");
        const secret = String((c && c.token) || "");
        if (id.length < 8 || id.length > 200 || secret.length < 8) continue;
        await env.KV.put(`cal:g:${id}`, JSON.stringify({ email, token: secret }), { expirationTtl: CHANNEL_TTL_SECONDS });
      }
    } else if (a.provider === "microsoft" && a.subscription) {
      const id = String(a.subscription.id || "");
      const state = String(a.subscription.clientState || "");
      if (id.length < 8 || id.length > 200 || state.length < 8) continue;
      await env.KV.put(`cal:ms:${id}`, JSON.stringify({ email, state }), { expirationTtl: SUBSCRIPTION_TTL_SECONDS });
    }
  }
  return new Response("ok");
}

/**
 * Google Calendar push: headers only (the body is empty). "sync" is the handshake after events.watch and is ignored.
 * The channel token must match what the app registered; otherwise we answer 200 and do nothing (no hints to strangers).
 */
async function fromGoogleCalendar(request, env, ctx) {
  const channelId = request.headers.get("X-Goog-Channel-ID") || "";
  const state = request.headers.get("X-Goog-Resource-State") || "";
  const secret = request.headers.get("X-Goog-Channel-Token") || "";
  if (!channelId || state === "sync") return new Response("ok");
  ctx.waitUntil((async () => {
    const raw = await env.KV.get(`cal:g:${channelId}`);
    if (!raw) return;
    const channel = JSON.parse(raw);
    if (!secret || channel.token !== secret) return;
    await wake(env, channel.email, { type: "gcal", email: channel.email, channelId }, calKey);
  })());
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
  await wake(env, address, { type: "gmail", email: address, historyId: String(note.historyId || "") });
  return new Response("ok");
}

/**
 * Microsoft Graph change notifications. When a subscription is created Microsoft first calls with ?validationToken=...
 * and wants it echoed back as plain text within seconds; after that every notification is a POST with
 * {"value":[{subscriptionId, clientState, resource, ...}]} that must be answered 202 quickly (the work goes on after).
 */
async function fromGraph(request, env, ctx, url) {
  const token = url.searchParams.get("validationToken");
  if (token) return new Response(token, { status: 200, headers: { "Content-Type": "text/plain" } });
  const body = await request.json();
  const notes = Array.isArray(body.value) ? body.value : [];
  ctx.waitUntil((async () => {
    const done = new Set();
    for (const n of notes) {
      const calRaw = await env.KV.get(`cal:ms:${n.subscriptionId}`);
      const raw = calRaw || await env.KV.get(`ms:${n.subscriptionId}`);
      if (!raw) continue; // unknown or expired subscription
      const sub = JSON.parse(raw);
      const id = `${calRaw ? "cal" : "mail"}:${sub.email}`;
      if (sub.state !== n.clientState || done.has(id)) continue; // not ours, or already announced in this call
      done.add(id);
      if (calRaw) await wake(env, sub.email, { type: "mscal", email: sub.email }, calKey);
      else await wake(env, sub.email, { type: "microsoft", email: sub.email });
    }
  })());
  return new Response(null, { status: 202 });
}

/** Sends [data] as a high-priority FCM message to every phone that follows [address]. */
async function wake(env, address, data, keyOf = key) {
  const tokens = await readTokens(env, address, keyOf);
  if (tokens.length === 0) return;
  const access = await accessToken(env);
  const sa = JSON.parse(env.FCM_SA);
  const dead = [];
  await Promise.all(tokens.map(async (token) => {
    const res = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
      method: "POST",
      headers: { Authorization: `Bearer ${access}`, "Content-Type": "application/json" },
      body: JSON.stringify({ message: { token, data, android: { priority: "HIGH" } } }),
    });
    if (res.status === 404 || res.status === 400) dead.push(token); // phone gone or token expired
    else if (!res.ok) console.log("fcm", res.status, await res.text());
  }));
  if (dead.length) await writeTokens(env, address, tokens.filter((t) => !dead.includes(t)), keyOf);
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
