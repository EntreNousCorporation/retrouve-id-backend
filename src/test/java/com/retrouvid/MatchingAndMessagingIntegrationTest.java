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
class MatchingAndMessagingIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    private String register(String email) throws Exception {
        return TestAuthHelper.registerAndGetToken(mvc, om, email, "password123", "X", "Y");
    }

    @Test
    void matchTwoDeclarationsAndChat() throws Exception {
        String tPerte = register("loser@test.com");
        String tTrouve = register("finder@test.com");

        String perteBody = """
                {"type":"PERTE","documentType":"CNI","ownerName":"Jean Dupont","city":"Abidjan","latitude":5.32,"longitude":-4.03,"dateEvent":"2026-04-01"}
                """;
        mvc.perform(post("/api/v1/declarations")
                        .header("Authorization", "Bearer " + tPerte)
                        .contentType(MediaType.APPLICATION_JSON).content(perteBody))
                .andExpect(status().isOk());

        String trouveBody = """
                {"type":"DECOUVERTE","documentType":"CNI","ownerName":"Jean Dupond","city":"Abidjan","latitude":5.33,"longitude":-4.02,"dateEvent":"2026-04-02"}
                """;
        mvc.perform(post("/api/v1/declarations")
                        .header("Authorization", "Bearer " + tTrouve)
                        .contentType(MediaType.APPLICATION_JSON).content(trouveBody))
                .andExpect(status().isOk());

        MvcResult matches = mvc.perform(get("/api/v1/matches")
                        .header("Authorization", "Bearer " + tPerte))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").isNotEmpty())
                .andReturn();
        JsonNode first = om.readTree(matches.getResponse().getContentAsString()).at("/data/items/0");
        String matchId = first.get("id").asText();
        assertThat(first.get("score").asDouble()).isGreaterThanOrEqualTo(60.0);

        MvcResult conv = mvc.perform(post("/api/v1/conversations/from-match")
                        .header("Authorization", "Bearer " + tPerte)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matchId\":\"" + matchId + "\"}"))
                .andExpect(status().isOk()).andReturn();
        String convId = om.readTree(conv.getResponse().getContentAsString()).at("/data/id").asText();

        mvc.perform(post("/api/v1/conversations/" + convId + "/messages")
                        .header("Authorization", "Bearer " + tPerte)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Bonjour, je crois que c'est ma pièce\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNotEmpty());

        mvc.perform(get("/api/v1/conversations/" + convId + "/messages")
                        .header("Authorization", "Bearer " + tTrouve))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].content").value("Bonjour, je crois que c'est ma pièce"));

        mvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + tTrouve))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isNotEmpty());
    }
}
