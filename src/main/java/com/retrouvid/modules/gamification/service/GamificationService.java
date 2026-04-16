package com.retrouvid.modules.gamification.service;

import com.retrouvid.modules.declaration.entity.Declaration;
import com.retrouvid.modules.declaration.entity.DocumentType;
import com.retrouvid.modules.gamification.entity.Badge;
import com.retrouvid.modules.gamification.entity.UserBadge;
import com.retrouvid.modules.gamification.entity.UserStats;
import com.retrouvid.modules.gamification.repository.BadgeRepository;
import com.retrouvid.modules.gamification.repository.UserBadgeRepository;
import com.retrouvid.modules.gamification.repository.UserStatsRepository;
import com.retrouvid.modules.gamification.seed.BadgeSeeder;
import com.retrouvid.modules.matching.entity.Match;
import com.retrouvid.modules.notification.entity.NotificationType;
import com.retrouvid.modules.notification.service.NotificationService;
import com.retrouvid.modules.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Attribue points et badges au trouveur à chaque restitution complétée
 *  (transition PICKED_UP). Appelé synchronement par MatchingService#pickup. */
@Slf4j
@Service
@RequiredArgsConstructor
public class GamificationService {

    private static final int BASE_POINTS = 100;
    private static final int BONUS_DISCRIMINANT = 30;
    private static final int BONUS_FAST_DROP = 50;
    private static final int BONUS_DISTANCE = 20;
    private static final double DISTANCE_THRESHOLD_KM = 2.0;
    private static final Duration FAST_DROP_WINDOW = Duration.ofHours(24);

    private final UserStatsRepository userStatsRepository;
    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final NotificationService notificationService;

    @Transactional
    public GamificationOutcome onRestitution(Match match) {
        User finder = match.getDeclarationDecouverte().getUser();
        UserStats stats = userStatsRepository.findByUserId(finder.getId())
                .orElseGet(() -> userStatsRepository.save(
                        UserStats.builder().userId(finder.getId()).build()));

        int points = BASE_POINTS;
        Declaration decouverte = match.getDeclarationDecouverte();
        if (decouverte.getDiscriminantHint() != null && !decouverte.getDiscriminantHint().isBlank()) {
            points += BONUS_DISCRIMINANT;
        }
        boolean fastDrop = isFastDrop(match);
        if (fastDrop) {
            points += BONUS_FAST_DROP;
        }
        if (isFarRelay(match)) {
            points += BONUS_DISTANCE;
        }

        stats.setTotalPoints(stats.getTotalPoints() + points);
        stats.setCurrentMonthPoints(stats.getCurrentMonthPoints() + points);
        stats.setRestitutionsCompleted(stats.getRestitutionsCompleted() + 1);

        List<String> awardedCodes = new ArrayList<>();
        if (stats.getRestitutionsCompleted() == 1) {
            awardBadge(finder, BadgeSeeder.FIRST_RESTITUTION, null).ifPresent(awardedCodes::add);
        }
        int count = stats.getRestitutionsCompleted();
        for (var tier : List.of(
                Map.entry(3, BadgeSeeder.CITIZEN_3),
                Map.entry(10, BadgeSeeder.CITIZEN_10),
                Map.entry(25, BadgeSeeder.CITIZEN_25),
                Map.entry(50, BadgeSeeder.CITIZEN_50),
                Map.entry(100, BadgeSeeder.CITIZEN_100))) {
            if (count == tier.getKey()) {
                awardBadge(finder, tier.getValue(), null).ifPresent(awardedCodes::add);
            }
        }
        if (fastDrop) {
            awardBadge(finder, BadgeSeeder.FAST_DROP, null).ifPresent(awardedCodes::add);
        }
        if (match.getRelayPoint() != null && match.getRelayPoint().getCity() != null) {
            awardBadge(finder, BadgeSeeder.ECLAIREUR, match.getRelayPoint().getCity().toLowerCase())
                    .ifPresent(awardedCodes::add);
        }
        if (decouverte.getDocumentType() == DocumentType.PASSEPORT) {
            awardBadge(finder, BadgeSeeder.SECOURISTE, null).ifPresent(awardedCodes::add);
        }

        if (!awardedCodes.isEmpty()) {
            notificationService.notify(finder.getId(), NotificationType.SYSTEM,
                    "Nouveaux badges débloqués",
                    "Bravo ! Vous avez débloqué : " + String.join(", ", awardedCodes),
                    Map.of("type", "BADGES_EARNED", "codes", String.join(",", awardedCodes)));
        }

        return new GamificationOutcome(points, awardedCodes);
    }

    @Transactional
    public void resetMonthlyPoints() {
        Instant now = Instant.now();
        int updated = 0;
        for (UserStats s : userStatsRepository.findAll()) {
            if (s.getCurrentMonthPoints() > 0) {
                s.setCurrentMonthPoints(0);
                s.setMonthResetAt(now);
                updated++;
            }
        }
        log.info("Reset mensuel leaderboard : {} utilisateurs", updated);
    }

    private Optional<String> awardBadge(User user, String code, String metadataKey) {
        Badge badge = badgeRepository.findByCode(code).orElse(null);
        if (badge == null) {
            log.warn("Badge {} absent du catalogue — seed incomplet ?", code);
            return Optional.empty();
        }
        if (userBadgeRepository.findByUserIdAndBadgeIdAndMetadataKey(
                user.getId(), badge.getId(), metadataKey).isPresent()) {
            return Optional.empty();
        }
        userBadgeRepository.save(UserBadge.builder()
                .user(user).badge(badge).metadataKey(metadataKey).build());
        return Optional.of(badge.getCode());
    }

    private boolean isFastDrop(Match match) {
        Instant dropped = match.getDroppedAt();
        var dateEvent = match.getDeclarationDecouverte().getDateEvent();
        if (dropped == null || dateEvent == null) return false;
        Instant discovery = dateEvent.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        return !dropped.isAfter(discovery.plus(FAST_DROP_WINDOW));
    }

    private boolean isFarRelay(Match match) {
        if (match.getRelayPoint() == null) return false;
        Double lat1 = match.getDeclarationDecouverte().getLatitude();
        Double lon1 = match.getDeclarationDecouverte().getLongitude();
        Double lat2 = match.getRelayPoint().getLatitude();
        Double lon2 = match.getRelayPoint().getLongitude();
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) return false;
        double km = haversine(lat1, lon1, lat2, lon2);
        return km > DISTANCE_THRESHOLD_KM;
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * R * Math.asin(Math.sqrt(a));
    }

    public record GamificationOutcome(int pointsEarned, List<String> badgesEarned) {}
}
