-- Approval workflow: request rows + per-level step rows.
-- A request has N levels (1..3). For each level we create one step row that
-- names the approver. Steps move from pending → approved/rejected/sent_back.
-- When the final level approves, the parent request flips to approved.

create table if not exists public.approval_requests (
    id              uuid primary key default gen_random_uuid(),
    requester_uid   text not null references public.users(firebase_uid) on delete cascade,
    title           text not null,
    description     text,
    department      text not null,
    file_name       text,
    file_uri        text,
    levels          int  not null default 1,
    status          text not null default 'pending',
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);

create index if not exists approval_requests_requester_idx on public.approval_requests (requester_uid);
create index if not exists approval_requests_department_idx on public.approval_requests (department);
create index if not exists approval_requests_status_idx on public.approval_requests (status);
create index if not exists approval_requests_created_at_idx on public.approval_requests (created_at);

alter table public.approval_requests
    drop constraint if exists approval_requests_status_check;
alter table public.approval_requests
    add constraint approval_requests_status_check
    check (status in ('pending', 'approved', 'rejected', 'sent_back'));

alter table public.approval_requests
    drop constraint if exists approval_requests_levels_check;
alter table public.approval_requests
    add constraint approval_requests_levels_check
    check (levels between 1 and 3);

alter table public.approval_requests
    drop constraint if exists approval_requests_department_check;
alter table public.approval_requests
    add constraint approval_requests_department_check
    check (department in (
        'Legal', 'Finance', 'Procurement', 'HR', 'Management', 'Operations'
    ));

drop trigger if exists approval_requests_set_updated_at on public.approval_requests;
create trigger approval_requests_set_updated_at
    before update on public.approval_requests
    for each row execute function public.set_updated_at();


create table if not exists public.approval_steps (
    id              uuid primary key default gen_random_uuid(),
    request_id      uuid not null references public.approval_requests(id) on delete cascade,
    level           int not null,
    approver_uid    text not null references public.users(firebase_uid),
    status          text not null default 'pending',
    note            text,
    acted_at        timestamptz,
    created_at      timestamptz not null default now()
);

create index if not exists approval_steps_request_idx on public.approval_steps (request_id);
create index if not exists approval_steps_approver_idx on public.approval_steps (approver_uid);
create index if not exists approval_steps_status_idx on public.approval_steps (status);

alter table public.approval_steps
    drop constraint if exists approval_steps_status_check;
alter table public.approval_steps
    add constraint approval_steps_status_check
    check (status in ('pending', 'approved', 'rejected', 'sent_back'));

alter table public.approval_steps
    drop constraint if exists approval_steps_level_check;
alter table public.approval_steps
    add constraint approval_steps_level_check
    check (level between 1 and 3);

alter table public.approval_steps
    drop constraint if exists approval_steps_unique_level_per_request;
alter table public.approval_steps
    add constraint approval_steps_unique_level_per_request
    unique (request_id, level);


alter table public.approval_requests enable row level security;
alter table public.approval_steps enable row level security;

drop policy if exists "approval_requests_anon_all" on public.approval_requests;
create policy "approval_requests_anon_all"
    on public.approval_requests for all to anon using (true) with check (true);

drop policy if exists "approval_steps_anon_all" on public.approval_steps;
create policy "approval_steps_anon_all"
    on public.approval_steps for all to anon using (true) with check (true);
