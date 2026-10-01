package com.example.bookinghotel.backend.vnpay;

import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VnPaySignerTest {
    private static final String SECRET = "sandbox-secret-for-test-only";

    @Test
    void signedQueryRoundTrip_verifiesSuccessfully() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", "B0X8KC9I");
        params.put("vnp_Amount", "125000000");
        params.put("vnp_OrderInfo", "Thanh toan BookingHotel booking 7");
        params.put("vnp_TxnRef", "BH71700000000000ABC12345");

        String signedQuery = VnPaySigner.buildSignedQuery(params, SECRET);
        Map<String, String> callback = parseQuery(signedQuery);

        assertTrue(VnPaySigner.verify(callback, SECRET));
    }

    @Test
    void changedCallbackValue_failsVerification() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_TmnCode", "B0X8KC9I");
        params.put("vnp_Amount", "125000000");
        params.put("vnp_TxnRef", "BH71700000000000ABC12345");

        Map<String, String> callback = parseQuery(VnPaySigner.buildSignedQuery(params, SECRET));
        callback.put("vnp_Amount", "99999900");

        assertFalse(VnPaySigner.verify(callback, SECRET));
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            String value = pair.length > 1
                    ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8)
                    : "";
            result.put(key, value);
        }
        return result;
    }
}
