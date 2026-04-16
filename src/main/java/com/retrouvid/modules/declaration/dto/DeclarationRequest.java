package com.retrouvid.modules.declaration.dto;

import com.retrouvid.modules.declaration.entity.DeclarationType;
import com.retrouvid.modules.declaration.entity.DocumentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record DeclarationRequest(
        @NotNull DeclarationType type,
        @NotNull DocumentType documentType,
        String documentNumberPartial,
        String documentNumberFull,
        LocalDate dateOfBirth,
        @Size(max = 160) String discriminantHint,
        String ownerName,
        String description,
        String photoUrl,
        Double latitude,
        Double longitude,
        String locationDescription,
        String city,
        LocalDate dateEvent
) {}
