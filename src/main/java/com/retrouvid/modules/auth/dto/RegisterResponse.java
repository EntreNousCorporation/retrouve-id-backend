package com.retrouvid.modules.auth.dto;

import java.util.UUID;

/**
 * Retourné à l'inscription. Ne contient PAS de tokens : l'utilisateur doit
 * vérifier son email via /verify-otp pour obtenir un AuthResponse.
 */
public record RegisterResponse(
        UUID userId,
        String email,
        boolean needsVerification
) {}
