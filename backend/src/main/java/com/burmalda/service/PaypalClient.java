package com.burmalda.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PayPal Payouts API (он же Mass Pay): одним запросом платим многим людям.
 * Docs: https://developer.paypal.com/docs/api/payments.payouts-batch/v1/
 *
 * Если PAYPAL_CLIENT_ID / PAYPAL_CLIENT_SECRET не заданы — работаем в режиме симуляции:
 * деньги двигаются только в нашей БД, batchId начинается с "SIM-".
 */
@Service
public class PaypalClient {

    private static final Logger log = LoggerFactory.getLogger(PaypalClient.class);

    private final String clientId;
    private final String clientSecret;
    private final RestClient http;

    private String accessToken;
    private Instant tokenExpires = Instant.EPOCH;

    public PaypalClient(@Value("${app.paypal.client-id:}") String clientId,
                        @Value("${app.paypal.client-secret:}") String clientSecret,
                        @Value("${app.paypal.base-url:https://api-m.sandbox.paypal.com}") String baseUrl) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.http = RestClient.builder().baseUrl(baseUrl).build();
    }

    public record Item(String receiverEmail, BigDecimal amount, String note) {
    }

    public record Batch(String batchId, String status, boolean simulated) {
    }

    public boolean enabled() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    public Batch payout(List<Item> items, String subject) {
        if (!enabled()) {
            String id = "SIM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            log.info("PayPal simulated payout {}: {} items", id, items.size());
            return new Batch(id, "SUCCESS", true);
        }
        try {
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("sender_batch_id", "burmalda-" + UUID.randomUUID());
            header.put("email_subject", subject);

            List<Map<String, Object>> body = items.stream().map(i -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("recipient_type", "EMAIL");
                m.put("receiver", i.receiverEmail());
                m.put("note", i.note());
                m.put("amount", Map.of("value", i.amount().toPlainString(), "currency", "USD"));
                return m;
            }).toList();

            JsonNode res = http.post()
                    .uri("/v1/payments/payouts")
                    .header("Authorization", "Bearer " + token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("sender_batch_header", header, "items", body))
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode bh = res == null ? null : res.path("batch_header");
            if (bh == null || bh.path("payout_batch_id").isMissingNode()) {
                throw new IllegalStateException("пустой ответ PayPal");
            }
            return new Batch(bh.path("payout_batch_id").asText(), bh.path("batch_status").asText(), false);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("PayPal payout failed", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PayPal не принял выплату: " + e.getMessage());
        }
    }

    /** OAuth2 client_credentials, токен кэшируем до истечения. */
    private synchronized String token() {
        if (accessToken != null && Instant.now().isBefore(tokenExpires)) return accessToken;
        String basic = Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        JsonNode res = http.post()
                .uri("/v1/oauth2/token")
                .header("Authorization", "Basic " + basic)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials")
                .retrieve()
                .body(JsonNode.class);
        if (res == null || !res.hasNonNull("access_token")) throw new IllegalStateException("PayPal не выдал токен");
        accessToken = res.get("access_token").asText();
        tokenExpires = Instant.now().plusSeconds(res.path("expires_in").asLong(3000) - 60);
        return accessToken;
    }
}
