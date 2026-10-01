package com.example.bookinghotel.backend.service;

import com.example.bookinghotel.backend.domain.UserEntity;
import com.example.bookinghotel.backend.exception.UnauthorizedException;
import com.example.bookinghotel.backend.repository.UserJpaRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    private final UserJpaRepository userRepository;

    public CurrentUserService(UserJpaRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserEntity requireUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new UnauthorizedException("AUTH_REQUIRED", "Authentication is required");
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new UnauthorizedException("USER_NOT_FOUND", "Authenticated user no longer exists"));
    }
}
