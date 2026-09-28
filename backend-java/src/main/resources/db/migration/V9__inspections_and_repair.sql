ALTER TABLE device_units DROP CONSTRAINT ck_device_units_status;

ALTER TABLE device_units ADD CONSTRAINT ck_device_units_status
    CHECK (status IN ('RECEIVED', 'INSPECTING', 'AVAILABLE', 'RESERVED', 'SOLD', 'REJECTED', 'IN_REPAIR'));

CREATE TABLE inspections (
    id UUID PRIMARY KEY,
    device_unit_id UUID NOT NULL REFERENCES device_units(id) ON DELETE CASCADE,
    inspector_id UUID REFERENCES app_users(id) ON DELETE SET NULL,
    passed BOOLEAN NOT NULL,
    grade VARCHAR(1) CHECK (grade IN ('A', 'B', 'C')),
    battery_health INTEGER CHECK (battery_health BETWEEN 0 AND 100),
    battery_health_unavailable_reason VARCHAR(1000),
    sale_price NUMERIC(14,2) CHECK (sale_price > 0),
    notes VARCHAR(2000) NOT NULL CHECK (length(btrim(notes)) > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_inspection_passing_evidence CHECK (
        passed IS FALSE OR (
            grade IS NOT NULL AND sale_price IS NOT NULL
            AND (battery_health IS NOT NULL OR battery_health_unavailable_reason IS NOT NULL)
        )
    ),
    CONSTRAINT ck_inspection_battery_reason CHECK (
        battery_health_unavailable_reason IS NULL OR (
            battery_health IS NULL AND length(btrim(battery_health_unavailable_reason)) > 0
        )
    )
);

CREATE INDEX ix_inspections_device_unit_id ON inspections(device_unit_id, created_at DESC);

INSERT INTO inspections (id, device_unit_id, inspector_id, passed, grade, battery_health, battery_health_unavailable_reason, sale_price, notes, created_at)
SELECT gen_random_uuid(), id, NULL, inspection_passed, grade, battery_health, battery_health_unavailable_reason, sale_price, COALESCE(inspection_notes, 'Initial inspection recorded during migration'), COALESCE(inspected_at, created_at)
FROM device_units
WHERE inspection_passed IS NOT NULL;
