package com.retrouvid;

import com.retrouvid.modules.admin.seed.AdminSeeder;
import com.retrouvid.modules.user.entity.Role;
import com.retrouvid.modules.user.entity.User;
import com.retrouvid.modules.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.admin.email=seeded-admin@test.com",
        "app.admin.password=seedpass123"
})
class AdminSeederTest {

    @Autowired AdminSeeder seeder;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder encoder;

    @Test
    void createsAdminOnFirstRun_andPromotesExistingOnSecond() {
        ApplicationArguments args = new DefaultApplicationArguments();

        seeder.run(args);
        User u1 = userRepository.findByEmail("seeded-admin@test.com").orElseThrow();
        assertThat(u1.getRole()).isEqualTo(Role.ADMIN);
        assertThat(encoder.matches("seedpass123", u1.getPasswordHash())).isTrue();

        // Demote manually then rerun seeder → should promote back
        u1.setRole(Role.USER);
        userRepository.saveAndFlush(u1);
        seeder.run(args);
        User u2 = userRepository.findByEmail("seeded-admin@test.com").orElseThrow();
        assertThat(u2.getRole()).isEqualTo(Role.ADMIN);

        // No duplicate
        assertThat(userRepository.findAll().stream()
                .filter(u -> "seeded-admin@test.com".equals(u.getEmail())).count()).isEqualTo(1);
    }
}
