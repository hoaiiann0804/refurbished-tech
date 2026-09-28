CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    actor_user_id UUID,
    actor_kind VARCHAR(20) NOT NULL CHECK (actor_kind IN ('USER', 'SYSTEM', 'LOCAL_OPERATOR')),
    action VARCHAR(80) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    details JSONB NOT NULL
);
CREATE INDEX ix_audit_target_time ON audit_events(target_type, target_id, occurred_at DESC, id DESC);
CREATE INDEX ix_audit_time ON audit_events(occurred_at DESC, id DESC);

-- Một key chỉ có một kết quả cho cùng nhân viên. Row giữ key và đơn được commit cùng nhau.
CREATE TABLE checkout_requests (
    actor_id UUID NOT NULL,
    request_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    order_id UUID REFERENCES sales_orders(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (actor_id, request_key)
);
CREATE INDEX ix_sales_orders_created ON sales_orders(created_at DESC, id DESC);
