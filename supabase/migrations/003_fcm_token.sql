-- FCM device token for push notifications (pending approvals, etc.).
-- Nullable: not every signed-in device has registered for messaging yet.

alter table public.users
    add column if not exists fcm_token text;

create index if not exists users_fcm_token_idx on public.users (fcm_token);
