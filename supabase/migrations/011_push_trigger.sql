-- Push trigger: on every new notification row, call the send-push Edge Function
-- via pg_net (an async HTTP client that ships with Supabase).
--
-- BEFORE RUNNING:
--   1. Enable pg_net extension (Supabase Dashboard → Database → Extensions →
--      search "pg_net" → enable). It is usually pre-installed.
--   2. Store the two values below in Postgres settings (Supabase SQL Editor
--      will not survive restarts, so set them as DB-level settings).
--      Replace <your-project-ref> and <your-shared-secret>.
--
--        alter database postgres set app.push_url =
--            'https://<your-project-ref>.functions.supabase.co/send-push';
--        alter database postgres set app.push_shared_secret =
--            '<your-shared-secret>';
--
--   3. Deploy the Edge Function with the matching secret:
--        supabase functions deploy send-push --no-verify-jwt
--        supabase secrets set PUSH_SHARED_SECRET=<your-shared-secret>
--        supabase secrets set FIREBASE_SERVICE_ACCOUNT="$(cat sa.json)"

create or replace function public.notify_send_push() returns trigger
language plpgsql security definer as $$
declare
    push_url text := current_setting('app.push_url', true);
    push_secret text := current_setting('app.push_shared_secret', true);
begin
    if push_url is null or push_secret is null then
        raise notice 'notify_send_push: app.push_url or app.push_shared_secret not set; skipping';
        return new;
    end if;

    perform net.http_post(
        url := push_url,
        headers := jsonb_build_object(
            'content-type', 'application/json',
            'x-shared-secret', push_secret
        ),
        body := jsonb_build_object('record', row_to_json(new))
    );
    return new;
end;
$$;

drop trigger if exists notifications_send_push on public.notifications;
create trigger notifications_send_push
    after insert on public.notifications
    for each row execute function public.notify_send_push();
