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
class AccountDeletionIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Test
    void deleteMe_withCorrectPassword_removesAccountAndCascades() throws Exception {
        String email = "del@test.com";
        String token = TestAuthHelper.registerAndGetToken(
                mvc, om, email, "password123", "Del", "User");

        // Crée une déclaration qui devrait cascader.
        mvc.perform(post("/api/v1/declarations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PERTE","documentType":"CNI","ownerName":"Del User","city":"Abidjan"}
                                """))
                .andExpect(status().isOk());

        // Supprime le compte.
        mvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isOk());

        // Le token n'est plus valide (user n'existe plus).
        mvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        // Login impossible.
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteMe_withWrongPassword_isRejected() throws Exception {
        String token = TestAuthHelper.registerAndGetToken(
                mvc, om, "del2@test.com", "password123", "Del", "Two");

        mvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrongpwd123\"}"))
                .andExpect(status().isUnauthorized());

        // Le compte existe toujours.
        mvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void deleteMe_withoutAuth_isRejected() throws Exception {
        mvc.perform(delete("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"anything\"}"))
                .andExpect(status().isForbidden());
    }
}
