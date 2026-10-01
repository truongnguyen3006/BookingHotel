package com.example.bookinghotel.backend.service;

import com.example.bookinghotel.backend.api.dto.AuthResponse;
import com.example.bookinghotel.backend.api.dto.LoginRequest;
import com.example.bookinghotel.backend.api.dto.RegisterRequest;
import com.example.bookinghotel.backend.api.dto.UserResponse;
import com.example.bookinghotel.backend.domain.Role;
import com.example.bookinghotel.backend.domain.UserEntity;
import com.example.bookinghotel.backend.exception.ConflictException;
import com.example.bookinghotel.backend.exception.UnauthorizedException;
import com.example.bookinghotel.backend.repository.UserJpaRepository;
import com.example.bookinghotel.backend.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {
    private final UserJpaRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final CurrentUserService currentUserService;

    public AuthService(
            UserJpaRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            CurrentUserService currentUserService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("EMAIL_ALREADY_EXISTS", "Email is already registered");
        }
        UserEntity user = userRepository.save(new UserEntity(
                email,
                passwordEncoder.encode(request.password()),
                request.displayName().trim(),
                Role.USER,
                true,
                Instant.now()
        ));
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public UserResponse me() {
        return toUserResponse(currentUserService.requireUser());
    }

    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        UserEntity user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UnauthorizedException("INVALID_CREDENTIALS", "Email or password is incorrect"));
        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Email or password is incorrect");
        }
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        UserEntity user = refreshTokenService.consumeAndRotate(refreshToken);
        return issueTokens(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private AuthResponse issueTokens(UserEntity user) {
        String accessToken = jwtService.createAccessToken(user);
        var refresh = refreshTokenService.issue(user);
        return new AuthResponse(
                accessToken,
                refresh.token(),
                jwtService.getAccessTokenSeconds(),
                toUserResponse(user)
        );
    }

    private UserResponse toUserResponse(UserEntity user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole().name());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
