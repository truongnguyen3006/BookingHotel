package com.example.bookinghotel.backend.controller;

import com.example.bookinghotel.backend.api.dto.VnPayCreateRequest;
import com.example.bookinghotel.backend.api.dto.VnPayCreateResponse;
import com.example.bookinghotel.backend.api.dto.VnPayStatusResponse;
import com.example.bookinghotel.backend.vnpay.VnPayService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class VnPayController {
    private final VnPayService vnPayService;

    public VnPayController(VnPayService vnPayService) {
        this.vnPayService = vnPayService;
    }

    @PostMapping("/bookings/{bookingId}/payment/vnpay")
    public VnPayCreateResponse createPayment(
            @PathVariable int bookingId,
            @Valid @RequestBody VnPayCreateRequest request,
            HttpServletRequest servletRequest
    ) {
        return vnPayService.createPayment(
                bookingId,
                request.idempotencyKey(),
                clientIp(servletRequest)
        );
    }

    @GetMapping("/bookings/{bookingId}/payment/vnpay/status")
    public VnPayStatusResponse paymentStatus(@PathVariable int bookingId) {
        return vnPayService.getStatus(bookingId);
    }

    @GetMapping("/payments/vnpay/ipn")
    public Map<String, String> ipn(@RequestParam Map<String, String> params) {
        try {
            // Catch outside the service's transactional proxy so a partial payment /
            // booking / inventory update is rolled back before acknowledging failure.
            return vnPayService.processIpn(params);
        } catch (RuntimeException exception) {
            org.slf4j.LoggerFactory.getLogger(VnPayController.class)
                    .error("VNPAY IPN transaction rolled back", exception);
            return Map.of("RspCode", "99", "Message", "Unknown error");
        }
    }

    @GetMapping(value = "/payments/vnpay/return", produces = MediaType.TEXT_HTML_VALUE)
    public String paymentReturn(@RequestParam Map<String, String> params) {
        String deepLink = vnPayService.returnDeepLink(params);
        String message = org.springframework.web.util.HtmlUtils.htmlEscape(vnPayService.returnMessage(params));
        return """
                <!doctype html>
                <html lang="vi">
                <head>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1" />
                  <title>BookingHotel - VNPAY</title>
                  <style>
                    body{font-family:system-ui,sans-serif;background:#f6f7fb;margin:0;padding:32px;color:#1d1b20}
                    .card{max-width:560px;margin:10vh auto;background:white;border-radius:18px;padding:28px;box-shadow:0 12px 35px #0001}
                    a{display:inline-block;margin-top:18px;padding:12px 18px;border-radius:12px;background:#6750a4;color:white;text-decoration:none}
                  </style>
                </head>
                <body>
                  <div class="card">
                    <h2>BookingHotel - VNPAY</h2>
                    <p>%s</p>
                    <p>Ket qua chinh thuc duoc backend xac nhan qua IPN cua VNPAY.</p>
                    <a href="%s">Quay lai ung dung BookingHotel</a>
                  </div>
                  <script>setTimeout(function(){ window.location.href = '%s'; }, 700);</script>
                </body>
                </html>
                """.formatted(message, deepLink, deepLink);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
