package com.retrouvid.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email String email,
        String phone,
        @Size(min = 8, max = 128) String password,
        String firstName,
        String lastName
) {}
