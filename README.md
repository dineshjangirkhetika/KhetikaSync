# Khetika Sync

A centralized **approval-workflow** Android app. Departments raise approval requests, multi-level approvers act on them, and every state change is tracked end-to-end with a complete audit trail, in-app notifications, and push notifications.

Built for the problem statement *"approvals across departments are currently managed through emails, WhatsApp messages, or verbal confirmations, making tracking difficult"*.

---

## Demo flow at a glance

```
Maker submits request ──► L1 approver gets push ──► L1 approves
                                                       │
                                                       ▼
                                            L2 approver gets push ──► L2 approves
                                                                          │
                                                                          ▼
                                                                L3 approver gets push ──► L3 approves
                                                                                              │
                                                                                              ▼
                                                                                  Maker gets "Request Approved" push

Any approver can also Reject or Send Back → maker gets push.
Maker can Resubmit a sent-back request → L1 gets push again.
Maker can hit Remind on a stalled request → active approver gets a push.
```

---

## Features

### Authentication
- **Continue with Google** via Firebase Auth + Credential Manager.
- **Email + 4-digit PIN** fallback (hashed with SHA-256 + email salt; works without Firebase session).
- On first login, user picks a **department** and sets a PIN in a one-time registration flow.
- Splash routes the user automatically: not signed in → Login, signed in but no profile → Registration, fully onboarded → Home.

### Home screen
- Centered "Approvals" title with a profile icon on the left and a bell on the right.
- **Three sections** that respect the active filters:
  1. *Pending your action* — requests where you're the active approver.
  2. *Your requests* — requests you've created.
  3. *Approved by me* — requests where you've already approved at least one step.
- Filters: date picker (defaults to today, format `dd/MM/yyyy`), department dropdown, status chips (Pending / Approved / Rejected / Sent back), and an "Approved by me" focus chip.
- **Search** by request title across requester + approver roles, with debounced live search.
- **FAB** opens the Create-request flow.

### Create approval request
- Title, description, **department** (20-entry list), **category** (Vendor / Legal / Expense / Procurement / HR / Other), **priority** (Normal / Urgent / High).
- **Multi-file attachments** via Android's document picker; each file is uploaded to Supabase Storage and persisted as a separate row in `approval_request_files`.
- 1, 2, or 3 approval levels.
- Per-level **typed-email approver picker** with debounced live autocomplete, **scoped to the selected department**.
- Submit fires a `pending_for_you` notification → push to L1.

### Multi-level approval engine
- A SQL view `actionable_steps` computes the "next pending step" per request — a step is only actionable once every earlier level is approved.
- Approver inbox is driven by that view; routing is **fully automatic** once L1 acts.
- Per-step actions: **Approve / Reject / Send back** with an optional comment, plus a **Remind** bell directly on the active step.

### Resubmit after Send Back
- When a request is sent back, the requester sees a Resubmit card on Detail.
- They can attach **new files**, add a comment, and resubmit; all steps reset to pending and L1 is notified again. Previous files are kept for the audit trail.

### Notifications
- In-app inbox with unread badge and a dedicated `NotificationsActivity`.
- **Push notifications** via FCM HTTP v1, dispatched from a Supabase **Edge Function** triggered by an `AFTER INSERT` trigger on the `notifications` table. Data-only payload guarantees the Android client always builds the system notification itself with a deep-link `PendingIntent` straight into Request Detail.
- Reminders are a dedicated notification type so the requester can nudge a stalled approver.

### Profile
- Avatar, name, email, department; sign-out clears both the Firebase session and the local PIN session.

### Delete
- The requester can delete a request **only while no level has approved yet**. Cascades to steps, files, and notifications.

---

## Architecture

```
┌────────────────────────┐
│  Android (Compose UI)  │  Jetpack Compose · Hilt · Coroutines · Material3
└────────────────────────┘
            │
            ▼
┌────────────────────────┐
│ Supabase (Postgres +   │  Postgrest queries · RLS · Storage · pg_net
│ Storage + Edge Funcs)  │  Edge Function `send-push` (Deno/TS)
└────────────────────────┘
            │
            ▼
┌────────────────────────┐
│ Firebase (Auth + FCM)  │  Google sign-in · Cloud Messaging
└────────────────────────┘
```

### Tech stack

