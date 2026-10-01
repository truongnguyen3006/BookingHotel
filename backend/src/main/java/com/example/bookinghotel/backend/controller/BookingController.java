package com.example.bookinghotel.backend.controller;

import com.example.bookinghotel.backend.api.dto.BookingRequest;
import com.example.bookinghotel.backend.api.dto.BookingResponse;
import com.example.bookinghotel.backend.api.dto.PaymentRequest;
import com.example.bookinghotel.backend.api.dto.PaymentResponse;
import com.example.bookinghotel.backend.service.BookingService;
import com.example.bookinghotel.backend.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {
    private final BookingService bookingService;
    private final PaymentService paymentService;

    public BookingController(BookingService bookingService, PaymentService paymentService) {
        this.bookingService = bookingService;
        this.paymentService = paymentService;
    }

    @GetMapping
    public List<BookingResponse> getMyBookings() {
        return bookingService.getMyBookings();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse createBooking(@Valid @RequestBody BookingRequest request) {
        return bookingService.createBooking(request);
    }

    @GetMapping("/{bookingId}")
    public BookingResponse getBooking(@PathVariable int bookingId) {
        return bookingService.getBooking(bookingId);
    }

    @PostMapping("/{bookingId}/payment")
    public PaymentResponse payBooking(
            @PathVariable int bookingId,
            @Valid @RequestBody PaymentRequest request
    ) {
        return paymentService.pay(bookingId, request);
    }
}
