ALTER TABLE auth_user RENAME COLUMN sign_in_attempts TO incomplete_sign_in_attempts;
ALTER TABLE auth_user ADD last_sign_in_attempt_at timestamp with time zone;
