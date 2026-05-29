-- Replace the department whitelist in both users + approval_requests.
-- Uses NOT VALID so existing rows with old values (Legal, HR, Procurement,
-- Management) are NOT rejected — they just become "grandfathered". All
-- *new* writes from the app will hit the new list.

-- ----- users.department -----
alter table public.users
    drop constraint if exists users_department_check;

alter table public.users
    add constraint users_department_check
    check (department is null or department in (
        'Admin',
        'Commercial',
        'Finance',
        'Human Resource',
        'Khetika Saathi',
        'Marketing',
        'Operations',
        'Operations Fresh',
        'Operations Grocery',
        'Quality',
        'Quality Assurance',
        'Sales',
        'Sales - DFM & Spices',
        'Sales - Fresh',
        'Sales - GT East',
        'Sales - NPD & Support',
        'Sales - Superzop',
        'Spices Division',
        'Supply Chain',
        'Technology'
    )) not valid;

-- ----- approval_requests.department -----
alter table public.approval_requests
    drop constraint if exists approval_requests_department_check;

alter table public.approval_requests
    add constraint approval_requests_department_check
    check (department in (
        'Admin',
        'Commercial',
        'Finance',
        'Human Resource',
        'Khetika Saathi',
        'Marketing',
        'Operations',
        'Operations Fresh',
        'Operations Grocery',
        'Quality',
        'Quality Assurance',
        'Sales',
        'Sales - DFM & Spices',
        'Sales - Fresh',
        'Sales - GT East',
        'Sales - NPD & Support',
        'Sales - Superzop',
        'Spices Division',
        'Supply Chain',
        'Technology'
    )) not valid;

-- ----- Optional: remap old dummy/test rows to new department names ----------
-- Uncomment if you want existing rows to use the new vocabulary.
-- (These are best-effort mappings; tweak to taste.)

-- update public.users set department = 'Human Resource' where department = 'HR';
-- update public.users set department = 'Admin'          where department = 'Management';
-- update public.users set department = 'Operations'     where department = 'Procurement';
-- update public.users set department = 'Operations'     where department = 'Legal';

-- update public.approval_requests set department = 'Human Resource' where department = 'HR';
-- update public.approval_requests set department = 'Admin'          where department = 'Management';
-- update public.approval_requests set department = 'Operations'     where department = 'Procurement';
-- update public.approval_requests set department = 'Operations'     where department = 'Legal';
