// Supabase Edge Function: send-push
//
// Triggered by the DB after a row is inserted into public.notifications.
// Reads the recipient's fcm_token, mints a Google OAuth2 access token using
// the Firebase service account, and dispatches a single FCM HTTP v1 message.
//
// Env (set via `supabase secrets set …`):
//   FIREBASE_SERVICE_ACCOUNT  raw JSON string of the service account key
//   PUSH_SHARED_SECRET        any random string; the trigger sends it as a header
//   SUPABASE_URL              auto-injected by Supabase
//   SUPABASE_SERVICE_ROLE_KEY auto-injected by Supabase
//
// Deploy:
//   supabase functions deploy send-push --no-verify-jwt
//
// (We skip verify-jwt because the trigger calls us with a custom shared
// secret header instead of a Supabase JWT.)

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";

interface NotificationRow {
  id: string;
  recipient_uid: string;
  type: string;
  title: string;
  body?: string | null;
  request_id?: string | null;
}

const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

Deno.serve(async (req) => {
  try {
    const sharedSecret = Deno.env.get("PUSH_SHARED_SECRET");
    if (!sharedSecret) return j(500, { error: "PUSH_SHARED_SECRET not set" });
    if (req.headers.get("x-shared-secret") !== sharedSecret) {
      return j(401, { error: "bad shared secret" });
    }

    const payload = await req.json();
    // Supabase pg_net delivers the row under "record" by convention.
    const row: NotificationRow = payload.record ?? payload;
    if (!row?.recipient_uid || !row?.title) {
      return j(400, { error: "missing fields", got: row });
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const { data: user, error: userErr } = await supabase
      .from("users")
      .select("fcm_token")
      .eq("firebase_uid", row.recipient_uid)
      .maybeSingle();
    if (userErr) return j(500, { error: "user lookup failed", detail: userErr.message });
    const token = user?.fcm_token;
    if (!token) {
      // No device registered — silently OK; the in-app notification still lives.
      return j(200, { skipped: true, reason: "no fcm_token for recipient" });
    }

    const sa = JSON.parse(Deno.env.get("FIREBASE_SERVICE_ACCOUNT") ?? "{}");
    if (!sa.client_email || !sa.private_key || !sa.project_id) {
      return j(500, { error: "invalid FIREBASE_SERVICE_ACCOUNT" });
    }

    const accessToken = await getAccessToken(sa);
    const fcmResp = await fetch(
      `https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`,
      {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          message: {
            token,
            // Data-only payload (no `notification` block).
            // This forces the Android client's onMessageReceived to fire in
            // every state — foreground, background, killed — so our service
            // builds the system notification with a deep-link PendingIntent
            // straight into RequestDetailActivity.
            data: {
              title: row.title,
              body: row.body ?? "",
              type: row.type,
              request_id: row.request_id ?? "",
              notification_id: row.id,
            },
            android: {
              priority: "HIGH",
            },
          },
        }),
      },
    );

    const text = await fcmResp.text();
    if (!fcmResp.ok) {
      return j(502, { error: "FCM rejected", status: fcmResp.status, body: text });
    }
    return j(200, { ok: true, fcm: text });
  } catch (e) {
    return j(500, { error: String(e?.message ?? e) });
  }
});

function j(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

// ---------- Google OAuth2 helpers (service-account JWT → access token) ----------

async function getAccessToken(sa: { client_email: string; private_key: string }): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const claim = {
    iss: sa.client_email,
    scope: FCM_SCOPE,
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  };
  const header = { alg: "RS256", typ: "JWT" };
  const enc = (o: unknown) => b64url(new TextEncoder().encode(JSON.stringify(o)));
  const signingInput = `${enc(header)}.${enc(claim)}`;

  const key = await importPrivateKey(sa.private_key);
  const sig = await crypto.subtle.sign(
    { name: "RSASSA-PKCS1-v1_5" },
    key,
    new TextEncoder().encode(signingInput),
  );
  const jwt = `${signingInput}.${b64url(new Uint8Array(sig))}`;

  const tokenResp = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });
  if (!tokenResp.ok) {
    throw new Error(`oauth2 token exchange failed: ${tokenResp.status} ${await tokenResp.text()}`);
  }
  const tokenJson = await tokenResp.json();
  return tokenJson.access_token as string;
}

async function importPrivateKey(pem: string): Promise<CryptoKey> {
  const cleaned = pem
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s+/g, "");
  const der = base64ToBytes(cleaned);
  return crypto.subtle.importKey(
    "pkcs8",
    der,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
}

function b64url(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes))
    .replace(/=+$/, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
}

function base64ToBytes(b64: string): Uint8Array {
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}
