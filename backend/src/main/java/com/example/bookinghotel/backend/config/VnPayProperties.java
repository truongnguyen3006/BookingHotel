package com.example.bookinghotel.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class VnPayProperties {
    private final String tmnCode;
    private final String hashSecret;
    private final String payUrl;
    private final String returnUrl;
    private final int expireMinutes;
    private final int confirmationGraceSeconds;

    public VnPayProperties(
            @Value("${vnpay.tmn-code:}") String tmnCode,
            @Value("${vnpay.hash-secret:}") String hashSecret,
            @Value("${vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}") String payUrl,
            @Value("${vnpay.return-url:http://localhost:8080/api/payments/vnpay/return}") String returnUrl,
            @Value("${vnpay.expire-minutes:15}") int expireMinutes,
            @Value("${vnpay.confirmation-grace-seconds:120}") int confirmationGraceSeconds
    ) {
        this.tmnCode = clean(tmnCode);
        this.hashSecret = clean(hashSecret);
        this.payUrl = clean(payUrl);
        this.returnUrl = clean(returnUrl);
        this.expireMinutes = expireMinutes;
        this.confirmationGraceSeconds = confirmationGraceSeconds;
    }

    public String getTmnCode() { return tmnCode; }
    public String getHashSecret() { return hashSecret; }
    public String getPayUrl() { return payUrl; }
    public String getReturnUrl() { return returnUrl; }
    public int getExpireMinutes() { return expireMinutes; }
    public int getConfirmationGraceSeconds() { return confirmationGraceSeconds; }

    public boolean isConfigured() {
        return tmnCode.matches("[A-Za-z0-9]{8}")
                && !hashSecret.isBlank()
                && !payUrl.isBlank()
                && !returnUrl.isBlank();
    }

    private static String clean(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '\"' && last == '\"') || (first == '\'' && last == '\'')) {
                value = value.substring(1, value.length() - 1).trim();
            }
        }
        return value;
    }
}
