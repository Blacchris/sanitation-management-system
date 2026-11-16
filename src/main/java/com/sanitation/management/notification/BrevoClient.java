package com.sanitation.management.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Thin wrapper over Brevo's transactional email and SMS REST endpoints.
 * Throws RestClientResponseException on 4xx/5xx (message includes Brevo's error body),
 * which OtpEmailOutboxWorker turns into a retry.
 */
@Slf4j
@Component
public class BrevoClient {

    private final RestClient http;
    private final String senderEmail;
    private final String senderName;
    private final String smsSender;
    private final String defaultCountryCode;

    public BrevoClient(RestClient.Builder builder,
                       @Value("${brevo.api-key}") String apiKey,
                       @Value("${brevo.base-url:https://api.brevo.com/v3}") String baseUrl,
                       @Value("${brevo.email.sender-email}") String senderEmail,
                       @Value("${brevo.email.sender-name:Sanitation Management}") String senderName,
                       @Value("${brevo.sms.sender:Sanitation}") String smsSender,
                       @Value("${brevo.sms.default-country-code:233}") String defaultCountryCode) {
        this.http = builder
                .baseUrl(baseUrl)
                .defaultHeader("api-key", apiKey)
                .defaultHeader("accept", "application/json")
                .build();
        this.senderEmail = senderEmail;
        this.senderName = senderName;
        this.smsSender = smsSender;
        this.defaultCountryCode = defaultCountryCode;
    }

    public void sendEmail(String to, String subject, String text) {
        Map<String, Object> body = Map.of(
                "sender", Map.of("name", senderName, "email", senderEmail),
                "to", List.of(Map.of("email", to)),
                "subject", subject,
                "textContent", text);
        post("/smtp/email", body);
    }

    public void sendSms(String phone, String content) {

        log.info("Brevo SMS sender: '{}', length: {}",
                smsSender, smsSender.length());

        Map<String, Object> body = Map.of(
                "sender", smsSender,
                "recipient", toInternational(phone),
                "content", content,
                "type", "transactional"
        );

        post("/transactionalSMS/send", body);
    }
    private void post(String path, Map<String, Object> body) {
        http.post().uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    /** Brevo wants country code + number, no '+': +233244... / 0244... / 00233244... -> 233244... */
    String toInternational(String phone) {
        String p = phone.trim();
        if (p.startsWith("+")) return p.substring(1);
        if (p.startsWith("00")) return p.substring(2);
        if (p.startsWith("0")) return defaultCountryCode + p.substring(1);
        return p;
    }
}
