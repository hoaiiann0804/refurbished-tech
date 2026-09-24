CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_app_users_email UNIQUE (email),
    CONSTRAINT ck_app_users_email_normalized CHECK (email = lower(btrim(email))),
    CONSTRAINT ck_app_users_display_name CHECK (length(btrim(display_name)) > 0),
    CONSTRAINT ck_app_users_role CHECK (role IN ('ADMIN', 'STAFF'))
);

CREATE TABLE oauth_login_codes (
    id UUID PRIMARY KEY,
    code_hash VARCHAR(64) NOT NULL,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_oauth_login_codes_hash UNIQUE (code_hash),
    CONSTRAINT ck_oauth_login_codes_hash CHECK (code_hash ~ '^[a-f0-9]{64}$'),
    CONSTRAINT ck_oauth_login_codes_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_oauth_login_codes_used CHECK (used_at IS NULL OR used_at >= created_at)
);

CREATE INDEX ix_oauth_login_codes_user ON oauth_login_codes(user_id);
CREATE INDEX ix_oauth_login_codes_expiry ON oauth_login_codes(expires_at);
