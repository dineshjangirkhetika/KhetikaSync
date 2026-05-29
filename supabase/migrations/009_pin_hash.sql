-- 4-digit PIN auth: stored as SHA-256(pin + ":" + email) hex digest.
-- Note: 4 digits is low-entropy; this is convenience, not strong security.
-- If anyone ever exfiltrates pin_hash + email they can brute force in ms.

alter table public.users
    add column if not exists pin_hash text;
