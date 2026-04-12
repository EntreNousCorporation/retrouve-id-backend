package com.retrouvid.modules.matching.controller;

import com.retrouvid.modules.matching.entity.Match;
import com.retrouvid.modules.matching.service.MatchingService;
import com.retrouvid.security.CurrentUser;
import com.retrouvid.shared.dto.ApiResponse;
import com.retrouvid.shared.dto.PagedResponse;
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
                           double score, String status, Instant createdAt) {
        static MatchDto from(Match m) {
            return new MatchDto(m.getId(),
                    m.getDeclarationPerte().getId(),
                    m.getDeclarationDecouverte().getId(),
                    m.getScore(),
                    m.getStatus().name(),
                    m.getCreatedAt());
        }
    }

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
}
