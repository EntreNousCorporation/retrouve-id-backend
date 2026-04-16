package com.retrouvid.modules.relay.service;

import com.retrouvid.modules.admin.entity.RelayPoint;
import com.retrouvid.modules.admin.repository.RelayPointRepository;
import com.retrouvid.modules.notification.entity.NotificationType;
import com.retrouvid.modules.notification.service.NotificationService;
import com.retrouvid.modules.relay.entity.RelayApplication;
import com.retrouvid.modules.relay.entity.RelayApplicationStatus;
import com.retrouvid.modules.relay.repository.RelayApplicationRepository;
import com.retrouvid.modules.user.entity.Role;
import com.retrouvid.modules.user.entity.User;
import com.retrouvid.modules.user.repository.UserRepository;
import com.retrouvid.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RelayApplicationService {

    private final RelayApplicationRepository applicationRepository;
    private final RelayPointRepository relayPointRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public RelayApplication submit(UUID userId, RelayApplication draft) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Utilisateur introuvable"));
        applicationRepository.findByUserIdAndStatus(userId, RelayApplicationStatus.PENDING)
                .ifPresent(a -> { throw ApiException.conflict("Vous avez déjà une candidature en attente"); });
        draft.setUser(user);
        draft.setStatus(RelayApplicationStatus.PENDING);
        return applicationRepository.save(draft);
    }

    @Transactional(readOnly = true)
    public List<RelayApplication> findMine(UUID userId) {
        return applicationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public Page<RelayApplication> findAll(RelayApplicationStatus status, Pageable pageable) {
        return status == null
                ? applicationRepository.findAll(pageable)
                : applicationRepository.findByStatus(status, pageable);
    }

    @Transactional
    public RelayApplication approve(UUID applicationId, UUID adminId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> ApiException.unauthorized("Admin introuvable"));
        RelayApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> ApiException.notFound("Candidature introuvable"));
        if (app.getStatus() != RelayApplicationStatus.PENDING) {
            throw ApiException.conflict("Candidature déjà traitée");
        }

        RelayPoint relay = RelayPoint.builder()
                .name(app.getBusinessName())
                .type(app.getBusinessType())
                .address(app.getAddress())
                .city(app.getCity())
                .latitude(app.getLatitude())
                .longitude(app.getLongitude())
                .phone(app.getPhone())
                .user(app.getUser())
                .active(true)
                .build();
        relay = relayPointRepository.save(relay);

        app.setStatus(RelayApplicationStatus.APPROVED);
        app.setRelayPoint(relay);
        app.setReviewedBy(admin);
        app.setReviewedAt(Instant.now());

        User candidate = app.getUser();
        if (candidate.getRole() != Role.ADMIN) {
            candidate.setRole(Role.AGENT);
        }

        notificationService.notify(candidate.getId(), NotificationType.RELAY_APPLICATION_UPDATE,
                "Candidature approuvée",
                "Félicitations, votre point relais « " + relay.getName() + " » est actif.",
                Map.of("applicationId", app.getId().toString(), "status", "APPROVED"));
        return app;
    }

    @Transactional
    public RelayApplication reject(UUID applicationId, UUID adminId, String reason) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> ApiException.unauthorized("Admin introuvable"));
        RelayApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> ApiException.notFound("Candidature introuvable"));
        if (app.getStatus() != RelayApplicationStatus.PENDING) {
            throw ApiException.conflict("Candidature déjà traitée");
        }
        app.setStatus(RelayApplicationStatus.REJECTED);
        app.setRejectionReason(reason);
        app.setReviewedBy(admin);
        app.setReviewedAt(Instant.now());

        notificationService.notify(app.getUser().getId(), NotificationType.RELAY_APPLICATION_UPDATE,
                "Candidature refusée",
                reason == null || reason.isBlank() ? "Votre candidature a été refusée." : reason,
                Map.of("applicationId", app.getId().toString(), "status", "REJECTED"));
        return app;
    }
}
