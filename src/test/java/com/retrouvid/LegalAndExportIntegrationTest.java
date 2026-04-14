package com.retrouvid;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LegalAndExportIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Test
    void cguIsPublic() throws Exception {
        mvc.perform(get("/api/v1/legal/cgu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value("cgu"))
                .andExpect(jsonPath("$.data.content",
                        containsString("Conditions Générales d'Utilisation")));
    }

    @Test
    void privacyIsPublic() throws Exception {
        mvc.perform(get("/api/v1/legal/privacy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content",
                        containsString("Politique de confidentialité")));
    }

    @Test
    void unknownLegalSlugIs404() throws Exception {
        mvc.perform(get("/api/v1/legal/other"))
                .andExpect(status().isNotFound());
    }

    @Test
    void exportMyData_returnsProfileAndDeclarations() throws Exception {
        String email = "export@test.com";
        String token = TestAuthHelper.registerAndGetToken(
                mvc, om, email, "password123", "Ex", "Port");

        mvc.perform(post("/api/v1/declarations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PERTE","documentType":"CNI","ownerName":"Ex Port","city":"Abidjan"}
                                """))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/users/me/export")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.email").value(email))
                .andExpect(jsonPath("$.data.profile.firstName").value("Ex"))
                .andExpect(jsonPath("$.data.declarations[0].documentType").value("CNI"))
                .andExpect(jsonPath("$.data.exportedAt").isNotEmpty());
    }

    @Test
    void exportMyData_requiresAuth() throws Exception {
        mvc.perform(get("/api/v1/users/me/export"))
                .andExpect(status().isUnauthorized());
    }
}
