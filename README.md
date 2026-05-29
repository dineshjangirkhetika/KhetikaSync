# Khetika Sync

## Problem Statement

Approvals across departments are currently managed through emails, WhatsApp messages, or verbal confirmations — making tracking difficult and leading to delays, missing records, and lack of accountability. There's no centralized system to monitor who approved what, what's still pending, or how the delivered output differs from what was approved.

## Solution Overview

**Khetika Sync** is a centralized Android app that turns multi-level approvals into a single, auditable workflow. A Maker submits a request with attached documents and picks 1–3 Approvers by email; each Approver receives a real-time push notification and acts (Approve / Reject / Send back) with a comment. Once a level approves, the request is automatically routed to the next level — no manual hand-off. The requester can resubmit a sent-back request with revised files, nudge a stalled approver, or delete a request that nobody has acted on yet. Every state change is logged with timestamps, surfaces in an in-app inbox, and emits an FCM push that deep-links straight into the request detail. The result is faster turnaround, complete accountability, and a searchable, status-filterable history of every approval the company has ever made.

## Architecture

```
┌────────────────────────────────────────┐
│  Android App (Jetpack Compose)         │
│  - Auth (Firebase + local PIN)         │
│  - Approvals UI                        │
│  - FCM Messaging Service               │
└──────────────┬─────────────────────────┘
               │ HTTPS (PostgREST / Storage)
               ▼
┌────────────────────────────────────────┐
│  Supabase                              │
│  ├─ Postgres (RLS, triggers, views)    │
│  ├─ Storage (approval_files bucket)    │
│  └─ Edge Function `send-push` (Deno)   │
└──────────────┬─────────────────────────┘
               │ FCM HTTP v1 (OAuth2 from service account)
               ▼
┌────────────────────────────────────────┐
│  Firebase                              │
│  ├─ Auth (Google sign-in)              │
│  └─ Cloud Messaging                    │
└──────────────┬─────────────────────────┘
               │ system push
               ▼
       Android notification tray
               │ tap
               ▼
   Request Detail screen (deep-link)
```

## Tech Stack

- **Frontend:** Android (Kotlin 2.3.21, Jetpack Compose with Material 3, Hilt 2.58 for DI, Coroutines, KSP 2.3.9, Compose BOM 2026.05.01)
- **Backend:** Supabase Edge Functions (Deno/TypeScript) — `send-push` handles FCM dispatch
- **Database:** Supabase Postgres (RLS policies, `pg_net` for HTTP triggers, a `actionable_steps` SQL view that drives approver routing)
- **Cloud / Infra:** Supabase (Postgres + Storage + Edge Functions) · Firebase (Auth + Cloud Messaging via HTTP v1) · Supabase Storage public bucket for attachments
- **AI/ML:** Not applicable for v1

## Features

- **Multi-level approval workflow (1–3 levels)** with automatic routing — once Level 1 approves, the request advances to Level 2 automatically via a SQL view, all the way through to the final approval.
- **Push notifications** via FCM for every status change (submit, approve, reject, send-back, resubmit, manual remind, final approval). Tapping a notification deep-links straight to the Request Detail screen.
- **Email-based approver selection** — type any part of an email and pick from live-suggested verified users.
- **Department auto-captured on submit** — the requester's department from their profile is automatically stamped on every new request, so it shows on every card and works with the Home department filter without the user having to pick it.
- **Multi-file attachments** uploaded to Supabase Storage; old files are kept on resubmit so the audit trail is complete.
- **Resubmit after Send Back** — requester attaches revised files + a note; all steps reset to pending and Level 1 is notified again.
- **In-app notifications inbox** with unread badge on the home bell, card-based list, and "mark all read".
- **Filterable home screen** — three sections (Pending your action / Your requests / Approved by me), filters by date (`dd/MM/yyyy`), department (20 entries), status chips, and a focused "Approved by me" view.
- **Live title search** across all requests where the user is requester or any-level approver, with debounced lookups.
- **Manual reminder** — inline bell icon on the active step and on home cards lets a requester nudge the current approver, which fires a push.
- **Dual login** — Google sign-in (Firebase Credential Manager) **or** email + 4-digit PIN (Supabase-only, no Firebase session needed).
- **Audit trail** on every step: who acted, when, with what comment.
- **Delete request** — available to the requester only when no level has approved yet; cascades to steps, files, and notifications.
- **Custom branded launcher icon + splash screen** (emerald checkmark mark).

