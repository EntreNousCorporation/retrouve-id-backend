package com.retrouvid.modules.gamification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Reset mensuel des points du leaderboard : le 1er du mois à 00:05 UTC.
 *  Les points totaux (`totalPoints`) restent intouchés, seul le compteur
 *  mensuel est remis à zéro. */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeaderboardResetJob {

    private final GamificationService gamification;

    @Scheduled(cron = "${app.jobs.leaderboard-reset.cron:0 5 0 1 * *}")
    public void resetMonthly() {
        log.info("LeaderboardResetJob : lancement");
        gamification.resetMonthlyPoints();
    }
}
