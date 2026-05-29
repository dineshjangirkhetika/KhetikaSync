# Khetika Sync — Execution Plan

**Repository:** https://github.com/dineshjangirkhetika/KhetikaSync

---

## 1. Project at a glance

| | |
|---|---|
| **Product** | Khetika Sync — a centralized Android approval-workflow app |
| **Platform** | Android (Kotlin + Jetpack Compose), Supabase backend, Firebase auth + push |
| **Repository** | https://github.com/dineshjangirkhetika/KhetikaSync |
| **Target users** | All departments, Legal, Finance, Procurement, HR, Management, etc. |
| **Status** | Demo-ready end-to-end. All core stages of the brief implemented. |

---

## 2. Problem statement

Approvals across departments are managed through emails, WhatsApp messages, or verbal confirmations — making tracking difficult and causing delays, missing records, and lack of accountability. There is no centralized system to monitor who approved what, what is pending, or how the delivered output differs from what was approved.

---

## 3. Solution overview

A single Android app that turns multi-level approvals into a structured, auditable workflow:

- A **Maker** submits a request with attached documents, picks a category and priority, and selects 1–3 **Approvers** by email.
- Each Approver gets an in-app + real push notification with a direct deep-link into the request detail screen, where they can **Approve / Reject / Send back** with a comment.
- Once a level approves, the request is automatically routed to the next level — no manual hand-off.
- The requester can **Resubmit** a sent-back request with new files, **Remind** a stalled approver with one tap, or **Delete** a request that no one has approved yet.
- Every state change is logged with timestamp, actor, and comment, and is surfaced in a card-based notification inbox with an unread badge.

The result: faster decisions, complete accountability, and a searchable, filterable history of every approval in the company.

---

## 4. Execution plan — phased delivery

| Phase | Outcome | Status |
|---|---|---|
| **0. Foundations** | Kotlin 2.3.21 / Gradle / Compose / Hilt / Supabase-kt / Ktor wiring | ✅ |
| **1. Auth + Profile** | Firebase Google sign-in via Credential Manager · Supabase `users` row · department + 4-digit PIN registration · email-PIN fallback login · profile screen | ✅ |
| **2. Database & RLS** | 12 migrations: users, approval_requests, approval_steps, approval_request_files, notifications, plus the `actionable_steps` view that powers approver routing | ✅ |
| **3. Request creation** | Title, description, dept, category, priority, multi-file upload to Supabase Storage, typed-email approver picker scoped to department, 1–3 approval levels | ✅ |
| **4. Multi-level approval engine** | Approve / Reject / Send back / Comment, automatic routing through levels via the `actionable_steps` view, request-level status finalization | ✅ |
| **5. Resubmit + Remind + Delete** | Resubmit with new files after Send Back · inline reminder bell on active step · safe delete (only before any level approves) | ✅ |
| **6. In-app notifications** | Bell icon with unread badge · standalone Notifications screen with card list, mark read + mark all read · deep-link to Request Detail | ✅ |
| **7. Push notifications (FCM)** | Supabase Edge Function `send-push` (Deno/TS, OAuth2 from service account, FCM HTTP v1) · Postgres trigger via `pg_net` · Android client tray notification with PendingIntent deep-link | ✅ |
| **8. Home filters + search** | Date filter (dd/MM/yyyy with timezone-correct range) · department dropdown · status chips · "Approved by me" view · live title search across requester + approver roles | ✅ |
| **9. Branding** | Custom emerald checkmark mark for launcher icon + splash screen, animated splash with dedicated vector logo | ✅ |
| **10. Documentation** | Full README following Problem → Solution → Architecture → Tech-stack → Features → Setup format · this execution plan | ✅ |

### Stretch goals (deliberately out of scope for v1)

- Stage 8 of the brief — **Deviation tracking** (approved vs delivered comparison): infrastructure noted, not implemented.
- Stage 9 of the brief — **Dashboards & MIS reports**: data is there, UI not built.
- Escalation timers via Supabase pg_cron.
- iOS port via Kotlin Multiplatform + Compose Multiplatform (migration brief written).

---

