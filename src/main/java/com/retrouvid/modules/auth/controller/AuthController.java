package com.retrouvid.modules.auth.controller;

import com.retrouvid.modules.auth.dto.AuthResponse;
import com.retrouvid.modules.auth.dto.LoginRequest;
import com.retrouvid.modules.auth.dto.RefreshRequest;
import com.retrouvid.modules.auth.dto.RegisterRequest;
import com.retrouvid.modules.auth.service.AuthService;
import com.retrouvid.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

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
}
