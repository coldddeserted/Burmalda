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
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
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
    /** Model that answered last time: if the main one is down we stick to the one that works. */
    private volatile String activeModel;
    /** Flash models this key can use, asked from Google once (GET /v1beta/models). */
    private volatile List<String> discovered;
    private final RestClient http;
    private final ObjectMapper mapper;

    public LlmClient(@Value("${app.ai.api-key:}") String apiKey,
                     @Value("${app.ai.model:gemini-3.8-flash}") String model,
                     @Value("${app.ai.fallback-models:}") List<String> fallbackModels,
                     @Value("${app.ai.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
                     ObjectMapper mapper) {
        this.apiKey = apiKey;
        this.model = model;
        this.activeModel = model;
        this.fallbackModels = fallbackModels.stream().map(String::trim).filter(x -> !x.isEmpty()).toList();
        // own copy: keep BigDecimal as written (100.00, not 1E+2) when tool results go to the model
        this.mapper = mapper.copy()
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
        // java.net.http client with timeouts: without them a stuck request hangs the chat forever
        JdkClientHttpRequestFactory rf = new JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build());
        rf.setReadTimeout(java.time.Duration.ofSeconds(30)); // a stuck model -> switch to another one sooner
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
        return activeModel;
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

            List<String> used = new ArrayList<>();
            for (int step = 0; step < maxSteps; step++) {
                long t0 = System.currentTimeMillis();
                String raw = call(body);
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
     * One generateContent call that survives a bad moment at Google:
     *  - 429 / 500 / 503 ("high demand", free tier limit) -> wait and retry the same model once;
     *  - timeout or still failing -> try the next model: AI_FALLBACK_MODELS, then the flash models
     *    this key can use (asked from Google, not guessed);
     *  - the model that worked becomes the first choice for the next requests.
     * The whole call gives up after ~75 s, then the chat answers with rules.
     */
    private String call(ObjectNode body) throws Exception {
        long deadline = System.currentTimeMillis() + 75_000;
        List<String> models = new ArrayList<>(new LinkedHashSet<>(candidates(false)));
        Exception last = null;
        for (int i = 0; i < models.size(); i++) {
            String m = models.get(i);
            int attempts = i == 0 ? 2 : 1;
            for (int attempt = 1; attempt <= attempts; attempt++) {
                if (System.currentTimeMillis() > deadline) throw last != null ? last : new IllegalStateException("AI timeout");
                try {
                    body.set("generationConfig", generationConfig(m));
                    // read as text and parse ourselves: does not depend on the response Content-Type
                    String raw = http.post()
                            .uri("/v1beta/models/{model}:generateContent", m)
                            .header("x-goog-api-key", apiKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(mapper.writeValueAsString(body))
                            .retrieve()
                            .body(String.class);
                    if (!m.equals(activeModel)) {
                        log.info("Gemini: switched to model {}", m);
                        activeModel = m;
                    }
                    return raw;
                } catch (RestClientResponseException e) {
                    int code = e.getStatusCode().value();
                    boolean temporary = code == 429 || code == 500 || code == 503;
                    boolean unknownModel = code == 404 || code == 400 && i > 0;
                    if (!temporary && !unknownModel) throw e;
                    last = e;
                    log.warn("Gemini {} answered {} (attempt {}/{})", m, code, attempt, attempts);
                    if (unknownModel) break;
                    if (attempt < attempts) sleep(1500);
                } catch (ResourceAccessException e) {
                    last = e;
                    log.warn("Gemini {} did not answer in time, trying another model", m);
                    break; // a stuck model will not get faster on a retry
                }
            }
            if (i == models.size() - 1) {
                // ran out of known models -> ask Google which ones this key can use (once)
                for (String extra : candidates(true)) if (!models.contains(extra)) models.add(extra);
            }
        }
        throw last != null ? last : new IllegalStateException("No Gemini model answered");
    }

    /** Order: the model that worked last time, the configured one, AI_FALLBACK_MODELS, then discovered ones. */
    private List<String> candidates(boolean withDiscovered) {
        List<String> list = new ArrayList<>();
        list.add(activeModel);
        list.add(model);
        list.addAll(fallbackModels);
        if (withDiscovered) list.addAll(discover());
        return list;
    }

    /**
     * GET /v1beta/models: which models this API key may call. We keep text "flash" models that support
     * generateContent (fast and cheap): stable ones first, then "lite", then previews. Max 4.
     */
    private List<String> discover() {
        if (discovered != null) return discovered;
        List<String> found = new ArrayList<>();
        try {
            String raw = http.get()
                    .uri("/v1beta/models?pageSize=200")
                    .header("x-goog-api-key", apiKey)
                    .retrieve()
                    .body(String.class);
            for (JsonNode m : mapper.readTree(raw).path("models")) {
                String name = m.path("name").asText().replaceFirst("^models/", "");
                boolean canChat = false;
                for (JsonNode method : m.path("supportedGenerationMethods")) {
                    if ("generateContent".equals(method.asText())) canChat = true;
                }
                boolean textFlash = name.contains("flash") && !name.matches(".*(image|tts|audio|live|embedding|vision).*");
                if (canChat && textFlash) found.add(name);
            }
            found.sort(Comparator.comparingInt(LlmClient::rank).thenComparing(Comparator.reverseOrder()));
            if (found.size() > 4) found = new ArrayList<>(found.subList(0, 4));
            log.info("Gemini models available for this key: {}", found);
        } catch (Exception e) {
            log.warn("Could not list Gemini models: {}", e.toString());
        }
        discovered = found;
        return found;
    }

    private static int rank(String name) {
        if (name.contains("preview") || name.contains("exp")) return 2;
        if (name.contains("lite")) return 1;
        return 0;
    }

    /** "Thinking" makes answers slow; a small budget is plenty for our tools. Each model family has its own knob. */
    private ObjectNode generationConfig(String m) {
        ObjectNode gen = mapper.createObjectNode().put("temperature", 0.2);
        if (m.contains("2.5")) gen.set("thinkingConfig", mapper.createObjectNode().put("thinkingBudget", 512));
        else if (m.startsWith("gemini-3")) gen.set("thinkingConfig", mapper.createObjectNode().put("thinkingLevel", "low"));
        return gen;
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
