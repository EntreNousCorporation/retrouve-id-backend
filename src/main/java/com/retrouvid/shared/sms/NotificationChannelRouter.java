package com.retrouvid.shared.sms;

import com.retrouvid.modules.auth.service.EmailService;
import com.retrouvid.modules.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Route un message vers SMS si le user a un téléphone, sinon vers email. Si
 * aucun canal n'est disponible, log uniquement (push déjà envoyée en parallèle
 * via NotificationService).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationChannelRouter {

    private final SmsService smsService;
    private final EmailService emailService;

    public void sendUrgent(User user, String subject, String body) {
        if (user.getPhone() != null && !user.getPhone().isBlank()) {
            smsService.send(user.getPhone(), body);
            return;
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            emailService.send(user.getEmail(), subject, body);
            return;
        }
        log.warn("[CHANNEL] user {} n'a ni téléphone ni email — notification non livrée : {}", user.getId(), subject);
    }
}
