-- Phase 1: reservation lifecycle hardening.
-- inventory_released makes room restoration idempotent across payment callbacks,
-- status polling and the expiration scheduler.
ALTER TABLE bookings
    ADD COLUMN inventory_released BOOLEAN NOT NULL DEFAULT FALSE AFTER status,
    ADD COLUMN reservation_expires_at TIMESTAMP(6) NULL AFTER inventory_released;

-- Older application versions changed failed bookings to FAILED but never returned
-- their reserved rooms. Repair those historical leaks once during migration.
UPDATE rooms r
JOIN (
    SELECT room_id, SUM(quantity) AS quantity_to_restore
    FROM bookings
    WHERE status = 'FAILED'
    GROUP BY room_id
) leaked ON leaked.room_id = r.id
SET r.available_rooms = r.available_rooms + leaked.quantity_to_restore;

UPDATE bookings
SET inventory_released = TRUE
WHERE status = 'FAILED';

-- Existing active bookings need an expiry timestamp so the scheduler can clean them.
-- A VNPAY PROCESSING booking gets the provider's 15-minute window plus a short
-- 2-minute confirmation grace period for a delayed IPN.
UPDATE bookings b
LEFT JOIN (
    SELECT booking_id, MAX(created_at) AS last_payment_at
    FROM payments
    GROUP BY booking_id
) p ON p.booking_id = b.id
SET b.reservation_expires_at = CASE
    WHEN b.status = 'PROCESSING' AND p.last_payment_at IS NOT NULL
        THEN DATE_ADD(p.last_payment_at, INTERVAL 17 MINUTE)
    ELSE DATE_ADD(b.created_at, INTERVAL 15 MINUTE)
END
WHERE b.status IN ('PENDING_PAYMENT', 'PROCESSING');

CREATE INDEX idx_bookings_reservation_expiry
    ON bookings(status, reservation_expires_at);
