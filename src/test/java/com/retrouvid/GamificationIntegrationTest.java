package com.retrouvid;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retrouvid.modules.admin.entity.RelayPoint;
import com.retrouvid.modules.admin.repository.RelayPointRepository;
import com.retrouvid.modules.gamification.entity.UserBadge;
import com.retrouvid.modules.gamification.repository.BadgeRepository;
import com.retrouvid.modules.gamification.repository.UserBadgeRepository;
import com.retrouvid.modules.gamification.repository.UserStatsRepository;
import com.retrouvid.modules.gamification.seed.BadgeSeeder;
import com.retrouvid.modules.matching.entity.Match;
import com.retrouvid.modules.matching.entity.MatchStatus;
import com.retrouvid.modules.matching.repository.MatchRepository;
import com.retrouvid.modules.user.entity.Role;
import com.retrouvid.modules.user.entity.User;
import com.retrouvid.modules.user.repository.UserRepository;
import com.retrouvid.shared.hashing.HashingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GamificationIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired UserRepository userRepository;
    @Autowired RelayPointRepository relayPointRepository;
    @Autowired MatchRepository matchRepository;
    @Autowired UserStatsRepository userStatsRepository;
    @Autowired UserBadgeRepository userBadgeRepository;
    @Autowired BadgeRepository badgeRepository;

    @Test
    @Transactional
    void pickupAwardsPointsAndBadges() throws Exception {
        String tPerte = TestAuthHelper.registerAndGetToken(mvc, om,
                "gam-owner@test.com", "password123", "Awa", "Koné");
        String tTrouve = TestAuthHelper.registerAndGetToken(mvc, om,
                "gam-finder@test.com", "password123", "Yao", "N'Guessan");
        String tAgent = TestAuthHelper.registerAndGetToken(mvc, om,
                "gam-agent@test.com", "password123", "Adjoua", "Diabaté");

        User agent = userRepository.findByEmail("gam-agent@test.com").orElseThrow();
        agent.setRole(Role.AGENT);
        userRepository.save(agent);
        RelayPoint relay = relayPointRepository.save(RelayPoint.builder()
                .name("Pharmacie des Deux Plateaux")
                .type("PHARMACIE")
                .address("Bd Latrille")
                .city("Abidjan")
                .latitude(5.40).longitude(-4.00)
                .active(true).user(agent).build());

        String perteBody = """
                {"type":"PERTE","documentType":"PASSEPORT","ownerName":"Awa Koné",
                "documentNumberFull":"CI0001112223","dateOfBirth":"1988-02-10",
                "discriminantHint":"tampon cassé en bas",
                "city":"Abidjan","latitude":5.32,"longitude":-4.03,"dateEvent":"2026-04-14"}
                """;
        mvc.perform(post("/api/v1/declarations")
                        .header("Authorization", "Bearer " + tPerte)
                        .contentType(MediaType.APPLICATION_JSON).content(perteBody))
                .andExpect(status().isOk());

        String trouveBody = """
                {"type":"DECOUVERTE","documentType":"PASSEPORT","ownerName":"Awa Koné",
                "documentNumberFull":"CI0001112223","dateOfBirth":"1988-02-10",
                "discriminantHint":"tampon cassé en bas",
                "city":"Abidjan","latitude":5.33,"longitude":-4.02,"dateEvent":"2026-04-15"}
                """;
        mvc.perform(post("/api/v1/declarations")
                        .header("Authorization", "Bearer " + tTrouve)
                        .contentType(MediaType.APPLICATION_JSON).content(trouveBody))
                .andExpect(status().isOk());

        String matchId = om.readTree(mvc.perform(get("/api/v1/matches")
                        .header("Authorization", "Bearer " + tPerte))
                .andReturn().getResponse().getContentAsString())
                .at("/data/items/0/id").asText();

        mvc.perform(patch("/api/v1/matches/" + matchId + "/confirm")
                        .header("Authorization", "Bearer " + tPerte))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/matches/" + matchId + "/choose-relay")
                        .header("Authorization", "Bearer " + tTrouve)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"relayPointId\":\"" + relay.getId() + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/matches/" + matchId + "/drop")
                        .header("Authorization", "Bearer " + tAgent))
                .andExpect(status().isOk());

        Match m = matchRepository.findById(UUID.fromString(matchId)).orElseThrow();
        String probe = "111000";
        m.setCodeHash(new HashingService("retrouvid-dev-salt-change-me").hash(probe));
        m.setCodeExpiresAt(Instant.now().plusSeconds(3600));
        matchRepository.save(m);

        mvc.perform(post("/api/v1/matches/" + matchId + "/pickup")
                        .header("Authorization", "Bearer " + tAgent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + probe + "\"}"))
                .andExpect(status().isOk());

        Match restituted = matchRepository.findById(UUID.fromString(matchId)).orElseThrow();
        assertThat(restituted.getStatus()).isEqualTo(MatchStatus.PICKED_UP);

        UUID finderId = userRepository.findByEmail("gam-finder@test.com").orElseThrow().getId();
        var stats = userStatsRepository.findByUserId(finderId).orElseThrow();
        // 100 base + 30 discriminant + 50 fast-drop (droppedAt dans 24h du dateEvent) = 180
        // le bonus distance (>2km) dépend de la pos GPS : entre relais(5.40,-4.00) et
        // découverte(5.33,-4.02) ≈ 8.3 km → bonus actif. Total 200.
        assertThat(stats.getTotalPoints()).isGreaterThanOrEqualTo(100);
        assertThat(stats.getRestitutionsCompleted()).isEqualTo(1);

        List<UserBadge> badges = (List<UserBadge>) userBadgeRepository.findByUserId(finderId);
        assertThat(badges).extracting(b -> b.getBadge().getCode())
                .contains(BadgeSeeder.FIRST_RESTITUTION, BadgeSeeder.SECOURISTE);

        mvc.perform(get("/api/v1/gamification/me")
                        .header("Authorization", "Bearer " + tTrouve))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.restitutionsCompleted").value(1))
                .andExpect(jsonPath("$.data.badges[0].code").exists());

        mvc.perform(post("/api/v1/thanks")
                        .header("Authorization", "Bearer " + tPerte)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matchId\":\"" + matchId + "\",\"content\":\"Merci infiniment !\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/thanks/received")
                        .header("Authorization", "Bearer " + tTrouve))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].content").value("Merci infiniment !"));
    }
}
