-- Allow 'reminder' type in notifications so the requester can nudge the
-- current approver on a pending request.

alter table public.notifications
    drop constraint if exists notifications_type_check;
alter table public.notifications
    add constraint notifications_type_check
    check (type in (
        'pending_for_you',
        'approved',
        'rejected',
        'sent_back',
        'resubmitted',
        'reminder'
    ));
