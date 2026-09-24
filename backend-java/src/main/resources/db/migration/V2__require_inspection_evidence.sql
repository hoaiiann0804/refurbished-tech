ALTER TABLE device_units
    ADD COLUMN battery_health_unavailable_reason VARCHAR(1000);

ALTER TABLE device_units
    ADD CONSTRAINT ck_device_units_battery_reason CHECK (
        battery_health_unavailable_reason IS NULL OR (
            battery_health IS NULL AND length(btrim(battery_health_unavailable_reason)) > 0
        )
    ),
    ADD CONSTRAINT ck_device_units_sellable_battery_evidence CHECK (
        status NOT IN ('AVAILABLE', 'RESERVED', 'SOLD') OR (
            battery_health IS NOT NULL OR battery_health_unavailable_reason IS NOT NULL
        )
    ),
    ADD CONSTRAINT ck_device_units_rejected_evidence CHECK (
        status <> 'REJECTED' OR (
            inspection_passed IS FALSE AND inspected_at IS NOT NULL
            AND inspection_notes IS NOT NULL AND length(btrim(inspection_notes)) > 0
        )
    );
