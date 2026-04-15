package com.retrouvid.modules.admin.controller;

import com.retrouvid.modules.admin.entity.RelayPoint;
import com.retrouvid.modules.admin.repository.RelayPointRepository;
import com.retrouvid.modules.declaration.entity.Declaration;
import com.retrouvid.modules.declaration.entity.DeclarationStatus;
import com.retrouvid.modules.declaration.entity.DeclarationType;
import com.retrouvid.modules.declaration.entity.DocumentType;
import com.retrouvid.modules.declaration.repository.DeclarationRepository;
import org.springframework.data.jpa.domain.Specification;
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
        long active = declarationRepository.countByStatus(DeclarationStatus.ACTIVE);
        long matched = declarationRepository.countByStatus(DeclarationStatus.MATCHED);
        long restituted = declarationRepository.countByStatus(DeclarationStatus.RESTITUTED);
        long pending = matchRepository.countByStatus(MatchStatus.PENDING);
        long confirmed = matchRepository.countByStatus(MatchStatus.CONFIRMED);

        Map<String, Long> byType = Map.of(
                "PERTE", declarationRepository.countByType(DeclarationType.PERTE),
                "DECOUVERTE", declarationRepository.countByType(DeclarationType.DECOUVERTE));

        Map<String, Long> byDoc = declarationRepository.countGroupByDocumentType().stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> ((DocumentType) row[0]).name(),
                        row -> (Long) row[1]));

        return ApiResponse.ok(new DashboardDto(
                totalUsers, totalDecl, active, matched, restituted,
                pending, confirmed, byType, byDoc));
    }

    public record TimeseriesPoint(String date, long perte, long decouverte) {}

    /// Évolution quotidienne des déclarations (PERTE / DECOUVERTE) sur la
    /// fenêtre `days` (1 à 365). Les jours sans déclaration sont remplis
    /// à 0 pour que la courbe côté UI ne fasse pas de trous.
    @GetMapping("/dashboard/timeseries")
    public ApiResponse<java.util.List<TimeseriesPoint>> timeseries(
            @RequestParam(defaultValue = "30") int days) {
        int window = Math.max(1, Math.min(days, 365));
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        java.time.LocalDate start = today.minusDays(window - 1L);
        java.time.Instant since = start.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();

        java.util.Map<java.time.LocalDate, long[]> bucket = new java.util.LinkedHashMap<>();
        for (int i = 0; i < window; i++) {
            bucket.put(start.plusDays(i), new long[2]);
        }
        for (Object[] row : declarationRepository.countByDayAndType(since)) {
            java.time.Instant ts = ((java.sql.Timestamp) row[0]).toInstant();
            java.time.LocalDate day = ts.atZone(java.time.ZoneOffset.UTC).toLocalDate();
            DeclarationType type = (DeclarationType) row[1];
            long total = ((Number) row[2]).longValue();
            long[] slot = bucket.get(day);
            if (slot == null) continue;
            if (type == DeclarationType.PERTE) slot[0] = total;
            else if (type == DeclarationType.DECOUVERTE) slot[1] = total;
        }
        java.util.List<TimeseriesPoint> out = bucket.entrySet().stream()
                .map(e -> new TimeseriesPoint(e.getKey().toString(),
                        e.getValue()[0], e.getValue()[1]))
                .toList();
        return ApiResponse.ok(out);
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
            @RequestParam(required = false) DeclarationStatus status,
            @RequestParam(required = false) DeclarationType type,
            @RequestParam(required = false) DocumentType documentType,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        Specification<Declaration> spec = (root, q, cb) -> cb.conjunction();
        if (status != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        if (type != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("type"), type));
        if (documentType != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("documentType"), documentType));
        if (search != null && !search.isBlank()) {
            String like = "%" + search.toLowerCase() + "%";
            Specification<Declaration> s = spec;
            spec = s.and((r, q, cb) -> cb.or(
                    cb.like(cb.lower(r.get("ownerName")), like),
                    cb.like(cb.lower(r.get("city")), like)));
        }
        var page = declarationRepository.findAll(spec, pageable).map(AdminDeclarationDto::from);
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
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean verified,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        Specification<User> spec = (r, q, cb) -> cb.conjunction();
        if (role != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("role"), role));
        if (verified != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("verified"), verified));
        if (search != null && !search.isBlank()) {
            String like = "%" + search.toLowerCase() + "%";
            Specification<User> s = spec;
            spec = s.and((r, q, cb) -> cb.or(
                    cb.like(cb.lower(cb.coalesce(r.get("email"), "")), like),
                    cb.like(cb.lower(cb.coalesce(r.get("phone"), "")), like),
                    cb.like(cb.lower(cb.coalesce(r.get("firstName"), "")), like),
                    cb.like(cb.lower(cb.coalesce(r.get("lastName"), "")), like)));
        }
        var page = userRepository.findAll(spec, pageable).map(AdminUserDto::from);
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
