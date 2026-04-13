package com.retrouvid.modules.auth.controller;

import com.retrouvid.modules.auth.dto.AuthResponse;
import com.retrouvid.modules.auth.dto.LoginRequest;
import com.retrouvid.modules.auth.dto.RefreshRequest;
import com.retrouvid.modules.auth.dto.RegisterRequest;
import com.retrouvid.modules.auth.dto.RegisterResponse;
import com.retrouvid.modules.user.entity.User;
import com.retrouvid.modules.auth.service.AuthService;
import com.retrouvid.modules.auth.service.OtpService;
import com.retrouvid.modules.auth.service.PasswordResetService;
import com.retrouvid.modules.user.repository.UserRepository;
import com.retrouvid.shared.exception.ApiException;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
    private final OtpService otpService;
    private final UserRepository userRepository;

    @Value("${app.password-reset.expose-token:true}")
    private boolean exposeToken;

    @PostMapping("/register")
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest req) {
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

    public record SendOtpRequest(@Email @NotBlank String email) {}
    public record VerifyOtpRequest(
            @Email @NotBlank String email,
            @NotBlank @Pattern(regexp = "\\d{6}", message = "Le code doit contenir 6 chiffres") String code) {}

    @PostMapping("/send-otp")
    public ApiResponse<Map<String, String>> sendOtp(@Valid @RequestBody SendOtpRequest req) {
        var user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> ApiException.notFound("Utilisateur introuvable"));
        String code = otpService.sendEmailVerification(user);
        return ApiResponse.ok(code != null ? Map.of("code", code) : Map.of());
    }

    @PostMapping("/verify-otp")
    public ApiResponse<AuthResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest req) {
        User user = otpService.verifyEmail(req.email(), req.code());
        return ApiResponse.ok(authService.issueTokensForVerifiedUser(user));
    }
}
