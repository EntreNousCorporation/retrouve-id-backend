package com.retrouvid.modules.declaration.dto;

import com.retrouvid.modules.declaration.entity.Declaration;
import com.retrouvid.modules.declaration.entity.DeclarationStatus;
import com.retrouvid.modules.declaration.entity.DeclarationType;
import com.retrouvid.modules.declaration.entity.DocumentType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DeclarationResponse(
        UUID id,
        UUID userId,
        DeclarationType type,
        DocumentType documentType,
        String documentNumberPartial,
        String ownerName,
        String description,
        String photoUrl,
        Double latitude,
        Double longitude,
        String locationDescription,
        String city,
        DeclarationStatus status,
        LocalDate dateEvent,
        Instant createdAt,
        Instant expiresAt
) {
    public static DeclarationResponse from(Declaration d) {
        return new DeclarationResponse(
                d.getId(),
                d.getUser().getId(),
                d.getType(),
                d.getDocumentType(),
                d.getDocumentNumberPartial(),
                d.getOwnerName(),
                d.getDescription(),
                d.getPhotoUrl(),
                d.getLatitude(),
                d.getLongitude(),
                d.getLocationDescription(),
                d.getCity(),
                d.getStatus(),
                d.getDateEvent(),
                d.getCreatedAt(),
                d.getExpiresAt()
        );
    }
}
