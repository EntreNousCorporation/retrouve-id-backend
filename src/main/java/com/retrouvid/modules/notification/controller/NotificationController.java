package com.retrouvid.modules.notification.controller;

import com.retrouvid.modules.notification.entity.Notification;
import com.retrouvid.modules.notification.service.NotificationService;
import com.retrouvid.security.CurrentUser;
import com.retrouvid.shared.dto.ApiResponse;
import com.retrouvid.shared.dto.PagedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    public record NotificationDto(UUID id, String title, String body, String type, boolean read,
                                  String data, Instant createdAt) {
        static NotificationDto from(Notification n) {
            return new NotificationDto(n.getId(), n.getTitle(), n.getBody(),
                    n.getType() == null ? null : n.getType().name(),
                    n.isRead(), n.getData(), n.getCreatedAt());
        }
    }

    @GetMapping
    public ApiResponse<PagedResponse<NotificationDto>> list(@PageableDefault(size = 20) Pageable pageable) {
        var page = service.list(CurrentUser.id(), pageable).map(NotificationDto::from);
        return ApiResponse.ok(PagedResponse.of(page));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<Void> read(@PathVariable UUID id) {
        service.markRead(id, CurrentUser.id());
        return ApiResponse.ok(null);
    }

    @PatchMapping("/read-all")
    public ApiResponse<Void> readAll() {
        service.markAllRead(CurrentUser.id());
        return ApiResponse.ok(null);
    }
}
