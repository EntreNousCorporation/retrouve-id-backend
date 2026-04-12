package com.retrouvid.modules.declaration.dto;

import com.retrouvid.modules.declaration.entity.DeclarationStatus;
import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(@NotNull DeclarationStatus status) {}
