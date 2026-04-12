package com.retrouvid.modules.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class OneSignalPushService {

    private static final String API_URL = "https://onesignal.com/api/v1/notifications";

    private final String appId;
    private final String restApiKey;
    private final RestClient http;

    public OneSignalPushService(
            @Value("${app.onesignal.app-id:}") String appId,
            @Value("${app.onesignal.rest-api-key:}") String restApiKey) {
        this.appId = appId;
        this.restApiKey = restApiKey;
        this.http = RestClient.create();
    }

    public void sendToUser(UUID userId, String title, String body, Map<String, String> data) {
        if (appId.isBlank() || restApiKey.isBlank()) {
            log.debug("OneSignal disabled — would push to user {}: {} / {}", userId, title, body);
            return;
        }
        try {
            Map<String, Object> payload = Map.of(
                    "app_id", appId,
                    "include_external_user_ids", List.of(userId.toString()),
                    "channel_for_external_user_ids", "push",
                    "headings", Map.of("en", title, "fr", title),
                    "contents", Map.of("en", body, "fr", body),
                    "data", data == null ? Map.of() : data
            );
            http.post()
                    .uri(API_URL)
                    .header("Authorization", "Basic " + restApiKey)
                    .header("Content-Type", "application/json")
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("OneSignal send failed: {}", e.getMessage());
        }
    }
}
