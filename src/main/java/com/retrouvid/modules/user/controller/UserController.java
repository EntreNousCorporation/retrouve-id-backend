package com.retrouvid.modules.user.controller;

import com.retrouvid.modules.user.entity.User;
import com.retrouvid.modules.user.repository.UserRepository;
import com.retrouvid.security.CurrentUser;
import com.retrouvid.shared.dto.ApiResponse;
import com.retrouvid.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    public record UserDto(UUID id, String email, String phone, String firstName, String lastName,
                         String profilePhotoUrl, String role, String city, Double latitude, Double longitude) {
        static UserDto from(User u) {
            return new UserDto(u.getId(), u.getEmail(), u.getPhone(), u.getFirstName(), u.getLastName(),
                    u.getProfilePhotoUrl(), u.getRole().name(), u.getCity(), u.getLatitude(), u.getLongitude());
        }
    }

    public record UpdateProfileRequest(String firstName, String lastName, String profilePhotoUrl,
                                       String city, Double latitude, Double longitude) {}

    @GetMapping("/me")
    public ApiResponse<UserDto> me() {
        User user = userRepository.findById(CurrentUser.id())
                .orElseThrow(() -> ApiException.unauthorized("Utilisateur introuvable"));
        return ApiResponse.ok(UserDto.from(user));
    }

    @PutMapping("/me")
    @Transactional
    public ApiResponse<UserDto> update(@RequestBody UpdateProfileRequest req) {
        User user = userRepository.findById(CurrentUser.id())
                .orElseThrow(() -> ApiException.unauthorized("Utilisateur introuvable"));
        if (req.firstName() != null) user.setFirstName(req.firstName());
        if (req.lastName() != null) user.setLastName(req.lastName());
        if (req.profilePhotoUrl() != null) user.setProfilePhotoUrl(req.profilePhotoUrl());
        if (req.city() != null) user.setCity(req.city());
        if (req.latitude() != null) user.setLatitude(req.latitude());
        if (req.longitude() != null) user.setLongitude(req.longitude());
        return ApiResponse.ok(UserDto.from(user));
    }
}
