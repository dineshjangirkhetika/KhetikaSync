-- Users table: one row per authenticated user.
-- Keyed by the Firebase UID since auth happens in Firebase, not Supabase Auth.

create table if not exists public.users (
    id              uuid primary key default gen_random_uuid(),
    firebase_uid    text unique not null,
    email           text unique not null,
    display_name    text,
    photo_url       text,
    department      text,
    role            text,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);

create index if not exists users_firebase_uid_idx on public.users (firebase_uid);
create index if not exists users_email_idx on public.users (email);

-- Auto-update updated_at on every UPDATE
create or replace function public.set_updated_at()
returns trigger language plpgsql as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists users_set_updated_at on public.users;
create trigger users_set_updated_at
    before update on public.users
    for each row execute function public.set_updated_at();

-- RLS: enable, but keep policies permissive only for the anon key while
-- prototyping. Tighten these before production — e.g. require Supabase JWT
-- whose `sub` matches firebase_uid, or proxy writes through an edge function.
alter table public.users enable row level security;

drop policy if exists "users_anon_read" on public.users;
create policy "users_anon_read"
    on public.users for select
    to anon
    using (true);

drop policy if exists "users_anon_upsert" on public.users;
create policy "users_anon_upsert"
    on public.users for insert
    to anon
    with check (true);

drop policy if exists "users_anon_update" on public.users;
create policy "users_anon_update"
    on public.users for update
    to anon
    using (true)
    with check (true);
