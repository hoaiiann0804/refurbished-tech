CREATE TABLE warranty_claims (
    id UUID PRIMARY KEY,
    warranty_id UUID NOT NULL REFERENCES warranties(id) ON DELETE RESTRICT,
    reported_issue VARCHAR(1000) NOT NULL CHECK (length(btrim(reported_issue)) > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN', 'DIAGNOSING', 'REPAIRING', 'RESOLVED', 'REJECTED')),
    resolution_notes VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_warranty_claims_warranty_id ON warranty_claims(warranty_id, created_at DESC);
CREATE INDEX ix_warranty_claims_status ON warranty_claims(status);
