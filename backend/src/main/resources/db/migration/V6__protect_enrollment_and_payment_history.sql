-- Preflight: duplicate ACTIVE enrollments/provider transactions must be reviewed
-- before applying. This migration deliberately fails rather than deleting history.
ALTER TABLE enrollments
    ADD COLUMN active_offering_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN status = 'ACTIVE' THEN offering_id ELSE NULL END) STORED,
    ADD CONSTRAINT uk_enrollments_active UNIQUE (user_id, active_offering_id);
ALTER TABLE payments
    ADD CONSTRAINT uk_payments_provider_transaction UNIQUE (provider, provider_transaction_id),
    ADD COLUMN checkout_url VARCHAR(2000) NULL,
    ADD COLUMN reconciliation_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN result_note VARCHAR(255) NULL;
ALTER TABLE reviews ADD COLUMN is_hidden BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_orders_expiry ON orders(status, expires_at);
