package com.retrouvid.modules.matching.service;

import com.retrouvid.modules.admin.entity.RelayPoint;
import com.retrouvid.modules.admin.repository.RelayPointRepository;
import com.retrouvid.modules.gamification.service.GamificationService;
import com.retrouvid.modules.declaration.entity.Declaration;
import com.retrouvid.modules.declaration.entity.DeclarationStatus;
import com.retrouvid.modules.declaration.entity.DeclarationType;
import com.retrouvid.modules.declaration.repository.DeclarationRepository;
import com.retrouvid.modules.matching.algorithm.MatchingAlgorithm;
import com.retrouvid.modules.matching.algorithm.VerificationScorer;
import com.retrouvid.modules.matching.entity.Match;
import com.retrouvid.modules.matching.entity.MatchStatus;
import com.retrouvid.modules.matching.repository.MatchRepository;
import com.retrouvid.modules.notification.entity.NotificationType;
import com.retrouvid.modules.notification.service.NotificationService;
import com.retrouvid.modules.user.entity.User;
import com.retrouvid.shared.exception.ApiException;
import com.retrouvid.shared.hashing.HashingService;
import com.retrouvid.shared.sms.NotificationChannelRouter;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingService {

    private static final Duration HANDOVER_TTL = Duration.ofHours(48);
    private static final Duration PICKUP_TTL = Duration.ofDays(14);
    private static final Duration PICKUP_LOCK = Duration.ofMinutes(30);
    private static final int MAX_PICKUP_ATTEMPTS = 3;
    private static final SecureRandom RNG = new SecureRandom();

    private final DeclarationRepository declarationRepository;
    private final MatchRepository matchRepository;
    private final RelayPointRepository relayPointRepository;
    private final MatchingAlgorithm algorithm;
    private final VerificationScorer verificationScorer;
    private final NotificationService notificationService;
    private final NotificationChannelRouter channelRouter;
    private final HashingService hashingService;
    private final GamificationService gamificationService;

    @Transactional
    public List<Match> computeForDeclaration(Declaration source) {
        if (source.getStatus() != DeclarationStatus.ACTIVE) return List.of();
        DeclarationType opposite = source.getType() == DeclarationType.PERTE
                ? DeclarationType.DECOUVERTE : DeclarationType.PERTE;

        Specification<Declaration> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("type"), opposite));
            ps.add(cb.equal(root.get("documentType"), source.getDocumentType()));
            ps.add(cb.equal(root.get("status"), DeclarationStatus.ACTIVE));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        List<Declaration> candidates = declarationRepository.findAll(spec);

        List<Match> created = new ArrayList<>();
        for (Declaration candidate : candidates) {
            Declaration perte = source.getType() == DeclarationType.PERTE ? source : candidate;
            Declaration decouverte = source.getType() == DeclarationType.DECOUVERTE ? source : candidate;

            double score = algorithm.score(perte, decouverte);
            if (score < MatchingAlgorithm.NOTIFY_THRESHOLD) continue;

            double verification = verificationScorer.score(perte, decouverte);
            if (verification < VerificationScorer.MATCH_THRESHOLD) continue;

            if (matchRepository.findByDeclarationPerteIdAndDeclarationDecouverteId(perte.getId(), decouverte.getId()).isPresent()) {
                continue;
            }
            Match m = Match.builder()
                    .declarationPerte(perte)
                    .declarationDecouverte(decouverte)
                    .score(score)
                    .verificationScore(verification)
                    .status(MatchStatus.PENDING)
                    .build();
            m = matchRepository.save(m);
            created.add(m);

            notifyMatch(m);
        }
        return created;
    }

    private void notifyMatch(Match m) {
        String title = "Correspondance potentielle";
        String body = String.format("Nous avons trouvé une correspondance (%.0f%%) pour votre %s.",
                m.getScore(),
                m.getDeclarationPerte().getDocumentType().name());
        Map<String, String> data = Map.of("matchId", m.getId().toString(), "type", "MATCH_FOUND");
        notificationService.notify(m.getDeclarationPerte().getUser().getId(), NotificationType.MATCH_FOUND, title, body, data);
        notificationService.notify(m.getDeclarationDecouverte().getUser().getId(), NotificationType.MATCH_FOUND, title, body, data);
    }

    @Transactional(readOnly = true)
    public Page<Match> findForUser(UUID userId, Pageable pageable) {
        return matchRepository.findForUser(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Match get(UUID id, UUID userId) {
        Match m = matchRepository.findById(id).orElseThrow(() -> ApiException.notFound("Match introuvable"));
        ensureParticipant(m, userId);
        return m;
    }

    @Transactional
    public Match confirm(UUID id, UUID userId) {
        Match m = get(id, userId);
        m.setStatus(MatchStatus.CONFIRMED);
        m.getDeclarationPerte().setStatus(DeclarationStatus.MATCHED);
        m.getDeclarationDecouverte().setStatus(DeclarationStatus.MATCHED);
        return m;
    }

    @Transactional
    public Match reject(UUID id, UUID userId) {
        Match m = get(id, userId);
        m.setStatus(MatchStatus.REJECTED);
        return m;
    }

    /**
     * Le trouveur (owner de la DECOUVERTE) choisit un point relais où déposer la
     * pièce. Le match passe en HANDOVER_PENDING avec une deadline de 48h.
     */
    @Transactional
    public Match chooseRelay(UUID id, UUID userId, UUID relayPointId) {
        Match m = matchRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Match introuvable"));
        if (!m.getDeclarationDecouverte().getUser().getId().equals(userId)) {
            throw ApiException.forbidden("Seul le trouveur peut choisir un point relais");
        }
        if (m.getStatus() != MatchStatus.CONFIRMED) {
            throw ApiException.conflict("Le match doit être confirmé avant le choix du relais");
        }
        RelayPoint relay = relayPointRepository.findById(relayPointId)
                .orElseThrow(() -> ApiException.notFound("Point relais introuvable"));
        if (!relay.isActive()) {
            throw ApiException.conflict("Point relais inactif");
        }
        m.setRelayPoint(relay);
        m.setHandoverDeadline(Instant.now().plus(HANDOVER_TTL));
        m.setStatus(MatchStatus.HANDOVER_PENDING);
        return m;
    }

    /**
     * L'agent du point relais confirme le dépôt physique de la pièce. Génère un
     * code de retrait 6 chiffres, le stocke hashé, notifie le perdeur (push +
     * SMS ou email).
     */
    @Transactional
    public Match dropAtRelay(UUID id, UUID agentUserId) {
        Match m = matchRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Match introuvable"));
        if (m.getStatus() != MatchStatus.HANDOVER_PENDING) {
            throw ApiException.conflict("Le match n'attend pas de dépôt");
        }
        RelayPoint relay = m.getRelayPoint();
        if (relay == null) throw ApiException.conflict("Aucun relais associé au match");
        if (relay.getUser() == null || !relay.getUser().getId().equals(agentUserId)) {
            throw ApiException.forbidden("Vous n'êtes pas l'agent de ce point relais");
        }

        String code = generateRetrievalCode();
        m.setCodeHash(hashingService.hash(code));
        m.setCodeExpiresAt(Instant.now().plus(PICKUP_TTL));
        m.setDroppedAt(Instant.now());
        m.setAgentUser(relay.getUser());
        m.setStatus(MatchStatus.DROPPED);

        User owner = m.getDeclarationPerte().getUser();
        String title = "Votre pièce est au point relais";
        String body = String.format(
                "Votre pièce a été déposée au relais « %s » (%s). Code de retrait : %s. Valable 14 jours.",
                relay.getName(), relay.getAddress() == null ? relay.getCity() : relay.getAddress(), code);
        notificationService.notify(owner.getId(), NotificationType.PICKUP_CODE, title, body,
                Map.of("matchId", m.getId().toString(), "type", "PICKUP_CODE"));
        channelRouter.sendUrgent(owner, title, body);
        return m;
    }

    /**
     * L'agent saisit le code fourni par le perdeur. 3 essais max, puis lock 30
     * min. Succès → match clos, déclarations → RESTITUTED.
     */
    @Transactional
    public Match pickup(UUID id, UUID agentUserId, String code) {
        Match m = matchRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Match introuvable"));
        if (m.getStatus() != MatchStatus.DROPPED) {
            throw ApiException.conflict("Le match n'attend pas de retrait");
        }
        RelayPoint relay = m.getRelayPoint();
        if (relay == null || relay.getUser() == null || !relay.getUser().getId().equals(agentUserId)) {
            throw ApiException.forbidden("Vous n'êtes pas l'agent de ce point relais");
        }
        if (m.getPickupLockedUntil() != null && m.getPickupLockedUntil().isAfter(Instant.now())) {
            throw ApiException.conflict("Trop d'essais, réessayez plus tard");
        }
        if (m.getCodeExpiresAt() != null && m.getCodeExpiresAt().isBefore(Instant.now())) {
            throw ApiException.conflict("Code expiré, contactez le support");
        }
        if (!hashingService.matches(code, m.getCodeHash())) {
            int attempts = m.getFailedPickupAttempts() + 1;
            m.setFailedPickupAttempts(attempts);
            if (attempts >= MAX_PICKUP_ATTEMPTS) {
                m.setPickupLockedUntil(Instant.now().plus(PICKUP_LOCK));
                log.warn("Match {} verrouillé après {} essais", m.getId(), attempts);
            }
            throw ApiException.badRequest("Code incorrect");
        }

        m.setPickedUpAt(Instant.now());
        m.setStatus(MatchStatus.PICKED_UP);
        m.setFailedPickupAttempts(0);
        m.setPickupLockedUntil(null);
        m.getDeclarationPerte().setStatus(DeclarationStatus.RESTITUTED);
        m.getDeclarationDecouverte().setStatus(DeclarationStatus.RESTITUTED);

        gamificationService.onRestitution(m);

        User owner = m.getDeclarationPerte().getUser();
        String title = "Pièce récupérée";
        String body = "Vous avez bien récupéré votre pièce. Merci d'avoir utilisé RetrouvID.";
        notificationService.notify(owner.getId(), NotificationType.PIECE_RECOVERED, title, body,
                Map.of("matchId", m.getId().toString(), "type", "PIECE_RECOVERED"));
        User finder = m.getDeclarationDecouverte().getUser();
        notificationService.notify(finder.getId(), NotificationType.PIECE_RECOVERED,
                "Merci !", "La pièce que vous avez restituée a été récupérée par son propriétaire.",
                Map.of("matchId", m.getId().toString(), "type", "PIECE_RECOVERED"));
        return m;
    }

    private String generateRetrievalCode() {
        int n = RNG.nextInt(1_000_000);
        return String.format("%06d", n);
    }

    private void ensureParticipant(Match m, UUID userId) {
        boolean ok = m.getDeclarationPerte().getUser().getId().equals(userId)
                || m.getDeclarationDecouverte().getUser().getId().equals(userId);
        if (!ok) throw ApiException.forbidden("Action non autorisée");
    }
}
