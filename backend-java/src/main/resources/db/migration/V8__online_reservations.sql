-- Online state is separate from immutable completed counter sales.
CREATE TABLE online_reservations (
    id UUID PRIMARY KEY,
    actor_id UUID NOT NULL,
    device_unit_id UUID NOT NULL REFERENCES device_units(id),
    customer_name VARCHAR(200) NOT NULL,
    shipping_address VARCHAR(500) NOT NULL,
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','EXPIRED','CANCELLED','PAID')),
    payment_status VARCHAR(24) NOT NULL DEFAULT 'UNPAID'
        CHECK (payment_status IN ('UNPAID','PAID','LATE_PAYMENT','REFUNDED')),
    order_id UUID UNIQUE REFERENCES sales_orders(id),
    tracking_number VARCHAR(100),
    shipment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (shipment_status IN ('PENDING','SHIPPED','DELIVERED')),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_active_reservation_device ON online_reservations(device_unit_id) WHERE status='ACTIVE';
CREATE INDEX ix_reservation_expiry ON online_reservations(expires_at) WHERE status='ACTIVE';
CREATE INDEX ix_reservation_actor_created ON online_reservations(actor_id,created_at DESC,id DESC);
CREATE TABLE sandbox_payment_events (
    event_id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES online_reservations(id),
    kind VARCHAR(10) NOT NULL CHECK (kind IN ('PAYMENT','REFUND')),
    amount NUMERIC(14,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
