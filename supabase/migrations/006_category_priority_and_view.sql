-- 1) Category + Priority on requests.
alter table public.approval_requests
    add column if not exists category text,
    add column if not exists priority text not null default 'Normal';

alter table public.approval_requests
    drop constraint if exists approval_requests_category_check;
alter table public.approval_requests
    add constraint approval_requests_category_check
    check (category is null or category in (
        'Vendor', 'Legal', 'Expense', 'Procurement', 'HR', 'Other'
    ));

alter table public.approval_requests
    drop constraint if exists approval_requests_priority_check;
alter table public.approval_requests
    add constraint approval_requests_priority_check
    check (priority in ('Normal', 'Urgent', 'High'));

create index if not exists approval_requests_category_idx on public.approval_requests (category);
create index if not exists approval_requests_priority_idx on public.approval_requests (priority);


-- 2) actionable_steps view: the next pending step in each request.
-- A step is "actionable" when it's pending AND every earlier (lower-level) step
-- is already approved. Use this to power "Pending your action" inboxes.
drop view if exists public.actionable_steps;
create view public.actionable_steps
with (security_invoker = true) as
select s.*
from public.approval_steps s
where s.status = 'pending'
  and not exists (
      select 1
      from public.approval_steps s2
      where s2.request_id = s.request_id
        and s2.level < s.level
        and s2.status <> 'approved'
  );

grant select on public.actionable_steps to anon, authenticated;
