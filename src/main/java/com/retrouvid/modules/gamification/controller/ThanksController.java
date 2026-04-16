package com.retrouvid.modules.gamification.controller;

import com.retrouvid.modules.gamification.entity.ThanksMessage;
import com.retrouvid.modules.gamification.repository.ThanksMessageRepository;
import com.retrouvid.modules.matching.entity.Match;
import com.retrouvid.modules.matching.entity.MatchStatus;
import com.retrouvid.modules.matching.repository.MatchRepository;
import com.retrouvid.modules.notification.entity.NotificationType;
import com.retrouvid.modules.notification.service.NotificationService;
import com.retrouvid.security.CurrentUser;
import com.retrouvid.shared.dto.ApiResponse;
import com.retrouvid.shared.dto.PagedResponse;
import com.retrouvid.shared.exception.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/thanks")
@RequiredArgsConstructor
public class ThanksController {

    private final ThanksMessageRepository thanksRepository;
    private final MatchRepository matchRepository;
    private final NotificationService notificationService;

    public record SendRequest(@NotNull UUID matchId,
                              @NotBlank @Size(max = 140) String content) {}

    public record ThanksDto(UUID id, UUID matchId, UUID fromUserId,
                            UUID toUserId, String content, Instant createdAt) {
        static ThanksDto from(ThanksMessage t) {
            return new ThanksDto(t.getId(), t.getMatch().getId(),
                    t.getFromUser().getId(), t.getToUser().getId(),
                    t.getContent(), t.getCreatedAt());
        }
    }

    @PostMapping
    @Transactional
    public ApiResponse<ThanksDto> send(@Valid @RequestBody SendRequest req) {
        UUID uid = CurrentUser.id();
        Match match = matchRepository.findById(req.matchId())
                .orElseThrow(() -> ApiException.notFound("Match introuvable"));
        if (match.getStatus() != MatchStatus.PICKED_UP) {
            throw ApiException.conflict("Un message n'est possible qu'après restitution");
        }
        if (!match.getDeclarationPerte().getUser().getId().equals(uid)) {
            throw ApiException.forbidden("Seul le propriétaire peut remercier");
        }
        if (thanksRepository.findByMatchId(req.matchId()).isPresent()) {
            throw ApiException.conflict("Remerciement déjà envoyé");
        }

        var finder = match.getDeclarationDecouverte().getUser();
        ThanksMessage t = thanksRepository.save(ThanksMessage.builder()
                .match(match)
                .fromUser(match.getDeclarationPerte().getUser())
                .toUser(finder)
                .content(req.content().trim())
                .build());

        notificationService.notify(finder.getId(), NotificationType.SYSTEM,
                "Un propriétaire vous remercie",
                req.content().trim(),
                Map.of("type", "THANKS_RECEIVED", "matchId", match.getId().toString()));

        return ApiResponse.ok(ThanksDto.from(t));
    }

    @GetMapping("/received")
    @Transactional(readOnly = true)
    public ApiResponse<PagedResponse<ThanksDto>> received(
            @PageableDefault(size = 20) Pageable pageable) {
        var page = thanksRepository
                .findByToUserIdOrderByCreatedAtDesc(CurrentUser.id(), pageable)
                .map(ThanksDto::from);
        return ApiResponse.ok(PagedResponse.of(page));
    }
}
