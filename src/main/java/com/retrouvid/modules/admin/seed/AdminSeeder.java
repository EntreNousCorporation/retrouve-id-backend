package com.retrouvid.modules.admin.seed;

import com.retrouvid.modules.user.entity.Role;
import com.retrouvid.modules.user.entity.User;
import com.retrouvid.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:}")
    private String email;

    @Value("${app.admin.password:}")
    private String password;

    @Value("${app.admin.first-name:Admin}")
    private String firstName;

    @Value("${app.admin.last-name:RetrouvID}")
    private String lastName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            log.info("AdminSeeder: app.admin.email/password not set — skipping");
            return;
        }

        var existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.getRole() != Role.ADMIN) {
                user.setRole(Role.ADMIN);
                log.info("AdminSeeder: promoted existing user {} to ADMIN", email);
            } else {
                log.debug("AdminSeeder: admin {} already present", email);
            }
            return;
        }

        User admin = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .firstName(firstName)
                .lastName(lastName)
                .role(Role.ADMIN)
                .verified(true)
                .build();
        userRepository.save(admin);
        log.warn("AdminSeeder: created default ADMIN {} — CHANGE PASSWORD ASAP", email);
    }
}
