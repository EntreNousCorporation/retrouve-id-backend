package com.retrouvid;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retrouvid.modules.user.entity.Role;
import com.retrouvid.modules.user.entity.User;
import com.retrouvid.modules.user.repository.UserRepository;
import com.retrouvid.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtTokenProvider tokenProvider;

    @Test
    void adminCanSeeDashboardAndModerate() throws Exception {
        User admin = userRepository.save(User.builder()
                .email("admin@test.com")
                .passwordHash(encoder.encode("password123"))
                .role(Role.ADMIN).verified(true).build());
        String token = tokenProvider.generateAccessToken(admin.getId(), "ADMIN");

        mvc.perform(get("/api/v1/admin/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").isNumber());

        // Create a relay point
        mvc.perform(post("/api/v1/admin/relay-points")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Commissariat Cocody\",\"type\":\"COMMISSARIAT\",\"city\":\"Abidjan\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNotEmpty());
    }

    @Test
    void regularUserCannotAccessAdmin() throws Exception {
        User user = userRepository.save(User.builder()
                .email("user@test.com")
                .passwordHash(encoder.encode("password123"))
                .role(Role.USER).build());
        String token = tokenProvider.generateAccessToken(user.getId(), "USER");

        mvc.perform(get("/api/v1/admin/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
