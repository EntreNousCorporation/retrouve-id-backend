package com.retrouvid.modules.declaration.controller;

import com.retrouvid.modules.declaration.dto.DeclarationRequest;
import com.retrouvid.modules.declaration.dto.DeclarationResponse;
import com.retrouvid.modules.declaration.dto.StatusUpdateRequest;
import com.retrouvid.modules.declaration.entity.DeclarationStatus;
import com.retrouvid.modules.declaration.entity.DeclarationType;
import com.retrouvid.modules.declaration.entity.DocumentType;
import com.retrouvid.modules.declaration.service.DeclarationService;
import com.retrouvid.security.CurrentUser;
import com.retrouvid.shared.dto.ApiResponse;
import com.retrouvid.shared.dto.PagedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/declarations")
@RequiredArgsConstructor
public class DeclarationController {

    private final DeclarationService service;

    @PostMapping
    public ApiResponse<DeclarationResponse> create(@Valid @RequestBody DeclarationRequest req) {
        return ApiResponse.ok(DeclarationResponse.from(service.create(CurrentUser.id(), req)));
    }

    @GetMapping
    public ApiResponse<PagedResponse<DeclarationResponse>> list(
            @RequestParam(required = false) DeclarationType type,
            @RequestParam(required = false) DocumentType documentType,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String ownerName,
            @RequestParam(required = false, defaultValue = "ACTIVE") DeclarationStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<DeclarationResponse> page = service.search(type, documentType, city, ownerName, status, pageable)
                .map(DeclarationResponse::from);
        return ApiResponse.ok(PagedResponse.of(page));
    }

    @GetMapping("/my")
    public ApiResponse<PagedResponse<DeclarationResponse>> mine(@PageableDefault(size = 20) Pageable pageable) {
        Page<DeclarationResponse> page = service.findMine(CurrentUser.id(), pageable).map(DeclarationResponse::from);
        return ApiResponse.ok(PagedResponse.of(page));
    }

    @GetMapping("/nearby")
    public ApiResponse<PagedResponse<DeclarationResponse>> nearby(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "10") double radiusKm,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<DeclarationResponse> page = service.findNearby(lat, lon, radiusKm, pageable).map(DeclarationResponse::from);
        return ApiResponse.ok(PagedResponse.of(page));
    }

    @GetMapping("/{id}")
    public ApiResponse<DeclarationResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(DeclarationResponse.from(service.get(id)));
    }

    @PutMapping("/{id}")
    public ApiResponse<DeclarationResponse> update(@PathVariable UUID id, @Valid @RequestBody DeclarationRequest req) {
        return ApiResponse.ok(DeclarationResponse.from(service.update(id, CurrentUser.id(), req)));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<DeclarationResponse> updateStatus(@PathVariable UUID id, @Valid @RequestBody StatusUpdateRequest req) {
        return ApiResponse.ok(DeclarationResponse.from(service.changeStatus(id, CurrentUser.id(), req.status())));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id, CurrentUser.id());
        return ApiResponse.ok(null);
    }
}
