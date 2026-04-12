package com.retrouvid.security;

import com.retrouvid.shared.exception.ApiException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class CurrentUser {
    private CurrentUser() {}

    public static UUID id() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw ApiException.unauthorized("Non authentifié");
        }
        return UUID.fromString(auth.getPrincipal().toString());
    }
}
