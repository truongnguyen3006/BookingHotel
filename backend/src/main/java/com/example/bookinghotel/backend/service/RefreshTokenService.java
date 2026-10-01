package com.example.bookinghotel.backend.service;

import com.example.bookinghotel.backend.domain.RefreshTokenEntity;
import com.example.bookinghotel.backend.domain.UserEntity;
import com.example.bookinghotel.backend.exception.UnauthorizedException;
import com.example.bookinghotel.backend.repository.RefreshTokenJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {
    private final RefreshTokenJpaRepository repository;
    private final long refreshDays;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenJpaRepository repository,
            @Value("${security.jwt.refresh-token-days}") long refreshDays
    ) {
        this.repository = repository;
        this.refreshDays = refreshDays;
    }

    @Transactional
    public IssuedRefreshToken issue(UserEntity user) {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plus(refreshDays, ChronoUnit.DAYS);
        repository.save(new RefreshTokenEntity(user, hash(raw), expiresAt, Instant.now()));
        return new IssuedRefreshToken(raw, expiresAt);
    }

    @Transactional
    public UserEntity consumeAndRotate(String rawToken) {
        Instant now = Instant.now();
        RefreshTokenEntity token = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException("INVALID_REFRESH_TOKEN", "Refresh token is invalid"));
        if (!token.isUsable(now)) {
            throw new UnauthorizedException("EXPIRED_REFRESH_TOKEN", "Refresh token is expired or revoked");
        }
        token.revoke(now);
        return token.getUser();
    }

    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken)).ifPresent(token -> token.revoke(Instant.now()));
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record IssuedRefreshToken(String token, Instant expiresAt) {}
}
