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
    private final long vndPerPriceUnit;

    public VnPayProperties(
            @Value("${vnpay.tmn-code:}") String tmnCode,
            @Value("${vnpay.hash-secret:}") String hashSecret,
            @Value("${vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}") String payUrl,
            @Value("${vnpay.return-url:http://localhost:8080/api/payments/vnpay/return}") String returnUrl,
            @Value("${vnpay.expire-minutes:15}") int expireMinutes,
            @Value("${vnpay.vnd-per-price-unit:25000}") long vndPerPriceUnit
    ) {
        this.tmnCode = tmnCode == null ? "" : tmnCode.trim();
        this.hashSecret = hashSecret == null ? "" : hashSecret.trim();
        this.payUrl = payUrl == null ? "" : payUrl.trim();
        this.returnUrl = returnUrl == null ? "" : returnUrl.trim();
        this.expireMinutes = expireMinutes;
        this.vndPerPriceUnit = vndPerPriceUnit;
    }

    public String getTmnCode() { return tmnCode; }
    public String getHashSecret() { return hashSecret; }
    public String getPayUrl() { return payUrl; }
    public String getReturnUrl() { return returnUrl; }
    public int getExpireMinutes() { return expireMinutes; }
    public long getVndPerPriceUnit() { return vndPerPriceUnit; }

    public boolean isConfigured() {
        return !tmnCode.isBlank() && !hashSecret.isBlank() && !payUrl.isBlank() && !returnUrl.isBlank();
    }
}
