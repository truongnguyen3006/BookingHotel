-- Phase 2: make VND the only money unit in the system.
-- Existing demo catalog values were USD-like price units charged by VNPAY with a 25,000 multiplier.
-- Convert them once so the user-visible amount stays economically identical after this migration.

UPDATE rooms
SET price_per_night = ROUND(price_per_night * 25000);

UPDATE bookings
SET total_price = ROUND(total_price * 25000);

ALTER TABLE rooms
    MODIFY COLUMN price_per_night BIGINT NOT NULL;

ALTER TABLE bookings
    MODIFY COLUMN total_price BIGINT NOT NULL;

-- VNPAY rows already stored native VND in amount_vnd, so they MUST NOT be multiplied again.
-- Backfill missing provider snapshots from the now-native booking total where possible.
UPDATE payments p
JOIN bookings b ON b.id = p.booking_id
SET p.amount_vnd = b.total_price
WHERE p.method = 'VNPAY'
  AND p.amount_vnd IS NULL;
