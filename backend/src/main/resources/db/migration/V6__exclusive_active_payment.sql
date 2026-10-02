-- Retire provider attempts left active by pre-Phase-5 demo payments or expiration.
-- Never rewrite a successful payment or resurrect a released reservation.
UPDATE payments p JOIN bookings b ON b.id = p.booking_id
SET p.status = 'FAILED', p.provider_response_code = 'SUPERSEDED',
    p.provider_transaction_status = 'SUPERSEDED'
WHERE p.status = 'PENDING'
  AND (b.status IN ('SUCCESS', 'FAILED') OR b.inventory_released = TRUE);

-- Keep only the latest active attempt for any historical overlapping attempts.
UPDATE payments p JOIN (
    SELECT booking_id, MAX(id) AS keep_id
    FROM payments WHERE status = 'PENDING' GROUP BY booking_id
) newest ON newest.booking_id = p.booking_id AND p.id <> newest.keep_id
SET p.status = 'FAILED', p.provider_response_code = 'SUPERSEDED',
    p.provider_transaction_status = 'SUPERSEDED'
WHERE p.status = 'PENDING';

-- MySQL allows multiple NULLs in a unique index. Only PENDING consumes this key.
ALTER TABLE payments ADD COLUMN active_booking_id INT
    GENERATED ALWAYS AS (CASE WHEN status = 'PENDING' THEN booking_id ELSE NULL END) STORED;
CREATE UNIQUE INDEX uk_payments_one_active_booking ON payments(active_booking_id);