| Layer | Choices |
|---|---|
| Language | Kotlin 2.3.21 |
| UI | Jetpack Compose (BOM 2026.05.01), Material 3 |
| DI | Hilt 2.58 + KSP 2.3.9 |
| Networking | supabase-kt 3.6 (postgrest, auth, storage) + Ktor 3.5 |
| Auth | Firebase Auth + Credential Manager (Google sign-in), local PIN sessions in SharedPreferences |
| Push | Firebase Cloud Messaging via Supabase Edge Function (HTTP v1) |
| Storage | Supabase Storage (public bucket) |
| DB | Supabase Postgres + pg_net for trigger HTTP |
| Build | AGP 8.10.1 · Compose Compiler plugin |

### Project layout

```
KhetikaSync/
├── app/
│   ├── src/main/java/com/khetika/khetikasync/
│   │   ├── KhetikaApplication.kt
│   │   ├── data/
│   │   │   ├── approval/    # ApprovalRepository (queries + state engine)
│   │   │   ├── auth/        # AppUser, AuthRepository (Firebase + PIN), PinHasher
│   │   │   ├── file/        # FileRepository (Storage upload), PickedFile
│   │   │   ├── model/       # DTOs: User, ApprovalRequest, Step, File, Notification
│   │   │   ├── notification/# NotificationRepository
│   │   │   └── user/        # UserRepository (search by email, FCM token, PIN)
│   │   ├── di/              # Hilt modules: Supabase client, Firebase, SharedPreferences
│   │   ├── messaging/       # KhetikaFirebaseMessagingService (tray + deep-link)
│   │   └── ui/
│   │       ├── create/      # Create Approval Request screen
│   │       ├── detail/      # Request Detail + step actions + resubmit + remind + delete
│   │       ├── home/        # Home with filters + sections + bell
│   │       ├── login/       # Email+PIN and Google sign-in
│   │       ├── notifications/  # Notifications screen
│   │       ├── profile/     # Profile + sign-out
│   │       ├── registration/   # Dept + PIN setup
│   │       └── splash/      # Auth-aware routing
│   ├── google-services.json # Firebase config (safe to commit; bundled into APK)
│   └── build.gradle.kts
├── supabase/
│   ├── functions/
│   │   └── send-push/index.ts   # Edge Function: queue notifications row → FCM push
│   └── migrations/
│       ├── 001_users.sql
│       ├── 002_is_verified.sql
│       ├── 003_fcm_token.sql
│       ├── 004_approvals.sql
│       ├── 005_dummy_users.sql
│       ├── 006_category_priority_and_view.sql
│       ├── 007_storage_bucket.sql
│       ├── 008_notifications_and_files.sql
│       ├── 009_pin_hash.sql
│       ├── 010_reminder_notification.sql
│       ├── 011_push_trigger.sql  (use inline version in README §Setup-4 if dashboard blocks ALTER DATABASE)
│       └── 012_update_departments.sql
└── README.md
```

---

## Database schema (high-level)

| Table | Purpose | Notable columns |
|---|---|---|
| `users` | One row per signed-in person | `firebase_uid`, `email`, `display_name`, `department`, `role`, `is_verified`, `fcm_token`, `pin_hash` |
| `approval_requests` | One row per request | `requester_uid`, `title`, `department`, `category`, `priority`, `levels`, `status` |
| `approval_steps` | One row per (request × level) | `request_id`, `level`, `approver_uid`, `status`, `note`, `acted_at` |
| `approval_request_files` | Multi-file attachments | `request_id`, `file_name`, `file_url`, `size_bytes` |
| `notifications` | In-app inbox + push fan-out | `recipient_uid`, `type`, `title`, `body`, `request_id`, `is_read` |
| `actionable_steps` *(view)* | Next-pending step per request | computed; powers the approver inbox |

RLS is enabled on all tables; for the demo, policies are permissive (`for all to anon`). Lock these down before going to production.

---

## Setup

### 1. Local properties

Create `local.properties` at the repo root with:

```properties
sdk.dir=/Users/<you>/Library/Android/sdk

SUPABASE_URL=https://<your-project-ref>.supabase.co
SUPABASE_ANON_KEY=<your-anon-key>
GOOGLE_WEB_CLIENT_ID=<your-firebase-web-client-id>.apps.googleusercontent.com
```

