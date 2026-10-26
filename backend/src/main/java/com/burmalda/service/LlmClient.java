package com.burmalda.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Клиент LLM (Anthropic Messages API). Включается переменной AI_API_KEY.
 * Без ключа AiService отвечает по правилам из реальных данных — для демо этого хватает.
 */
@Service
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final String apiKey;
    private final String model;
    private final RestClient http;

    public LlmClient(@Value("${app.ai.api-key:}") String apiKey,
                     @Value("${app.ai.model:claude-haiku-5-5}") String model,
                     @Value("${app.ai.base-url:https://api.anthropic.com}") String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.http = RestClient.builder().baseUrl(baseUrl).build();
    }

    public boolean enabled() {
        return !apiKey.isBlank();
    }

    public Optional<String> ask(String system, String question) {
        if (!enabled()) return Optional.empty();
        try {
            JsonNode res = http.post()
                    .uri("/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", model,
                            "max_tokens", 600,
                            "system", system,
                            "messages", List.of(Map.of("role", "user", "content", question))))
                    .retrieve()
                    .body(JsonNode.class);
            if (res == null) return Optional.empty();
            StringBuilder sb = new StringBuilder();
            for (JsonNode block : res.path("content")) {
                if ("text".equals(block.path("type").asText())) sb.append(block.path("text").asText());
            }
            return sb.isEmpty() ? Optional.empty() : Optional.of(sb.toString().trim());
        } catch (Exception e) {
            log.warn("LLM request failed, falling back to rules: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
