-- Deleting a user should also remove their refresh tokens instead of failing on the foreign key.
ALTER TABLE refresh_tokens DROP CONSTRAINT fk_refresh_token_user;
ALTER TABLE refresh_tokens
    ADD CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
