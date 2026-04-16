package com.retrouvid.modules.matching.controller;

import com.retrouvid.modules.matching.entity.Match;
import com.retrouvid.modules.matching.service.MatchingService;
import com.retrouvid.security.CurrentUser;
import com.retrouvid.shared.dto.ApiResponse;
import com.retrouvid.shared.dto.PagedResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchingService matching;

    public record MatchDto(UUID id, UUID declarationPerteId, UUID declarationDecouverteId,
                           double score, Double verificationScore, String status,
                           UUID relayPointId, Instant handoverDeadline, Instant codeExpiresAt,
                           Instant droppedAt, Instant pickedUpAt, Instant createdAt) {
        static MatchDto from(Match m) {
            return new MatchDto(m.getId(),
                    m.getDeclarationPerte().getId(),
                    m.getDeclarationDecouverte().getId(),
                    m.getScore(),
                    m.getVerificationScore(),
                    m.getStatus().name(),
                    m.getRelayPoint() == null ? null : m.getRelayPoint().getId(),
                    m.getHandoverDeadline(),
                    m.getCodeExpiresAt(),
                    m.getDroppedAt(),
                    m.getPickedUpAt(),
                    m.getCreatedAt());
        }
    }

    public record ChooseRelayRequest(@NotNull UUID relayPointId) {}
    public record PickupRequest(@NotBlank String code) {}

    @GetMapping
    public ApiResponse<PagedResponse<MatchDto>> mine(@PageableDefault(size = 20) Pageable pageable) {
        var page = matching.findForUser(CurrentUser.id(), pageable).map(MatchDto::from);
        return ApiResponse.ok(PagedResponse.of(page));
    }

    @GetMapping("/{id}")
    public ApiResponse<MatchDto> get(@PathVariable UUID id) {
        return ApiResponse.ok(MatchDto.from(matching.get(id, CurrentUser.id())));
    }

    @PatchMapping("/{id}/confirm")
    public ApiResponse<MatchDto> confirm(@PathVariable UUID id) {
        return ApiResponse.ok(MatchDto.from(matching.confirm(id, CurrentUser.id())));
    }

    @PatchMapping("/{id}/reject")
    public ApiResponse<MatchDto> reject(@PathVariable UUID id) {
        return ApiResponse.ok(MatchDto.from(matching.reject(id, CurrentUser.id())));
    }

    @PostMapping("/{id}/choose-relay")
    public ApiResponse<MatchDto> chooseRelay(@PathVariable UUID id,
                                             @Valid @RequestBody ChooseRelayRequest req) {
        return ApiResponse.ok(MatchDto.from(matching.chooseRelay(id, CurrentUser.id(), req.relayPointId())));
    }

    @PostMapping("/{id}/drop")
    public ApiResponse<MatchDto> drop(@PathVariable UUID id) {
        return ApiResponse.ok(MatchDto.from(matching.dropAtRelay(id, CurrentUser.id())));
    }

    @PostMapping("/{id}/pickup")
    public ApiResponse<MatchDto> pickup(@PathVariable UUID id,
                                        @Valid @RequestBody PickupRequest req) {
        return ApiResponse.ok(MatchDto.from(matching.pickup(id, CurrentUser.id(), req.code())));
    }
}
