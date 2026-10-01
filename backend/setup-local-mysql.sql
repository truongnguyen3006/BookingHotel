CREATE DATABASE IF NOT EXISTS booking_hotel
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'booking_app'@'localhost' IDENTIFIED BY 'booking_app_password';
GRANT ALL PRIVILEGES ON booking_hotel.* TO 'booking_app'@'localhost';
FLUSH PRIVILEGES;
