-- Department is no longer collected on the Create Request form.
-- Make the column nullable on approval_requests and allow NULL in the
-- CHECK constraint. Existing rows are unaffected; new rows submit with NULL.

alter table public.approval_requests
    alter column department drop not null;

alter table public.approval_requests
    drop constraint if exists approval_requests_department_check;

alter table public.approval_requests
    add constraint approval_requests_department_check
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
    ));
