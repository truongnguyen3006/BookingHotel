package com.example.bookinghotel.backend.bootstrap;

import com.example.bookinghotel.backend.domain.Role;
import com.example.bookinghotel.backend.domain.UserEntity;
import com.example.bookinghotel.backend.repository.UserJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Component
@ConditionalOnProperty(name = "app.admin.bootstrap.enabled", havingValue = "true")
public class AdminBootstrapRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserJpaRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String displayName;

    public AdminBootstrapRunner(
            UserJpaRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.bootstrap.email:}") String email,
            @Value("${app.admin.bootstrap.password:}") String password,
            @Value("${app.admin.bootstrap.display-name:Hotel Admin}") String displayName
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (normalizedEmail.isBlank()) {
            throw new IllegalStateException("ADMIN_EMAIL is required when admin bootstrap is enabled");
        }

        var existing = userRepository.findByEmailIgnoreCase(normalizedEmail);
        if (existing.isPresent()) {
            UserEntity user = existing.get();
            if (user.getRole() != Role.ADMIN) {
                user.promoteToAdmin();
                userRepository.save(user);
                log.info("Promoted configured bootstrap user to ADMIN: {}", normalizedEmail);
            } else {
                log.info("Bootstrap admin already exists: {}", normalizedEmail);
            }
            return;
        }

        if (password == null || password.length() < 8) {
            throw new IllegalStateException("ADMIN_PASSWORD must contain at least 8 characters when creating an admin");
        }

        userRepository.save(new UserEntity(
                normalizedEmail,
                passwordEncoder.encode(password),
                displayName == null || displayName.isBlank() ? "Hotel Admin" : displayName.trim(),
                Role.ADMIN,
                true,
                Instant.now()
        ));
        log.info("Created bootstrap ADMIN user: {}", normalizedEmail);
    }
}
