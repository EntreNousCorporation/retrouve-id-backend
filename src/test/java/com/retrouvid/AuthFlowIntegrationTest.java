package com.retrouvid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper om;

    @Test
    void registerDoesNotReturnTokens_mustVerifyOtpFirst() throws Exception {
        String body = """
                {"email":"alice@test.com","password":"password123","firstName":"Alice","lastName":"Test"}
                """;
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.needsVerification").value(true))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
    }

    @Test
    void fullFlow_registerThenOtpThenDeclaration() throws Exception {
        String email = "bob@test.com";
        String body = """
                {"email":"%s","password":"password123","firstName":"Bob","lastName":"Test"}
                """.formatted(email);
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        String otpCode = requestFreshOtp(email);
        String token = verifyOtp(email, otpCode);
        assertThat(token).isNotBlank();

        String declBody = """
                {"type":"PERTE","documentType":"CNI","ownerName":"Bob Test","city":"Abidjan","latitude":5.32,"longitude":-4.03,"dateEvent":"2026-04-01"}
                """;
        mvc.perform(post("/api/v1/declarations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(declBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void protectedEndpointRejectsUnauthenticatedRequest() throws Exception {
        // Spring Security renvoie 403 par défaut quand aucune auth n'est fournie.
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void jwtClaim_givenName_isPresentAfterOtpVerification() throws Exception {
        String email = "claire@test.com";
        String body = """
                {"email":"%s","password":"password123","firstName":"Claire","lastName":"T"}
                """.formatted(email);
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        String code = requestFreshOtp(email);
        String token = verifyOtp(email, code);

        // payload (partie 2 du JWT) doit contenir given_name=Claire
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
        assertThat(payload).contains("\"given_name\":\"Claire\"");
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private String requestFreshOtp(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/send-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = om.readTree(r.getResponse().getContentAsString());
        return json.at("/data/code").asText();
    }

    private String verifyOtp(String email, String code) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString())
                .at("/data/accessToken").asText();
    }
}
