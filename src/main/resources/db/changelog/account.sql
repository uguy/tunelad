--liquibase formatted sql

--changeset tunelad:account-accounts
-- An account, identified by the identity provider's issuer and subject
CREATE TABLE account_accounts
(
    id            TEXT                     NOT NULL DEFAULT gen_random_uuid()::text,
    issuer        TEXT                     NOT NULL,
    subject       TEXT                     NOT NULL,
    display_name  TEXT,
    email         TEXT,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    last_login_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id),
    CONSTRAINT account_accounts_identity UNIQUE (issuer, subject)
);
