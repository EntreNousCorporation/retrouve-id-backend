package com.retrouvid.modules.relay.controller;

import com.retrouvid.modules.relay.entity.RelayApplication;
import com.retrouvid.modules.relay.service.RelayApplicationService;
import com.retrouvid.security.CurrentUser;
import com.retrouvid.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/relay-applications")
@RequiredArgsConstructor
public class RelayApplicationController {

    private final RelayApplicationService service;

    public record RelayApplicationRequest(
            @NotBlank @Size(max = 200) String businessName,
            @NotBlank @Size(max = 50) String businessType,
            @NotBlank String address,
            @NotBlank @Size(max = 100) String city,
            @NotNull Double latitude,
            @NotNull Double longitude,
            @Size(max = 20) String phone,
            @Size(max = 200) String openingHours,
            String storefrontPhotoUrl,
            String justificationDocumentUrl
    ) {}

    public record RelayApplicationDto(UUID id, String businessName, String businessType,
                                      String address, String city, Double latitude, Double longitude,
                                      String phone, String openingHours, String storefrontPhotoUrl,
                                      String justificationDocumentUrl, String status,
                                      String rejectionReason, UUID relayPointId,
                                      Instant reviewedAt, Instant createdAt) {
        public static RelayApplicationDto from(RelayApplication a) {
            return new RelayApplicationDto(a.getId(), a.getBusinessName(), a.getBusinessType(),
                    a.getAddress(), a.getCity(), a.getLatitude(), a.getLongitude(),
                    a.getPhone(), a.getOpeningHours(), a.getStorefrontPhotoUrl(),
                    a.getJustificationDocumentUrl(), a.getStatus().name(),
                    a.getRejectionReason(),
                    a.getRelayPoint() == null ? null : a.getRelayPoint().getId(),
                    a.getReviewedAt(), a.getCreatedAt());
        }
    }

    @PostMapping
    public ApiResponse<RelayApplicationDto> submit(@Valid @RequestBody RelayApplicationRequest req) {
        RelayApplication draft = RelayApplication.builder()
                .businessName(req.businessName())
                .businessType(req.businessType())
                .address(req.address())
                .city(req.city())
                .latitude(req.latitude())
                .longitude(req.longitude())
                .phone(req.phone())
                .openingHours(req.openingHours())
                .storefrontPhotoUrl(req.storefrontPhotoUrl())
                .justificationDocumentUrl(req.justificationDocumentUrl())
                .build();
        return ApiResponse.ok(RelayApplicationDto.from(service.submit(CurrentUser.id(), draft)));
    }

    @GetMapping("/mine")
    public ApiResponse<List<RelayApplicationDto>> mine() {
        return ApiResponse.ok(service.findMine(CurrentUser.id())
                .stream().map(RelayApplicationDto::from).toList());
    }
}
