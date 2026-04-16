package com.retrouvid.modules.gamification.controller;

import com.retrouvid.modules.gamification.entity.UserBadge;
import com.retrouvid.modules.gamification.entity.UserStats;
import com.retrouvid.modules.gamification.repository.UserBadgeRepository;
import com.retrouvid.modules.gamification.repository.UserStatsRepository;
import com.retrouvid.security.CurrentUser;
import com.retrouvid.shared.dto.ApiResponse;
import com.retrouvid.shared.dto.PagedResponse;
import com.retrouvid.shared.exception.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class GamificationController {

    private final UserStatsRepository userStatsRepository;
    private final UserBadgeRepository userBadgeRepository;

    public record StatsDto(int totalPoints, int currentMonthPoints,
                           int restitutionsCompleted, boolean publicProfile,
                           String publicAlias, List<BadgeDto> badges,
                           Instant updatedAt) {}

    public record BadgeDto(String code, String name, String description,
                           String icon, String metadataKey, Instant earnedAt) {
        static BadgeDto from(UserBadge ub) {
            return new BadgeDto(ub.getBadge().getCode(), ub.getBadge().getName(),
                    ub.getBadge().getDescription(), ub.getBadge().getIcon(),
                    ub.getMetadataKey(), ub.getEarnedAt());
        }
    }

    public record LeaderboardEntryDto(int rank, String alias, int points,
                                      int restitutions, int badgeCount) {}

    public record PublicProfileDto(UUID userId, String alias, int totalPoints,
                                   int restitutionsCompleted,
                                   List<BadgeDto> badges) {}

    public record UpdateProfileRequest(
            boolean publicProfile,
            @Size(max = 80) String publicAlias) {}

    @GetMapping("/api/v1/gamification/me")
    @Transactional(readOnly = true)
    public ApiResponse<StatsDto> myStats() {
        UUID uid = CurrentUser.id();
        UserStats stats = userStatsRepository.findByUserId(uid).orElse(null);
        List<BadgeDto> badges = userBadgeRepository.findByUserId(uid)
                .stream().map(BadgeDto::from).toList();
        if (stats == null) {
            return ApiResponse.ok(new StatsDto(0, 0, 0, false, null, badges, Instant.now()));
        }
        return ApiResponse.ok(new StatsDto(stats.getTotalPoints(),
                stats.getCurrentMonthPoints(),
                stats.getRestitutionsCompleted(),
                stats.isPublicProfile(),
                stats.getPublicAlias(),
                badges,
                stats.getUpdatedAt()));
    }

    @PatchMapping("/api/v1/gamification/me")
    @Transactional
    public ApiResponse<StatsDto> updateProfile(@Valid @RequestBody UpdateProfileRequest req) {
        UUID uid = CurrentUser.id();
        UserStats stats = userStatsRepository.findByUserId(uid)
                .orElseGet(() -> userStatsRepository.save(
                        UserStats.builder().userId(uid).build()));
        stats.setPublicProfile(req.publicProfile());
        stats.setPublicAlias(req.publicAlias() == null || req.publicAlias().isBlank()
                ? null : req.publicAlias().trim());
        return myStats();
    }

    @GetMapping("/api/v1/gamification/leaderboard")
    @Transactional(readOnly = true)
    public ApiResponse<PagedResponse<LeaderboardEntryDto>> leaderboard(
            @PageableDefault(size = 10) Pageable pageable) {
        Pageable capped = PageRequest.of(pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), 50));
        Page<UserStats> page = userStatsRepository
                .findByCurrentMonthPointsGreaterThanOrderByCurrentMonthPointsDesc(0, capped);
        int startRank = page.getNumber() * page.getSize();
        int[] counter = {startRank};
        Page<LeaderboardEntryDto> mapped = page.map(s -> {
            counter[0]++;
            int badgeCount = userBadgeRepository.findByUserId(s.getUserId()).size();
            String alias = (s.isPublicProfile() && s.getPublicAlias() != null)
                    ? s.getPublicAlias()
                    : "Utilisateur anonyme #" + counter[0];
            return new LeaderboardEntryDto(counter[0], alias,
                    s.getCurrentMonthPoints(), s.getRestitutionsCompleted(), badgeCount);
        });
        return ApiResponse.ok(PagedResponse.of(mapped));
    }

    @GetMapping("/api/v1/gamification/users/{id}")
    @Transactional(readOnly = true)
    public ApiResponse<PublicProfileDto> publicProfile(@PathVariable UUID id) {
        UserStats stats = userStatsRepository.findByUserId(id)
                .orElseThrow(() -> ApiException.notFound("Profil introuvable"));
        if (!stats.isPublicProfile()) {
            throw ApiException.forbidden("Ce profil est privé");
        }
        List<BadgeDto> badges = userBadgeRepository.findByUserId(id)
                .stream().map(BadgeDto::from).toList();
        String alias = stats.getPublicAlias() != null ? stats.getPublicAlias() : "Citoyen RetrouvID";
        return ApiResponse.ok(new PublicProfileDto(id, alias,
                stats.getTotalPoints(), stats.getRestitutionsCompleted(), badges));
    }
}
