package com.retrouvid.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        String email,
        String phone,
        @NotBlank String password
) {}
