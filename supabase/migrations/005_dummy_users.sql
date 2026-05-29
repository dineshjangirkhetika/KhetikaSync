-- Six dummy verified users so the approver dropdown isn't empty.
-- Safe to re-run: ON CONFLICT (firebase_uid) DO NOTHING.

insert into public.users (firebase_uid, email, display_name, department, role, is_verified)
values
    ('dummy_legal_001',       'asha.menon@khetika.test',    'Asha Menon',    'Legal',       'Approver', true),
    ('dummy_finance_001',     'rohit.iyer@khetika.test',    'Rohit Iyer',    'Finance',     'Approver', true),
    ('dummy_finance_002',     'nidhi.shah@khetika.test',    'Nidhi Shah',    'Finance',     'Maker',    true),
    ('dummy_procurement_001', 'vikram.rao@khetika.test',    'Vikram Rao',    'Procurement', 'Approver', true),
    ('dummy_hr_001',          'priya.nair@khetika.test',    'Priya Nair',    'HR',          'Approver', true),
    ('dummy_management_001',  'karan.bhatia@khetika.test',  'Karan Bhatia',  'Management',  'Admin',    true)
on conflict (firebase_uid) do nothing;
