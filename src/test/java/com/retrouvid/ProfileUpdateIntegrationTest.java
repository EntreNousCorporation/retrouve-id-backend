package com.retrouvid;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileUpdateIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Test
    void updatePhoneAndReadBack() throws Exception {
        String token = TestAuthHelper.registerAndGetToken(
                mvc, om, "phone@test.com", "password123", "Ph", "One");

        mvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+2250708010203\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value("+2250708010203"));

        mvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.phone").value("+2250708010203"));
    }

    @Test
    void phoneUniquenessEnforced() throws Exception {
        String tokenA = TestAuthHelper.registerAndGetToken(
                mvc, om, "a@test.com", "password123", "A", "A");
        String tokenB = TestAuthHelper.registerAndGetToken(
                mvc, om, "b@test.com", "password123", "B", "B");

        mvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+2250700000001\"}"))
                .andExpect(status().isOk());

        mvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+2250700000001\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void changePassword_success() throws Exception {
        String email = "pwd@test.com";
        String token = TestAuthHelper.registerAndGetToken(
                mvc, om, email, "password123", "Pwd", "Ch");

        mvc.perform(post("/api/v1/users/me/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password123\",\"newPassword\":\"newpwd12345\"}"))
                .andExpect(status().isOk());

        // login avec le nouveau mot de passe OK
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"newpwd12345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        // login avec l'ancien mot de passe rejeté
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_wrongCurrent_rejected() throws Exception {
        String token = TestAuthHelper.registerAndGetToken(
                mvc, om, "pwd2@test.com", "password123", "P2", "P2");

        mvc.perform(post("/api/v1/users/me/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrongpwd\",\"newPassword\":\"newpwd12345\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_sameAsCurrent_rejected() throws Exception {
        String token = TestAuthHelper.registerAndGetToken(
                mvc, om, "pwd3@test.com", "password123", "P3", "P3");

        mvc.perform(post("/api/v1/users/me/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password123\",\"newPassword\":\"password123\"}"))
                .andExpect(status().isBadRequest());
    }
}
