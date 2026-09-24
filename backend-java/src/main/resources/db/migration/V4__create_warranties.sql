CREATE TABLE warranties (
    id UUID PRIMARY KEY,
    device_unit_id UUID NOT NULL REFERENCES device_units(id) ON DELETE RESTRICT,
    duration_months INTEGER NOT NULL,
    starts_on DATE NOT NULL,
    ends_on DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_warranties_device_unit UNIQUE (device_unit_id),
    CONSTRAINT ck_warranties_duration_months CHECK (duration_months BETWEEN 1 AND 36),
    CONSTRAINT ck_warranties_dates CHECK (ends_on > starts_on)
);