## 5. Architecture

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
               │ tap (deep-link)
               ▼
   Request Detail screen
```

---

## 6. Tech stack

| Layer | Choice |
|---|---|
| Mobile | Android, Kotlin 2.3.21, Jetpack Compose, Material 3, Hilt 2.58, KSP 2.3.9, Coroutines, Compose BOM 2026.05.01 |
| Backend / Functions | Supabase Edge Functions (Deno/TypeScript) — `send-push` |
| Database | Supabase Postgres with RLS, `pg_net` for HTTP, the `actionable_steps` SQL view |
| Auth | Firebase Auth (Google via Credential Manager) + local SHA-256 PIN sessions in SharedPreferences |
| Push | Firebase Cloud Messaging (HTTP v1) |
| Storage | Supabase Storage (`approval_files` public bucket) |
| Build | AGP 8.10.1, Compose Compiler plugin |

---

## 7. Features delivered

1. **Multi-level approval workflow (1–3 levels)** with automatic routing once a level approves.
2. **Push notifications** via FCM with deep-link straight to Request Detail (works foreground, background, and killed).
3. **Email-based approver selection** scoped to the selected department, with debounced live autocomplete.
4. **Multi-file attachments** uploaded to Supabase Storage; previous files preserved on resubmit for audit trail.
5. **Resubmit after Send Back** with new files + comment, resets steps to pending and re-notifies Level 1.
6. **In-app notifications inbox** — card-list UI, unread badge on Home bell, mark all read.
7. **Filterable Home** — sections for Pending action / Your requests / Approved by me, with date + department + status filters.
8. **Live title search** across all related requests (requester + any-level approver), debounced.
9. **Manual reminder** — inline bell on the active step, pushes the active approver.
10. **Dual login** — Google sign-in OR email + 4-digit PIN (no Firebase session required).
11. **Audit trail** — every step records actor, timestamp, and comment.
12. **Safe delete** — requester can delete only while no level has approved.
13. **Custom branded launcher icon + splash screen**.

---

## 8. How to run locally

Full step-by-step in the repo's [README](https://github.com/dineshjangirkhetika/KhetikaSync#setup-instructions). Short version:

```bash
git clone https://github.com/dineshjangirkhetika/KhetikaSync.git
cd KhetikaSync
```

1. Create `local.properties` with `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `GOOGLE_WEB_CLIENT_ID`, and `sdk.dir`.
2. Configure Firebase (add Android app, debug SHA-1, enable Google sign-in, download `google-services.json` to `app/`).
3. Run every file in `supabase/migrations/` in Supabase SQL Editor in order (001 → 012).
4. (Optional for push) Install Supabase CLI, upload secrets, deploy the Edge Function, run trigger SQL.
5. Open in Android Studio → ▶ Run.

Target devices: Android 10+ (Android 13+ for the runtime notification permission prompt).

---

## 9. Repository layout

```
KhetikaSync/
├── app/                              # Android module (Kotlin + Compose)
│   ├── google-services.json
│   └── src/main/java/com/khetika/khetikasync/
│       ├── data/                     # repos, DTOs, auth helpers
│       ├── di/                       # Hilt modules
│       ├── messaging/                # KhetikaFirebaseMessagingService
│       └── ui/                       # Compose screens per feature
├── supabase/
│   ├── functions/send-push/index.ts  # Edge Function
│   └── migrations/                   # 12 SQL migrations
├── README.md
└── EXECUTION_PLAN.md                 # this document
```

---

## 10. Future scope

- **Stage 8 (Deviation tracking)** — compare approved request vs delivered output and flag deviations for management review.
- **Stage 9 (Dashboards & reports)** — pending-approval dashboard, department-wise overview, turnaround time, bottleneck approvers, weekly MIS reports.
- **Escalation timers** — Supabase pg_cron job that re-notifies / re-assigns slow steps.
- **iOS port** via Kotlin Multiplatform + Compose Multiplatform.
- **Tighter RLS** for production (current policies are permissive for the demo).
- **Storage cleanup** trigger to garbage-collect orphan files when a request is deleted.

---

**Submission link:** https://github.com/dineshjangirkhetika/KhetikaSync