These are read by `app/build.gradle.kts` and exposed as `BuildConfig` fields.

### 2. Firebase

- Create a Firebase project, add an Android app with `applicationId = com.khetika.khetikasync`.
- Add your debug SHA-1 (run `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`).
- Enable **Authentication → Sign-in method → Google**.
- Download `google-services.json` into `app/`.
- Copy the Web client ID into `local.properties`.

### 3. Supabase

Apply migrations in order from `supabase/migrations/` via Dashboard → SQL Editor.

Create the storage bucket from migration `007`. Seed test users from `005` if needed.

### 4. Push notifications (optional but recommended)

1. Generate a Firebase service-account JSON: Firebase Console → Project settings → Service accounts → Generate new private key. Save it locally (don't commit).
2. Install Supabase CLI: `brew install supabase/tap/supabase`.
3. Authenticate + link the project:
   ```bash
   supabase login
   supabase link --project-ref <your-project-ref>
   ```
4. Upload secrets:
   ```bash
   supabase secrets set FIREBASE_SERVICE_ACCOUNT="$(cat <path-to-sa-json>)"
   supabase secrets set PUSH_SHARED_SECRET="demo"
   ```
5. Deploy the Edge Function:
   ```bash
   supabase functions deploy send-push --no-verify-jwt
   ```
6. In the SQL Editor, install the trigger that fans out from `notifications` → Edge Function. If the dashboard blocks `ALTER DATABASE`, use this inline version that hardcodes the URL and secret:

   ```sql
   create extension if not exists pg_net;

   create or replace function public.notify_send_push() returns trigger
   language plpgsql security definer as $$
   begin
       perform net.http_post(
           url := 'https://<your-project-ref>.functions.supabase.co/send-push',
           headers := jsonb_build_object(
               'content-type', 'application/json',
               'x-shared-secret', 'demo'
           ),
           body := jsonb_build_object('record', row_to_json(new))
       );
       return new;
   exception when others then
       raise notice 'notify_send_push failed: %', sqlerrm;
       return new;
   end;
   $$;

   drop trigger if exists notifications_send_push on public.notifications;
   create trigger notifications_send_push
       after insert on public.notifications
       for each row execute function public.notify_send_push();
   ```

### 5. Build + run

```bash
./gradlew assembleDebug
# or, in Android Studio: Run ▶ app
```

The target device should be Android 10+ for full feature support (Android 13+ for runtime notification permission prompt, which the app handles).

---

## Notification model

Every state change inserts a row into `notifications`. The trigger calls the Edge Function, which dispatches a data-only FCM push. The Android client always builds the system notification itself, attaching a deep-link `PendingIntent` into the Request Detail screen.

| Event | Recipient | Type |
|---|---|---|
| Maker submits | L1 approver | `pending_for_you` |
| Step approved & next pending | Next approver | `pending_for_you` |
| Final step approved | Maker | `approved` |
| Step rejected | Maker | `rejected` |
| Step sent back | Maker | `sent_back` |
| Maker resubmits | L1 approver | `resubmitted` |
| Maker taps Remind | Active approver | `reminder` |

Tap any tray notification → opens directly to Request Detail, back stack intact.

---

## Known limitations / future work

- **Deviation tracking** (planned stage 8): comparing the approved request vs the delivered output is not implemented.
- **Dashboards / MIS reports** (planned stage 9): counts, turnaround, bottleneck approvers — UI scaffolding not yet built.
- **Escalation timers** would need a scheduled job (Supabase pg_cron or scheduled Edge Function).
- **iOS port**: see the [Kotlin Multiplatform migration brief](#kotlin-multiplatform-migration) below.
- **Production RLS** policies are not in place; the current ones are wide open for demo simplicity. Lock per-table and per-action before shipping.
- **Storage cleanup** for orphan files when a request is deleted is not yet automated.

### Kotlin Multiplatform migration

If you want this to also run on iOS, see the prompt in `docs/kmp-migration.md` (or in chat history) for a full rewrite blueprint using Compose Multiplatform + Koin + Supabase-kt + a Firebase Kotlin wrapper.

---

## License

Internal/demo project. No license attached.

---

## Credits

Built collaboratively over an extended pair-programming session with Claude as the implementation assistant. Specs and product direction by the Khetika team.
