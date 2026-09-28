CREATE TABLE customers (
    id UUID PRIMARY KEY,
    phone_number VARCHAR(20) NOT NULL UNIQUE,
    full_name VARCHAR(200) NOT NULL,
    email VARCHAR(320),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_customers_phone CHECK (phone_number ~ '^[0-9+]{8,20}$'),
    CONSTRAINT ck_customers_name CHECK (length(btrim(full_name)) > 0)
);

CREATE INDEX ix_customers_phone ON customers(phone_number);
CREATE INDEX ix_customers_created_at ON customers(created_at DESC);

ALTER TABLE sales_orders ADD COLUMN customer_id UUID REFERENCES customers(id);

ALTER TABLE sales_orders DROP CONSTRAINT ck_sales_orders_status;
ALTER TABLE sales_orders ADD CONSTRAINT ck_sales_orders_status CHECK (status IN ('PENDING', 'COMPLETED', 'CANCELLED'));
