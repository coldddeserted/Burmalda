package com.burmalda.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Google Gemini API with function calling (tools). Enabled by the AI_API_KEY env variable
 * (free key: aistudio.google.com -> Get API key).
 *
 * Agent loop:
 *   1. send the conversation + tool descriptions to the model
 *   2. if the model answers with functionCall(s) -> run them on our side, send the results back
 *   3. repeat until the model answers with plain text (or the step limit is hit)
 *
 * Without a key AiService falls back to rule-based answers on the same real data.
 */
@Service
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final String apiKey;
    private final String model;
    private final List<String> fallbackModels;
    private final RestClient http;
    private final ObjectMapper mapper;

    public LlmClient(@Value("${app.ai.api-key:}") String apiKey,
                     @Value("${app.ai.model:gemini-3.8-flash}") String model,
                     @Value("${app.ai.fallback-models:}") List<String> fallbackModels,
                     @Value("${app.ai.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
                     ObjectMapper mapper) {
        this.apiKey = apiKey;
        this.model = model;
        this.fallbackModels = fallbackModels.stream().map(String::trim).filter(x -> !x.isEmpty()).toList();
        // own copy: keep BigDecimal as written (100.00, not 1E+2) when tool results go to the model
        this.mapper = mapper.copy()
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
        // java.net.http client with timeouts: without them a stuck request hangs the chat forever
        JdkClientHttpRequestFactory rf = new JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build());
        rf.setReadTimeout(java.time.Duration.ofSeconds(60));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(rf).build();
    }

    /** A tool the model may call. parameters = OpenAPI schema (see AiService.params), null if no args. */
    public record Tool(String name, String description, Map<String, Object> parameters) {
    }

    /** One previous message of the chat. role: "user" or "model". */
    public record Turn(String role, String text) {
    }

    /** Runs a tool by name. Return anything Jackson can serialize (record, Map, List...). */
    @FunctionalInterface
    public interface ToolRunner {
        Object run(String name, JsonNode args) throws Exception;
    }

    /** Final answer + which tools the agent called (shown in the chat for transparency). */
    public record AgentResult(String text, List<String> toolsUsed) {
    }

    public boolean enabled() {
        return !apiKey.isBlank();
    }

    public String model() {
        return model;
    }

    public Optional<AgentResult> runAgent(String system, List<Turn> history, String userMessage,
                                          List<Tool> tools, ToolRunner runner, int maxSteps) {
        if (!enabled()) return Optional.empty();
        try {
            ArrayNode contents = mapper.createArrayNode();
            for (Turn t : history) contents.add(textContent(t.role(), t.text()));
            contents.add(textContent("user", userMessage));

            ObjectNode body = mapper.createObjectNode();
            body.set("systemInstruction", mapper.createObjectNode()
                    .set("parts", mapper.createArrayNode().add(mapper.createObjectNode().put("text", system))));
            body.set("contents", contents);
            if (!tools.isEmpty()) {
                ArrayNode decls = mapper.createArrayNode();
                for (Tool tool : tools) {
                    ObjectNode d = decls.addObject();
                    d.put("name", tool.name());
                    d.put("description", tool.description());
                    // no-arg tools: Gemini rejects an OBJECT schema with empty properties, so omit it
                    if (tool.parameters() != null) d.set("parameters", mapper.valueToTree(tool.parameters()));
                }
                body.set("tools", mapper.createArrayNode().add(mapper.createObjectNode().set("functionDeclarations", decls)));
            }
            ObjectNode gen = mapper.createObjectNode().put("temperature", 0.2);
            // "thinking" makes answers slow (the 2nd step took >45 s). A small budget is plenty for our tools.
            if (model.contains("2.5")) gen.set("thinkingConfig", mapper.createObjectNode().put("thinkingBudget", 512));
            else if (model.startsWith("gemini-3")) gen.set("thinkingConfig", mapper.createObjectNode().put("thinkingLevel", "low"));
            body.set("generationConfig", gen);

            List<String> used = new ArrayList<>();
            for (int step = 0; step < maxSteps; step++) {
                long t0 = System.currentTimeMillis();
                String raw = call(mapper.writeValueAsString(body));
                log.info("Gemini step {} took {} ms ({} tools used so far)", step + 1,
                        System.currentTimeMillis() - t0, used.size());
                JsonNode res = raw == null || raw.isBlank() ? null : mapper.readTree(raw);

                JsonNode content = res == null ? null : res.path("candidates").path(0).path("content");
                if (content == null || content.isMissingNode()) {
                    log.warn("Gemini returned no content: {}", res);
                    return Optional.empty();
                }
                // Keep the model turn AS IS: newer Gemini models attach "thoughtSignature" to parts
                // and require it to be sent back unchanged.
                contents.add(content);

                List<JsonNode> calls = new ArrayList<>();
                StringBuilder text = new StringBuilder();
                for (JsonNode part : content.path("parts")) {
                    if (part.has("functionCall")) calls.add(part.get("functionCall"));
                    else if (part.has("text") && !part.path("thought").asBoolean(false)) text.append(part.get("text").asText());
                }
                if (calls.isEmpty()) {
                    String answer = text.toString().trim();
                    return answer.isEmpty() ? Optional.empty() : Optional.of(new AgentResult(answer, used));
                }

                ArrayNode responseParts = mapper.createArrayNode();
                for (JsonNode call : calls) {
                    String name = call.path("name").asText();
                    used.add(name);
                    Object result;
                    try {
                        result = runner.run(name, call.path("args"));
                    } catch (Exception e) {
                        // the model sees the error and can explain it to the user
                        result = Map.of("error", String.valueOf(e.getMessage()));
                    }
                    ObjectNode fr = responseParts.addObject().putObject("functionResponse");
                    fr.put("name", name);
                    fr.set("response", mapper.createObjectNode().set("result", toTree(result)));
                }
                contents.add(mapper.createObjectNode().put("role", "user").set("parts", responseParts));
            }
            log.warn("Agent hit the step limit ({})", maxSteps);
            return Optional.empty();
        } catch (RestClientResponseException e) {
            // Gemini's error body says what is wrong: bad key, unknown model, quota exceeded...
            log.warn("LLM request failed ({}), falling back to rules: {}", e.getStatusCode().value(),
                    e.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("LLM request failed, falling back to rules: {}", e.toString());
            return Optional.empty();
        }
    }

    /**
     * One generateContent call with retries. Gemini sometimes answers 503 "high demand" or 429 (free tier
     * limit): wait and try again, then try the fallback models (AI_FALLBACK_MODELS) if any.
     */
    private String call(String json) {
        List<String> models = new ArrayList<>();
        models.add(model);
        models.addAll(fallbackModels);
        RestClientResponseException last = null;
        for (String m : models) {
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    // read as text and parse ourselves: does not depend on the response Content-Type
                    return http.post()
                            .uri("/v1beta/models/{model}:generateContent", m)
                            .header("x-goog-api-key", apiKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(json)
                            .retrieve()
                            .body(String.class);
                } catch (RestClientResponseException e) {
                    int code = e.getStatusCode().value();
                    boolean temporary = code == 429 || code == 500 || code == 503;
                    if (!temporary) throw e;
                    last = e;
                    log.warn("Gemini {} answered {} (attempt {}/3), retrying...", m, code, attempt);
                    sleep(attempt * 1500L);
                }
            }
        }
        throw last;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Via text, so BigDecimal keeps its scale (100.00), not 1E+2. */
    private JsonNode toTree(Object value) throws Exception {
        return mapper.readTree(mapper.writeValueAsString(value));
    }

    private ObjectNode textContent(String role, String text) {
        ObjectNode c = mapper.createObjectNode().put("role", role);
        c.set("parts", mapper.createArrayNode().add(mapper.createObjectNode().put("text", text)));
        return c;
    }
}
