ALTER TABLE payments
    ADD COLUMN provider_reference VARCHAR(100) NULL AFTER idempotency_key,
    ADD COLUMN provider_response_code VARCHAR(20) NULL AFTER provider_reference,
    ADD COLUMN provider_transaction_status VARCHAR(20) NULL AFTER provider_response_code,
    ADD COLUMN amount_vnd BIGINT NULL AFTER provider_transaction_status;

CREATE UNIQUE INDEX uk_payments_provider_reference ON payments(provider_reference);
