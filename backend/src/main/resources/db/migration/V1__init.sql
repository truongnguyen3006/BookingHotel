CREATE TABLE rooms (
    id INT NOT NULL AUTO_INCREMENT,
    image_key VARCHAR(100) NOT NULL,
    type_key VARCHAR(100) NOT NULL,
    price_per_night DECIMAL(10,2) NOT NULL,
    available_rooms INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_rooms_type_key UNIQUE (type_key),
    CONSTRAINT chk_rooms_price CHECK (price_per_night >= 0),
    CONSTRAINT chk_rooms_available CHECK (available_rooms >= 0)
) ENGINE=InnoDB;

CREATE TABLE room_amenities (
    room_id INT NOT NULL,
    amenity VARCHAR(120) NOT NULL,
    CONSTRAINT fk_room_amenities_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE INDEX idx_room_amenities_room_id ON room_amenities(room_id);

CREATE TABLE bookings (
    id INT NOT NULL AUTO_INCREMENT,
    room_id INT NOT NULL,
    quantity INT NOT NULL,
    check_in_date DATE NOT NULL,
    check_out_date DATE NOT NULL,
    guests INT NOT NULL,
    nights INT NOT NULL,
    total_price DECIMAL(12,2) NOT NULL,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_bookings_room FOREIGN KEY (room_id) REFERENCES rooms(id),
    CONSTRAINT chk_booking_quantity CHECK (quantity > 0),
    CONSTRAINT chk_booking_guests CHECK (guests > 0),
    CONSTRAINT chk_booking_nights CHECK (nights > 0),
    CONSTRAINT chk_booking_total CHECK (total_price >= 0)
) ENGINE=InnoDB;

CREATE INDEX idx_bookings_room_id ON bookings(room_id);
CREATE INDEX idx_bookings_created_at ON bookings(created_at);

CREATE TABLE payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    booking_id INT NOT NULL,
    method VARCHAR(20) NOT NULL,
    status VARCHAR(40) NOT NULL,
    transaction_id VARCHAR(100) NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    paid_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_payments_booking FOREIGN KEY (booking_id) REFERENCES bookings(id),
    CONSTRAINT uk_payments_booking_idempotency UNIQUE (booking_id, idempotency_key)
) ENGINE=InnoDB;

CREATE INDEX idx_payments_booking_id ON payments(booking_id);
CREATE INDEX idx_payments_transaction_id ON payments(transaction_id);

INSERT INTO rooms (id, image_key, type_key, price_per_night, available_rooms) VALUES
    (1, 'standard_room', 'standard', 50.00, 10),
    (2, 'deluxe_room', 'deluxe', 80.00, 10),
    (3, 'suite_room', 'suite', 120.00, 10),
    (4, 'executive_room', 'executive', 150.00, 10),
    (5, 'family_room', 'family', 100.00, 10);

INSERT INTO room_amenities (room_id, amenity) VALUES
    (1, 'Wi-Fi'), (1, 'TV'),
    (2, 'Wi-Fi'), (2, 'TV'), (2, 'Mini Bar'),
    (3, 'Wi-Fi'), (3, 'TV'), (3, 'Mini Bar'), (3, 'Jacuzzi'),
    (4, 'Wi-Fi'), (4, 'TV'), (4, 'Mini Bar'), (4, 'Jacuzzi'), (4, 'Breakfast'),
    (5, 'Wi-Fi'), (5, 'TV'), (5, 'Kitchenette');
