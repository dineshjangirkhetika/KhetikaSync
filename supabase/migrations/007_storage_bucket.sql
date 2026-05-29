-- Public storage bucket for approval-request attachments.
-- Files are referenced from approval_requests.file_uri as their public URL.

insert into storage.buckets (id, name, public)
values ('approval_files', 'approval_files', true)
on conflict (id) do update set public = excluded.public;

-- Allow the anon role (the app's anon key) to upload into this bucket only.
drop policy if exists "approval_files_anon_insert" on storage.objects;
create policy "approval_files_anon_insert"
    on storage.objects for insert to anon
    with check (bucket_id = 'approval_files');

-- Public bucket already serves files via public URL without auth, but we also
-- expose select to anon so signed reads work consistently.
drop policy if exists "approval_files_anon_select" on storage.objects;
create policy "approval_files_anon_select"
    on storage.objects for select to anon
    using (bucket_id = 'approval_files');
