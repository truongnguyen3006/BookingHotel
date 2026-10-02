package com.example.bookinghotel.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BookingHotelBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(BookingHotelBackendApplication.class, args);
    }
}
