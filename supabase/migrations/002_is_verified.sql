-- Adds the is_verified flag. Default false — flips to true once the user
-- completes the in-app registration screen (department + role chosen).

alter table public.users
    add column if not exists is_verified boolean not null default false;

create index if not exists users_is_verified_idx on public.users (is_verified);

-- Optional: validate department/role values at the DB layer.
-- Adjust the lists below if your org needs more.
alter table public.users
    drop constraint if exists users_department_check;
alter table public.users
    add constraint users_department_check
    check (department is null or department in (
        'Legal', 'Finance', 'Procurement', 'HR', 'Management', 'Operations'
    ));

alter table public.users
    drop constraint if exists users_role_check;
alter table public.users
    add constraint users_role_check
    check (role is null or role in ('Maker', 'Approver', 'Admin'));
