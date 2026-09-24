CREATE TABLE sales_orders (
    id UUID PRIMARY KEY,
    customer_name VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_amount NUMERIC(14,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_sales_orders_customer_name CHECK (length(btrim(customer_name)) > 0),
    CONSTRAINT ck_sales_orders_status CHECK (status IN ('COMPLETED')),
    CONSTRAINT ck_sales_orders_total_amount CHECK (total_amount > 0)
);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES sales_orders(id) ON DELETE RESTRICT,
    device_unit_id UUID NOT NULL REFERENCES device_units(id) ON DELETE RESTRICT,
    unit_price NUMERIC(14,2) NOT NULL,
    CONSTRAINT uq_order_items_device_unit UNIQUE (device_unit_id),
    CONSTRAINT ck_order_items_unit_price CHECK (unit_price > 0)
);

CREATE INDEX ix_order_items_order_id ON order_items(order_id);
