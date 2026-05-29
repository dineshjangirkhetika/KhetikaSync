-- 1) Per-request file attachments (multi-file support).
-- We keep approval_requests.file_name / file_uri for legacy rows but stop
-- writing to them; all new uploads land here.

create table if not exists public.approval_request_files (
    id          uuid primary key default gen_random_uuid(),
    request_id  uuid not null references public.approval_requests(id) on delete cascade,
    file_name   text not null,
    file_url    text not null,
    size_bytes  bigint,
    created_at  timestamptz not null default now()
);

create index if not exists approval_request_files_request_idx
    on public.approval_request_files (request_id);

alter table public.approval_request_files enable row level security;

drop policy if exists "approval_request_files_anon_all" on public.approval_request_files;
create policy "approval_request_files_anon_all"
    on public.approval_request_files for all to anon using (true) with check (true);


-- 2) In-app notifications.
-- type values: 'pending_for_you', 'approved', 'rejected', 'sent_back'.
create table if not exists public.notifications (
    id              uuid primary key default gen_random_uuid(),
    recipient_uid   text not null references public.users(firebase_uid) on delete cascade,
    type            text not null,
    title           text not null,
    body            text,
    request_id      uuid references public.approval_requests(id) on delete cascade,
    is_read         boolean not null default false,
    created_at      timestamptz not null default now()
);

create index if not exists notifications_recipient_idx on public.notifications (recipient_uid);
create index if not exists notifications_unread_idx on public.notifications (recipient_uid, is_read);
create index if not exists notifications_created_at_idx on public.notifications (created_at desc);

alter table public.notifications
    drop constraint if exists notifications_type_check;
alter table public.notifications
    add constraint notifications_type_check
    check (type in ('pending_for_you', 'approved', 'rejected', 'sent_back', 'resubmitted'));

alter table public.notifications enable row level security;

drop policy if exists "notifications_anon_all" on public.notifications;
create policy "notifications_anon_all"
    on public.notifications for all to anon using (true) with check (true);
