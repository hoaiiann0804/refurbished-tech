CREATE TABLE products (
    id UUID PRIMARY KEY,
    model_code VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    brand VARCHAR(100) NOT NULL,
    specification_summary VARCHAR(2000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_products_model_code UNIQUE (model_code),
    CONSTRAINT ck_products_model_code CHECK (model_code ~ '^[A-Z0-9][A-Z0-9._/-]*$'),
    CONSTRAINT ck_products_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_products_brand CHECK (length(btrim(brand)) > 0)
);

CREATE TABLE device_units (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    serial_number VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    grade VARCHAR(1),
    battery_health INTEGER,
    sale_price NUMERIC(14,2),
    inspection_passed BOOLEAN,
    inspection_notes VARCHAR(2000),
    inspected_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_device_units_serial_number UNIQUE (serial_number),
    CONSTRAINT ck_device_units_serial_number CHECK (serial_number ~ '^[A-Z0-9][A-Z0-9._/-]*$'),
    CONSTRAINT ck_device_units_status CHECK (status IN ('RECEIVED', 'INSPECTING', 'AVAILABLE', 'RESERVED', 'SOLD', 'REJECTED')),
    CONSTRAINT ck_device_units_grade CHECK (grade IN ('A', 'B', 'C')),
    CONSTRAINT ck_device_units_battery_health CHECK (battery_health BETWEEN 0 AND 100),
    CONSTRAINT ck_device_units_sale_price CHECK (sale_price > 0),
    CONSTRAINT ck_device_units_sellable_inspection CHECK (
        status NOT IN ('AVAILABLE', 'RESERVED', 'SOLD') OR (
            inspection_passed IS TRUE AND inspected_at IS NOT NULL
            AND grade IS NOT NULL AND sale_price IS NOT NULL
            AND inspection_notes IS NOT NULL AND length(btrim(inspection_notes)) > 0
        )
    )
);

CREATE INDEX ix_device_units_product_status ON device_units(product_id, status);
