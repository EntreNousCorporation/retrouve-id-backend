package com.retrouvid.modules.auth.controller;

import com.retrouvid.modules.auth.dto.AuthResponse;
import com.retrouvid.modules.auth.dto.LoginRequest;
import com.retrouvid.modules.auth.dto.RefreshRequest;
import com.retrouvid.modules.auth.dto.RegisterRequest;
import com.retrouvid.modules.auth.service.AuthService;
import com.retrouvid.modules.auth.service.PasswordResetService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;

import java.util.Map;
import com.retrouvid.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    @Value("${app.password-reset.expose-token:true}")
    private boolean exposeToken;

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ApiResponse.ok(authService.register(req));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.ok(authService.login(req));
    }

    @PostMapping("/refresh-token")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return ApiResponse.ok(authService.refresh(req.refreshToken()));
    }

    public record ForgotPasswordRequest(@Email @NotBlank String email) {}
    public record ResetPasswordRequest(@NotBlank String token, @Size(min = 8, max = 128) String password) {}

    @PostMapping("/forgot-password")
    public ApiResponse<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        String token = passwordResetService.requestReset(req.email());
        if (exposeToken && token != null) {
            return ApiResponse.ok(Map.of("token", token));
        }
        return ApiResponse.ok(Map.of());
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        passwordResetService.reset(req.token(), req.password());
        return ApiResponse.ok(null);
    }
}
