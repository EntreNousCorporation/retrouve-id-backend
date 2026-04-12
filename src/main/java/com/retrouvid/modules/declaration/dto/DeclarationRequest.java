package com.retrouvid.modules.declaration.dto;

import com.retrouvid.modules.declaration.entity.DeclarationType;
import com.retrouvid.modules.declaration.entity.DocumentType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record DeclarationRequest(
        @NotNull DeclarationType type,
        @NotNull DocumentType documentType,
        String documentNumberPartial,
        String ownerName,
        String description,
        String photoUrl,
        Double latitude,
        Double longitude,
        String locationDescription,
        String city,
        LocalDate dateEvent
) {}
