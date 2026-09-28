ALTER TABLE users
    ADD COLUMN account_deletion_code_hash VARCHAR(64),
    ADD COLUMN account_deletion_code_expires_at TIMESTAMP;
