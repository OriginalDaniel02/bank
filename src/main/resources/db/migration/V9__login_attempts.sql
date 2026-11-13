-- Failed sign-in attempts per identifier (email or phone, hashed). Also tracked for identifiers that do not exist,
-- so a locked account and an unknown account look the same from outside.
CREATE TABLE login_attempts (
    attempt_key VARCHAR(64) PRIMARY KEY,
    failures INT NOT NULL,
    locked_until TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
