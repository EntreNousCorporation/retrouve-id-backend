package com.retrouvid.modules.auth.service;

import com.retrouvid.modules.auth.dto.AuthResponse;
import com.retrouvid.modules.auth.dto.LoginRequest;
import com.retrouvid.modules.auth.dto.RegisterRequest;
import com.retrouvid.modules.auth.dto.RegisterResponse;
import com.retrouvid.modules.user.entity.Role;
import com.retrouvid.modules.user.entity.User;
import com.retrouvid.modules.user.repository.UserRepository;
import com.retrouvid.security.JwtTokenProvider;
import com.retrouvid.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final OtpService otpService;

    @Transactional
    public RegisterResponse register(RegisterRequest req) {
        if ((req.email() == null || req.email().isBlank()) && (req.phone() == null || req.phone().isBlank())) {
            throw ApiException.badRequest("Email ou téléphone requis");
        }
        if (req.email() != null && userRepository.existsByEmail(req.email())) {
            throw ApiException.conflict("Email déjà utilisé");
        }
        if (req.phone() != null && !req.phone().isBlank() && userRepository.existsByPhone(req.phone())) {
            throw ApiException.conflict("Téléphone déjà utilisé");
        }
        User user = User.builder()
                .email(req.email())
                .phone(req.phone())
                .passwordHash(passwordEncoder.encode(req.password()))
                .firstName(req.firstName())
                .lastName(req.lastName())
                .role(Role.USER)
                .verified(false)
                .build();
        user = userRepository.save(user);
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            otpService.sendEmailVerification(user);
        }
        // Pas de tokens : l'utilisateur doit d'abord vérifier son email via
        // /verify-otp. Sans cette étape, impossible d'obtenir un accessToken.
        return new RegisterResponse(user.getId(), user.getEmail(), true);
    }

    /**
     * Appelé par AuthController.verifyOtp : OtpService a déjà validé le code
     * et marqué le user verified=true. On génère les tokens uniquement ici.
     */
    @Transactional(readOnly = true)
    public AuthResponse issueTokensForVerifiedUser(User user) {
        return buildResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user;
        if (req.email() != null && !req.email().isBlank()) {
            user = userRepository.findByEmail(req.email())
                    .orElseThrow(() -> ApiException.unauthorized("Identifiants invalides"));
        } else if (req.phone() != null && !req.phone().isBlank()) {
            user = userRepository.findByPhone(req.phone())
                    .orElseThrow(() -> ApiException.unauthorized("Identifiants invalides"));
        } else {
            throw ApiException.badRequest("Email ou téléphone requis");
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Identifiants invalides");
        }
        return buildResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        try {
            if (!tokenProvider.isRefresh(refreshToken)) {
                throw ApiException.unauthorized("Token invalide");
            }
            UUID userId = tokenProvider.getUserId(refreshToken);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> ApiException.unauthorized("Utilisateur introuvable"));
            return buildResponse(user);
        } catch (io.jsonwebtoken.JwtException e) {
            throw ApiException.unauthorized("Token invalide");
        }
    }

    private AuthResponse buildResponse(User user) {
        String access = tokenProvider.generateAccessToken(user.getId(), user.getRole().name(), user.getFirstName());
        String refresh = tokenProvider.generateRefreshToken(user.getId());
        return new AuthResponse(user.getId(), user.getEmail(), user.getRole().name(), access, refresh);
    }
}
