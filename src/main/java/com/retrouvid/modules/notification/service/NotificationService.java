package com.retrouvid.modules.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retrouvid.modules.notification.entity.Notification;
import com.retrouvid.modules.notification.entity.NotificationType;
import com.retrouvid.modules.notification.repository.NotificationRepository;
import com.retrouvid.modules.user.entity.User;
import com.retrouvid.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final OneSignalPushService push;
    private final ObjectMapper objectMapper;

    @Transactional
    public Notification notify(UUID userId, NotificationType type, String title, String body, Map<String, String> data) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return null;
        String dataJson = null;
        try { dataJson = data == null ? null : objectMapper.writeValueAsString(data); }
        catch (JsonProcessingException ignored) {}
        Notification n = Notification.builder()
                .user(user).type(type).title(title).body(body).data(dataJson).read(false).build();
        n = notificationRepository.save(n);

        push.sendToUser(userId, title, body, data);
        return n;
    }

    @Transactional(readOnly = true)
    public Page<Notification> list(UUID userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional
    public void markRead(UUID id, UUID userId) {
        notificationRepository.findById(id).ifPresent(n -> {
            if (n.getUser().getId().equals(userId)) n.setRead(true);
        });
    }

    @Transactional
    public void markAllRead(UUID userId) {
        notificationRepository.markAllRead(userId);
    }
}
