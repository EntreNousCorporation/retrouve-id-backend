package com.retrouvid.modules.relay.controller;

import com.retrouvid.modules.relay.controller.RelayApplicationController.RelayApplicationDto;
import com.retrouvid.modules.relay.entity.RelayApplicationStatus;
import com.retrouvid.modules.relay.service.RelayApplicationService;
import com.retrouvid.security.CurrentUser;
import com.retrouvid.shared.dto.ApiResponse;
import com.retrouvid.shared.dto.PagedResponse;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/relay-applications")
@RequiredArgsConstructor
public class AdminRelayApplicationController {

    private final RelayApplicationService service;

    public record RejectRequest(@Size(max = 500) String reason) {}

    @GetMapping
    public ApiResponse<PagedResponse<RelayApplicationDto>> list(
            @RequestParam(required = false) RelayApplicationStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        var page = service.findAll(status, pageable).map(RelayApplicationDto::from);
        return ApiResponse.ok(PagedResponse.of(page));
    }

    @PatchMapping("/{id}/approve")
    public ApiResponse<RelayApplicationDto> approve(@PathVariable UUID id) {
        return ApiResponse.ok(RelayApplicationDto.from(service.approve(id, CurrentUser.id())));
    }

    @PatchMapping("/{id}/reject")
    public ApiResponse<RelayApplicationDto> reject(@PathVariable UUID id,
                                                   @RequestBody(required = false) RejectRequest req) {
        String reason = req == null ? null : req.reason();
        return ApiResponse.ok(RelayApplicationDto.from(service.reject(id, CurrentUser.id(), reason)));
    }
}
