package com.retrouvid.modules.matching.service;

import com.retrouvid.modules.declaration.entity.DeclarationStatus;
import com.retrouvid.modules.matching.entity.Match;
import com.retrouvid.modules.matching.entity.MatchStatus;
import com.retrouvid.modules.matching.repository.MatchRepository;
import com.retrouvid.modules.notification.entity.NotificationType;
import com.retrouvid.modules.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchCleanupJob {

    private final MatchRepository matchRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "${app.jobs.match-cleanup.cron:0 */15 * * * *}")
    @Transactional
    public void expireStaleHandovers() {
        Instant now = Instant.now();
        List<Match> stale = matchRepository.findByStatusAndHandoverDeadlineBefore(
                MatchStatus.HANDOVER_PENDING, now);
        for (Match m : stale) {
            m.setStatus(MatchStatus.EXPIRED);
            m.getDeclarationPerte().setStatus(DeclarationStatus.ACTIVE);
            m.getDeclarationDecouverte().setStatus(DeclarationStatus.ACTIVE);
            notificationService.notify(m.getDeclarationPerte().getUser().getId(),
                    NotificationType.HANDOVER_EXPIRED,
                    "Dépôt expiré",
                    "Le trouveur n'a pas déposé la pièce dans les délais. Votre déclaration est de nouveau active.",
                    Map.of("matchId", m.getId().toString(), "type", "HANDOVER_EXPIRED"));
            notificationService.notify(m.getDeclarationDecouverte().getUser().getId(),
                    NotificationType.HANDOVER_EXPIRED,
                    "Dépôt expiré",
                    "Vous n'avez pas déposé la pièce à temps. La déclaration est de nouveau ouverte.",
                    Map.of("matchId", m.getId().toString(), "type", "HANDOVER_EXPIRED"));
            log.info("Match {} expiré (handover non déposé)", m.getId());
        }
    }

    @Scheduled(cron = "${app.jobs.match-cleanup.pickup-cron:0 0 * * * *}")
    @Transactional
    public void escalateStalePickups() {
        Instant now = Instant.now();
        List<Match> stale = matchRepository.findByStatusAndCodeExpiresAtBefore(
                MatchStatus.DROPPED, now);
        for (Match m : stale) {
            log.warn("Match {} en escalade — pièce non récupérée 14j au relais {}",
                    m.getId(),
                    m.getRelayPoint() == null ? "?" : m.getRelayPoint().getName());
            // L'entité reste en DROPPED, l'agent traite l'escalade offline
            // (transfert préfecture). On notifie seulement le perdeur.
            notificationService.notify(m.getDeclarationPerte().getUser().getId(),
                    NotificationType.HANDOVER_EXPIRED,
                    "Délai de retrait dépassé",
                    "Votre pièce n'a pas été retirée dans les 14 jours. Contactez le point relais.",
                    Map.of("matchId", m.getId().toString(), "type", "PICKUP_ESCALATED"));
        }
    }
}