## Setup Instructions

### 1. Prerequisites

- Android Studio Iguana or newer
- JDK 18
- A free [Supabase](https://supabase.com) project
- A free [Firebase](https://console.firebase.google.com) project with an Android app

### 2. Clone

```bash
git clone https://github.com/<your-username>/khetika-sync.git
cd khetika-sync
```

### 3. Create `local.properties` at the repo root

```properties
sdk.dir=/Users/<you>/Library/Android/sdk
SUPABASE_URL=https://<your-project-ref>.supabase.co
SUPABASE_ANON_KEY=<your-anon-key>
GOOGLE_WEB_CLIENT_ID=<your-firebase-web-client-id>.apps.googleusercontent.com
```

(The file is gitignored — your secrets stay local.)

### 4. Firebase configuration

1. Firebase Console → Project Settings → Add Android app, `applicationId = com.khetika.khetikasync`.
2. Grab your debug SHA-1:
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore \
     -alias androiddebugkey -storepass android | grep SHA1
   ```
   and paste it into the Firebase console.
3. **Authentication → Sign-in method → Google → Enable.**
4. Download `google-services.json` into `app/`.
5. Copy the **Web client ID** (`Authentication → Google → Web SDK configuration`) into `GOOGLE_WEB_CLIENT_ID` in `local.properties`.

### 5. Supabase configuration

1. **SQL Editor** → run every file in `supabase/migrations/` in order (001 → 012). They create the schema, the `actionable_steps` view, the Storage bucket policies, and the push-trigger function.
2. **(Optional) seed data:** migration 005 inserts 6 dummy verified users so the approver picker has options. Their default PIN is set to `1234` in the seeding step.

### 6. (Recommended) Push notifications

1. Generate a Firebase **service account JSON**: Project Settings → Service accounts → Generate new private key. Save locally — do not commit.
2. Install the Supabase CLI and authenticate:
   ```bash
   brew install supabase/tap/supabase
   supabase login
   supabase link --project-ref <your-project-ref>
   ```
3. Upload secrets and deploy the Edge Function:
   ```bash
   supabase secrets set FIREBASE_SERVICE_ACCOUNT="$(cat ~/khetika-sa.json)"
   supabase secrets set PUSH_SHARED_SECRET="demo"
   supabase functions deploy send-push --no-verify-jwt
   ```
4. In the Supabase **SQL Editor**, install the trigger that fans `notifications` inserts into the Edge Function — use the version in [supabase/migrations/011_push_trigger.sql](supabase/migrations/011_push_trigger.sql). It hardcodes the function URL + shared secret to bypass the dashboard's restriction on `ALTER DATABASE`.

### 7. Build and run

```bash
./gradlew assembleDebug
```

Or open the project in Android Studio and click **▶ Run app**.

Target: Android 10+ for full feature support (Android 13+ triggers the runtime notification permission prompt — the app handles it on first Home open).

---

## Repository layout

```
KhetikaSync/
├── app/                              # Android module
│   ├── google-services.json          # Firebase client config (bundled into APK)
│   └── src/main/java/com/khetika/khetikasync/
│       ├── KhetikaApplication.kt
│       ├── data/                     # repos, DTOs, auth helpers
│       ├── di/                       # Hilt modules
│       ├── messaging/                # KhetikaFirebaseMessagingService (push handler)
│       └── ui/                       # Compose screens per feature
├── supabase/
│   ├── functions/send-push/index.ts  # Edge Function: notifications → FCM
│   └── migrations/                   # 001–012 schema + seeds + triggers
└── README.md
```
