package com.example.bookinghotel.backend.api.dto;

import java.time.Instant;
import java.util.Map;

public record ApiError(
        String code,
        String message,
        String path,
        Instant timestamp,
        Map<String, String> details
) {}
