package com.retrouvid.modules.user.service;

import com.retrouvid.modules.declaration.repository.DeclarationRepository;
import com.retrouvid.modules.media.repository.MediaAssetRepository;
import com.retrouvid.modules.messaging.repository.MessageRepository;
import com.retrouvid.modules.notification.repository.NotificationRepository;
import com.retrouvid.modules.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Export RGPD (art. 15 + 20) : assemble dans une structure JSON l'ensemble
 * des données personnelles rattachées à un utilisateur, sous un format
 * lisible et portable.
 */
@Service
@RequiredArgsConstructor
public class UserDataExportService {

    private final DeclarationRepository declarationRepository;
    private final MessageRepository messageRepository;
    private final NotificationRepository notificationRepository;
    private final MediaAssetRepository mediaAssetRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> export(User user) {
        UUID uid = user.getId();

        Map<String, Object> profile = Map.of(
                "id", user.getId().toString(),
                "email", nullSafe(user.getEmail()),
                "phone", nullSafe(user.getPhone()),
                "firstName", nullSafe(user.getFirstName()),
                "lastName", nullSafe(user.getLastName()),
                "city", nullSafe(user.getCity()),
                "profilePhotoUrl", nullSafe(user.getProfilePhotoUrl()),
                "role", user.getRole().name(),
                "verified", user.isVerified()
        );

        List<Map<String, Object>> declarations = declarationRepository.findAll().stream()
                .filter(d -> d.getUser() != null && uid.equals(d.getUser().getId()))
                .map(d -> Map.<String, Object>of(
                        "id", d.getId().toString(),
                        "type", d.getType().name(),
                        "documentType", d.getDocumentType().name(),
                        "ownerName", nullSafe(d.getOwnerName()),
                        "description", nullSafe(d.getDescription()),
                        "city", nullSafe(d.getCity()),
                        "locationDescription", nullSafe(d.getLocationDescription()),
                        "status", d.getStatus().name(),
                        "createdAt", d.getCreatedAt() == null ? "" : d.getCreatedAt().toString()
                ))
                .collect(Collectors.toList());

        List<Map<String, Object>> messages = messageRepository.findAll().stream()
                .filter(m -> m.getSender() != null && uid.equals(m.getSender().getId()))
                .map(m -> Map.<String, Object>of(
                        "id", m.getId().toString(),
                        "conversationId",
                                m.getConversation() == null ? "" : m.getConversation().getId().toString(),
                        "content", nullSafe(m.getContent()),
                        "createdAt", m.getCreatedAt() == null ? "" : m.getCreatedAt().toString()
                ))
                .collect(Collectors.toList());

        List<Map<String, Object>> notifications = notificationRepository.findAll().stream()
                .filter(n -> n.getUser() != null && uid.equals(n.getUser().getId()))
                .map(n -> Map.<String, Object>of(
                        "id", n.getId().toString(),
                        "type", n.getType() == null ? "" : n.getType().name(),
                        "title", nullSafe(n.getTitle()),
                        "body", nullSafe(n.getBody()),
                        "read", n.isRead(),
                        "createdAt", n.getCreatedAt() == null ? "" : n.getCreatedAt().toString()
                ))
                .collect(Collectors.toList());

        List<Map<String, Object>> media = mediaAssetRepository.findByOwnerId(uid).stream()
                .map(a -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", a.getId().toString());
                    m.put("contentType", nullSafe(a.getContentType()));
                    m.put("sizeBytes", a.getSizeBytes());
                    m.put("createdAt", a.getCreatedAt() == null ? "" : a.getCreatedAt().toString());
                    return m;
                })
                .collect(Collectors.toList());

        return Map.of(
                "exportedAt", Instant.now().toString(),
                "profile", profile,
                "declarations", declarations,
                "messages", messages,
                "notifications", notifications,
                "media", media
        );
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
