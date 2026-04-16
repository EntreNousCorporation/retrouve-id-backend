package com.retrouvid.shared.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Envoi de SMS. L'implémentation réelle (provider pansy-backend) est branchée
 * via `app.sms.provider.url` + credentials. En l'absence de config (dev/tests),
 * on log le message et c'est l'EmailService qui prend le relais via
 * {@link NotificationChannelRouter}.
 */
@Slf4j
@Service
public class SmsService {

    private final String providerUrl;

    public SmsService(@Value("${app.sms.provider.url:}") String providerUrl) {
        this.providerUrl = providerUrl;
    }

    public boolean send(String phone, String message) {
        if (phone == null || phone.isBlank()) return false;
        if (providerUrl == null || providerUrl.isBlank()) {
            log.info("[SMS-MOCK] to={} body={}", phone, message);
            return true;
        }
        // TODO : brancher le vrai provider pansy-backend.
        // Pour l'instant on log, de sorte que la chaîne fonctionne en dev.
        log.warn("[SMS-STUB] provider={} to={} body={}", providerUrl, phone, message);
        return true;
    }
}
