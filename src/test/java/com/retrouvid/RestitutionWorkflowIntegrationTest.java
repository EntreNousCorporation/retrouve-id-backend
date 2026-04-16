package com.retrouvid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retrouvid.modules.admin.entity.RelayPoint;
import com.retrouvid.modules.admin.repository.RelayPointRepository;
import com.retrouvid.modules.matching.entity.Match;
import com.retrouvid.modules.matching.entity.MatchStatus;
import com.retrouvid.modules.matching.repository.MatchRepository;
import com.retrouvid.modules.user.entity.Role;
import com.retrouvid.modules.user.entity.User;
import com.retrouvid.modules.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RestitutionWorkflowIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired UserRepository userRepository;
    @Autowired RelayPointRepository relayPointRepository;
    @Autowired MatchRepository matchRepository;

    @Test
    @Transactional
    void fullFlow_perteDecouverteConfirmChooseRelayDropPickup() throws Exception {
        String tPerte = TestAuthHelper.registerAndGetToken(mvc, om,
                "owner@test.com", "password123", "Jean", "Dupont");
        String tTrouve = TestAuthHelper.registerAndGetToken(mvc, om,
                "finder@test.com", "password123", "Paul", "Kouadio");
        String tAgent = TestAuthHelper.registerAndGetToken(mvc, om,
                "agent@test.com", "password123", "Awa", "Traoré");

        UUID agentId = userRepository.findByEmail("agent@test.com").orElseThrow().getId();
        User agent = userRepository.findById(agentId).orElseThrow();
        agent.setRole(Role.AGENT);
        userRepository.save(agent);
        RelayPoint relay = RelayPoint.builder()
                .name("Station Shell Cocody")
                .type("STATION_ESSENCE")
                .address("Bd Latrille")
                .city("Abidjan")
                .latitude(5.34)
                .longitude(-4.01)
                .phone("+225010203")
                .active(true)
                .user(agent)
                .build();
        relay = relayPointRepository.save(relay);

        String perteBody = """
                {"type":"PERTE","documentType":"CNI","ownerName":"Jean Dupont",
                "documentNumberFull":"CI9876543210","dateOfBirth":"1992-07-14",
                "discriminantHint":"signature bleue au dos",
                "city":"Abidjan","latitude":5.32,"longitude":-4.03,"dateEvent":"2026-04-10"}
                """;
        mvc.perform(post("/api/v1/declarations")
                        .header("Authorization", "Bearer " + tPerte)
                        .contentType(MediaType.APPLICATION_JSON).content(perteBody))
                .andExpect(status().isOk());

        String trouveBody = """
                {"type":"DECOUVERTE","documentType":"CNI","ownerName":"Jean Dupont",
                "documentNumberFull":"CI9876543210","dateOfBirth":"1992-07-14",
                "discriminantHint":"signature bleue au dos",
                "city":"Abidjan","latitude":5.33,"longitude":-4.02,"dateEvent":"2026-04-11"}
                """;
        mvc.perform(post("/api/v1/declarations")
                        .header("Authorization", "Bearer " + tTrouve)
                        .contentType(MediaType.APPLICATION_JSON).content(trouveBody))
                .andExpect(status().isOk());

        MvcResult matchesRes = mvc.perform(get("/api/v1/matches")
                        .header("Authorization", "Bearer " + tPerte))
                .andExpect(status().isOk()).andReturn();
        JsonNode first = om.readTree(matchesRes.getResponse().getContentAsString()).at("/data/items/0");
        String matchId = first.get("id").asText();
        assertThat(first.get("verificationScore").asDouble()).isGreaterThanOrEqualTo(70.0);

        mvc.perform(patch("/api/v1/matches/" + matchId + "/confirm")
                        .header("Authorization", "Bearer " + tPerte))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        // Le trouveur choisit le relais
        mvc.perform(post("/api/v1/matches/" + matchId + "/choose-relay")
                        .header("Authorization", "Bearer " + tTrouve)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"relayPointId\":\"" + relay.getId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HANDOVER_PENDING"))
                .andExpect(jsonPath("$.data.handoverDeadline").isNotEmpty());

        // L'agent valide le dépôt (le code est généré côté backend ;
        // on le récupère via le repo pour simuler la notif SMS reçue)
        mvc.perform(post("/api/v1/matches/" + matchId + "/drop")
                        .header("Authorization", "Bearer " + tAgent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DROPPED"));

        // En test on ne peut pas intercepter le SMS : on force un code connu
        // via la base pour tester le pickup.
        Match m = matchRepository.findById(UUID.fromString(matchId)).orElseThrow();
        String probeCode = "654321";
        // Remplacement direct du hash pour pouvoir tester la validation côté
        // agent (le vrai code SMS reste non-accessible en test).
        m.setCodeHash(new com.retrouvid.shared.hashing.HashingService(
                "retrouvid-dev-salt-change-me").hash(probeCode));
        m.setCodeExpiresAt(Instant.now().plusSeconds(3600));
        matchRepository.save(m);

        // Essai code incorrect → 400 + incrément compteur
        mvc.perform(post("/api/v1/matches/" + matchId + "/pickup")
                        .header("Authorization", "Bearer " + tAgent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"000000\"}"))
                .andExpect(status().isBadRequest());

        // Essai code correct → PICKED_UP
        mvc.perform(post("/api/v1/matches/" + matchId + "/pickup")
                        .header("Authorization", "Bearer " + tAgent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + probeCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PICKED_UP"))
                .andExpect(jsonPath("$.data.pickedUpAt").isNotEmpty());

        Match restituted = matchRepository.findById(UUID.fromString(matchId)).orElseThrow();
        assertThat(restituted.getStatus()).isEqualTo(MatchStatus.PICKED_UP);
        assertThat(restituted.getDeclarationPerte().getStatus().name()).isEqualTo("RESTITUTED");
        assertThat(restituted.getDeclarationDecouverte().getStatus().name()).isEqualTo("RESTITUTED");
    }
}
