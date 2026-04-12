package com.retrouvid.modules.admin.controller;

import com.retrouvid.modules.admin.entity.RelayPoint;
import com.retrouvid.modules.admin.repository.RelayPointRepository;
import com.retrouvid.modules.declaration.entity.Declaration;
import com.retrouvid.modules.declaration.entity.DeclarationStatus;
import com.retrouvid.modules.declaration.entity.DeclarationType;
import com.retrouvid.modules.declaration.repository.DeclarationRepository;
import com.retrouvid.modules.matching.entity.MatchStatus;
import com.retrouvid.modules.matching.repository.MatchRepository;
import com.retrouvid.modules.user.entity.Role;
import com.retrouvid.modules.user.entity.User;
import com.retrouvid.modules.user.repository.UserRepository;
import com.retrouvid.shared.dto.ApiResponse;
import com.retrouvid.shared.dto.PagedResponse;
import com.retrouvid.shared.exception.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final DeclarationRepository declarationRepository;
    private final MatchRepository matchRepository;
    private final RelayPointRepository relayPointRepository;

    // ---------- Dashboard ----------

    public record DashboardDto(
            long totalUsers,
            long totalDeclarations,
            long activeDeclarations,
            long matchedDeclarations,
            long restitutedDeclarations,
            long pendingMatches,
            long confirmedMatches,
            Map<String, Long> declarationsByType,
            Map<String, Long> declarationsByDocumentType) {}

    @GetMapping("/dashboard")
    public ApiResponse<DashboardDto> dashboard() {
        long totalUsers = userRepository.count();
        long totalDecl = declarationRepository.count();
        long active = declarationRepository.findAll().stream()
                .filter(d -> d.getStatus() == DeclarationStatus.ACTIVE).count();
        long matched = declarationRepository.findAll().stream()
                .filter(d -> d.getStatus() == DeclarationStatus.MATCHED).count();
        long restituted = declarationRepository.findAll().stream()
                .filter(d -> d.getStatus() == DeclarationStatus.RESTITUTED).count();
        long pending = matchRepository.findByStatus(MatchStatus.PENDING).size();
        long confirmed = matchRepository.findByStatus(MatchStatus.CONFIRMED).size();

        Map<String, Long> byType = Map.of(
                "PERTE", declarationRepository.findAll().stream()
                        .filter(d -> d.getType() == DeclarationType.PERTE).count(),
                "DECOUVERTE", declarationRepository.findAll().stream()
                        .filter(d -> d.getType() == DeclarationType.DECOUVERTE).count()
        );
        Map<String, Long> byDoc = declarationRepository.findAll().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        d -> d.getDocumentType().name(),
                        java.util.stream.Collectors.counting()));

        return ApiResponse.ok(new DashboardDto(
                totalUsers, totalDecl, active, matched, restituted,
                pending, confirmed, byType, byDoc));
    }

    // ---------- Declarations moderation ----------

    public record AdminDeclarationDto(UUID id, UUID userId, String type, String documentType,
                                      String ownerName, String city, String status,
                                      Instant createdAt, Instant expiresAt) {
        static AdminDeclarationDto from(Declaration d) {
            return new AdminDeclarationDto(d.getId(), d.getUser().getId(),
                    d.getType().name(), d.getDocumentType().name(),
                    d.getOwnerName(), d.getCity(), d.getStatus().name(),
                    d.getCreatedAt(), d.getExpiresAt());
        }
    }

    public record ModerateRequest(@jakarta.validation.constraints.NotNull DeclarationStatus status) {}

    @GetMapping("/declarations")
    public ApiResponse<PagedResponse<AdminDeclarationDto>> declarations(
            @PageableDefault(size = 20) Pageable pageable) {
        var page = declarationRepository.findAll(pageable).map(AdminDeclarationDto::from);
        return ApiResponse.ok(PagedResponse.of(page));
    }

    @PatchMapping("/declarations/{id}/moderate")
    @Transactional
    public ApiResponse<AdminDeclarationDto> moderate(@PathVariable UUID id,
                                                     @Valid @RequestBody ModerateRequest req) {
        Declaration d = declarationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Déclaration introuvable"));
        d.setStatus(req.status());
        return ApiResponse.ok(AdminDeclarationDto.from(d));
    }

    // ---------- Users ----------

    public record AdminUserDto(UUID id, String email, String phone, String firstName,
                               String lastName, String role, boolean verified, String city,
                               Instant createdAt) {
        static AdminUserDto from(User u) {
            return new AdminUserDto(u.getId(), u.getEmail(), u.getPhone(),
                    u.getFirstName(), u.getLastName(), u.getRole().name(),
                    u.isVerified(), u.getCity(), u.getCreatedAt());
        }
    }

    public record RoleUpdateRequest(@jakarta.validation.constraints.NotNull Role role) {}

    @GetMapping("/users")
    public ApiResponse<PagedResponse<AdminUserDto>> users(
            @PageableDefault(size = 20) Pageable pageable) {
        var page = userRepository.findAll(pageable).map(AdminUserDto::from);
        return ApiResponse.ok(PagedResponse.of(page));
    }

    @PatchMapping("/users/{id}/role")
    @Transactional
    public ApiResponse<AdminUserDto> setRole(@PathVariable UUID id, @Valid @RequestBody RoleUpdateRequest req) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Utilisateur introuvable"));
        u.setRole(req.role());
        return ApiResponse.ok(AdminUserDto.from(u));
    }

    // ---------- Relay points ----------

    public record RelayPointRequest(
            @NotBlank String name,
            @NotBlank String type,
            String address,
            Double latitude,
            Double longitude,
            String city,
            String phone,
            Boolean active) {}

    public record RelayPointDto(UUID id, String name, String type, String address,
                                Double latitude, Double longitude, String city, String phone,
                                boolean active, Instant createdAt) {
        static RelayPointDto from(RelayPoint r) {
            return new RelayPointDto(r.getId(), r.getName(), r.getType(), r.getAddress(),
                    r.getLatitude(), r.getLongitude(), r.getCity(), r.getPhone(),
                    r.isActive(), r.getCreatedAt());
        }
    }

    @GetMapping("/relay-points")
    public ApiResponse<List<RelayPointDto>> listRelayPoints() {
        return ApiResponse.ok(relayPointRepository.findAll().stream()
                .map(RelayPointDto::from).toList());
    }

    @PostMapping("/relay-points")
    public ApiResponse<RelayPointDto> createRelayPoint(@Valid @RequestBody RelayPointRequest req) {
        RelayPoint r = RelayPoint.builder()
                .name(req.name()).type(req.type()).address(req.address())
                .latitude(req.latitude()).longitude(req.longitude())
                .city(req.city()).phone(req.phone())
                .active(req.active() == null || req.active())
                .build();
        return ApiResponse.ok(RelayPointDto.from(relayPointRepository.save(r)));
    }

    @PutMapping("/relay-points/{id}")
    @Transactional
    public ApiResponse<RelayPointDto> updateRelayPoint(@PathVariable UUID id,
                                                       @Valid @RequestBody RelayPointRequest req) {
        RelayPoint r = relayPointRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Point relais introuvable"));
        r.setName(req.name()); r.setType(req.type()); r.setAddress(req.address());
        r.setLatitude(req.latitude()); r.setLongitude(req.longitude());
        r.setCity(req.city()); r.setPhone(req.phone());
        if (req.active() != null) r.setActive(req.active());
        return ApiResponse.ok(RelayPointDto.from(r));
    }

    @DeleteMapping("/relay-points/{id}")
    public ApiResponse<Void> deleteRelayPoint(@PathVariable UUID id) {
        relayPointRepository.deleteById(id);
        return ApiResponse.ok(null);
    }
}
