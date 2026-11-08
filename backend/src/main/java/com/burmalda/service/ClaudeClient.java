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
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Anthropic Claude (Messages API) with tool use. Enabled by the ANTHROPIC_API_KEY env variable
 * (console.anthropic.com -> API keys). When it is set, LlmClient uses Claude instead of Gemini.
 *
 * Same agent loop as for Gemini:
 *   1. send the conversation + tool descriptions
 *   2. the model answers with tool_use blocks -> run the tools, send tool_result blocks back
 *   3. repeat until the model answers with text (stop_reason != "tool_use")
 */
@Service
public class ClaudeClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeClient.class);
    private static final String API_VERSION = "2023-06-01";

    private final String apiKey;
    private final String model;
    private final RestClient http;
    private final ObjectMapper mapper;

    public ClaudeClient(@Value("${app.ai.anthropic-api-key:}") String apiKey,
                        @Value("${app.ai.claude-model:claude-haiku-5-5}") String model,
                        @Value("${app.ai.anthropic-base-url:https://api.anthropic.com}") String baseUrl,
                        ObjectMapper mapper) {
        this.apiKey = apiKey;
        this.model = model;
        // own copy: keep BigDecimal as written (100.00, not 1E+2) when tool results go to the model
        this.mapper = mapper.copy()
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
        JdkClientHttpRequestFactory rf = new JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build());
        rf.setReadTimeout(java.time.Duration.ofSeconds(60));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(rf).build();
    }

    public boolean enabled() {
        return !apiKey.isBlank();
    }

    public String model() {
        return model;
    }

    public Optional<LlmClient.AgentResult> runAgent(String system, List<LlmClient.Turn> history, String userMessage,
                                                    List<LlmClient.Tool> tools, LlmClient.ToolRunner runner,
                                                    int maxSteps) {
        if (!enabled()) return Optional.empty();
        try {
            ArrayNode messages = mapper.createArrayNode();
            for (LlmClient.Turn t : history) {
                messages.add(textMessage("model".equals(t.role()) ? "assistant" : "user", t.text()));
            }
            messages.add(textMessage("user", userMessage));

            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("max_tokens", 1024);
            // no "temperature": newer Claude models reject it ("deprecated for this model")
            body.put("system", system);
            ArrayNode toolDefs = body.putArray("tools");
            for (LlmClient.Tool tool : tools) {
                ObjectNode d = toolDefs.addObject();
                d.put("name", tool.name());
                d.put("description", tool.description());
                Map<String, Object> schema = tool.parameters() != null
                        ? tool.parameters() : Map.of("type", "OBJECT", "properties", Map.of());
                d.set("input_schema", jsonSchema(mapper.valueToTree(schema)));
            }
            body.set("messages", messages);

            List<String> used = new ArrayList<>();
            for (int step = 0; step < maxSteps; step++) {
                long t0 = System.currentTimeMillis();
                JsonNode res = mapper.readTree(call(mapper.writeValueAsString(body)));
                log.info("Claude step {} took {} ms ({} tools used so far)", step + 1,
                        System.currentTimeMillis() - t0, used.size());

                JsonNode content = res.path("content");
                // keep the assistant turn as is: tool_use ids must match the tool_result blocks
                messages.add(mapper.createObjectNode().put("role", "assistant").set("content", content));

                StringBuilder text = new StringBuilder();
                ArrayNode results = mapper.createArrayNode();
                for (JsonNode block : content) {
                    String type = block.path("type").asText();
                    if ("text".equals(type)) text.append(block.path("text").asText());
                    if (!"tool_use".equals(type)) continue;
                    String name = block.path("name").asText();
                    used.add(name);
                    ObjectNode r = results.addObject();
                    r.put("type", "tool_result");
                    r.put("tool_use_id", block.path("id").asText());
                    try {
                        r.put("content", mapper.writeValueAsString(runner.run(name, block.path("input"))));
                    } catch (Exception e) {
                        // the model sees the error and can explain it to the user
                        r.put("content", "Error: " + e.getMessage());
                        r.put("is_error", true);
                    }
                }
                if (results.isEmpty()) {
                    String answer = text.toString().trim();
                    return answer.isEmpty() ? Optional.empty() : Optional.of(new LlmClient.AgentResult(answer, used));
                }
                messages.add(mapper.createObjectNode().put("role", "user").set("content", results));
            }
            log.warn("Claude agent hit the step limit ({})", maxSteps);
            return Optional.empty();
        } catch (RestClientResponseException e) {
            log.warn("Claude request failed ({}): {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Claude request failed: {}", e.toString());
            return Optional.empty();
        }
    }

    /** POST /v1/messages; on 429 / 500 / 529 (overloaded) wait and retry once. */
    private String call(String json) {
        RestClientResponseException last = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return http.post()
                        .uri("/v1/messages")
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", API_VERSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(json)
                        .retrieve()
                        .body(String.class);
            } catch (RestClientResponseException e) {
                int code = e.getStatusCode().value();
                if (code != 429 && code != 500 && code != 529) throw e;
                last = e;
                log.warn("Claude answered {} (attempt {}/2)", code, attempt);
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        throw last;
    }

    /** Our tool schemas use Gemini's upper-case types (OBJECT, INTEGER...); JSON Schema wants lower case. */
    private JsonNode jsonSchema(JsonNode node) {
        if (node.isObject()) {
            ObjectNode out = mapper.createObjectNode();
            node.fields().forEachRemaining(e -> out.set(e.getKey(),
                    "type".equals(e.getKey()) && e.getValue().isTextual()
                            ? mapper.getNodeFactory().textNode(e.getValue().asText().toLowerCase(Locale.ROOT))
                            : jsonSchema(e.getValue())));
            return out;
        }
        if (node.isArray()) {
            ArrayNode out = mapper.createArrayNode();
            for (JsonNode n : node) out.add(jsonSchema(n));
            return out;
        }
        return node;
    }

    private ObjectNode textMessage(String role, String text) {
        ObjectNode m = mapper.createObjectNode().put("role", role);
        m.putArray("content").addObject().put("type", "text").put("text", text);
        return m;
    }
}
