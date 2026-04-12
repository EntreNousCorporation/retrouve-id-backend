package com.retrouvid.modules.matching.service;

import com.retrouvid.modules.declaration.entity.Declaration;
import com.retrouvid.modules.declaration.entity.DeclarationStatus;
import com.retrouvid.modules.declaration.entity.DeclarationType;
import com.retrouvid.modules.declaration.repository.DeclarationRepository;
import com.retrouvid.modules.matching.algorithm.MatchingAlgorithm;
import com.retrouvid.modules.matching.entity.Match;
import com.retrouvid.modules.matching.entity.MatchStatus;
import com.retrouvid.modules.matching.repository.MatchRepository;
import com.retrouvid.modules.notification.entity.NotificationType;
import com.retrouvid.modules.notification.service.NotificationService;
import com.retrouvid.shared.exception.ApiException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingService {

    private final DeclarationRepository declarationRepository;
    private final MatchRepository matchRepository;
    private final MatchingAlgorithm algorithm;
    private final NotificationService notificationService;

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
            double score = algorithm.score(
                    source.getType() == DeclarationType.PERTE ? source : candidate,
                    source.getType() == DeclarationType.DECOUVERTE ? source : candidate
            );
            if (score < MatchingAlgorithm.NOTIFY_THRESHOLD) continue;

            Declaration perte = source.getType() == DeclarationType.PERTE ? source : candidate;
            Declaration decouverte = source.getType() == DeclarationType.DECOUVERTE ? source : candidate;

            if (matchRepository.findByDeclarationPerteIdAndDeclarationDecouverteId(perte.getId(), decouverte.getId()).isPresent()) {
                continue;
            }
            Match m = Match.builder()
                    .declarationPerte(perte)
                    .declarationDecouverte(decouverte)
                    .score(score)
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

    private void ensureParticipant(Match m, UUID userId) {
        boolean ok = m.getDeclarationPerte().getUser().getId().equals(userId)
                || m.getDeclarationDecouverte().getUser().getId().equals(userId);
        if (!ok) throw ApiException.forbidden("Action non autorisée");
    }
}
