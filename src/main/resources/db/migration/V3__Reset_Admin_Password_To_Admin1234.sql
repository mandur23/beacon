-- V2 applied environments do not allow editing old migrations.
-- Keep V2 immutable and apply password correction in a new migration.

UPDATE users
SET password = '$2a$10$w3d6RaIWyaqW0Y8JMD1/SeewWYcScUbYQetrzdeBmwQ3QkwKJwKvS',
    enabled = TRUE,
    mfa_enabled = FALSE,
    role = 'ADMIN'
WHERE username = 'admin';
