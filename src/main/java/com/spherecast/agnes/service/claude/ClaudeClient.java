package com.spherecast.agnes.service.claude;

import com.spherecast.agnes.config.ClaudeConfig;
import com.spherecast.agnes.dto.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class ClaudeClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeClient.class);

    private static final double DEFAULT_TEMPERATURE = 0.5;
    private static final double JSON_TEMPERATURE = 0.2;
    private static final int MAX_ATTEMPTS = 3;
    private static final long[] BACKOFF_MS = {500L, 1500L};
    private static final Set<Integer> RETRIABLE_STATUS = Set.of(429, 500, 502, 503, 504);
    private static final String JSON_ONLY_SUFFIX =
            "\n\nOutput ONLY valid JSON. No markdown fences, no prose before or after.";

    private final ClaudeConfig config;
    private final JsonExtractor jsonExtractor;
    private final RestClient restClient;

    @Autowired
    public ClaudeClient(ClaudeConfig config, JsonExtractor jsonExtractor) {
        this(config, jsonExtractor, defaultBuilder());
    }

    ClaudeClient(ClaudeConfig config, JsonExtractor jsonExtractor, RestClient.Builder builder) {
        this.config = config;
        this.jsonExtractor = jsonExtractor;
        this.restClient = builder
                .baseUrl(config.baseUrl())
                .defaultHeader("anthropic-version", config.anthropicVersion())
                .defaultHeader("content-type", "application/json")
                .defaultHeader("x-api-key", config.apiKey() == null ? "" : config.apiKey())
                .build();
    }

    private static RestClient.Builder defaultBuilder() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(60));
        return RestClient.builder().requestFactory(factory);
    }

    public String ask(String systemPrompt, String userPrompt) {
        return ask(systemPrompt, userPrompt, List.of(), null);
    }

    public String ask(String systemPrompt, String userPrompt, List<ChatMessage> history) {
        return ask(systemPrompt, userPrompt, history, null);
    }

    public String ask(String systemPrompt, String userPrompt,
                      List<ChatMessage> history, Double temperature) {
        guardApiKey();

        List<ChatMessage> messages = new ArrayList<>();
        if (history != null) {
            messages.addAll(history);
        }
        if (!messages.isEmpty() && "user".equals(messages.getLast().role())) {
            log.warn("History ends with a user message; Claude will likely reject the request");
        }
        messages.add(ChatMessage.user(userPrompt));

        ClaudeMessagesRequest request = new ClaudeMessagesRequest(
                config.model(),
                config.maxTokens(),
                systemPrompt,
                temperature != null ? temperature : DEFAULT_TEMPERATURE,
                messages
        );

        long start = System.currentTimeMillis();
        ClaudeMessagesResponse response = executeWithRetry(request);
        long duration = System.currentTimeMillis() - start;

        String text = extractText(response);
        if (log.isDebugEnabled() && response.usage() != null) {
            log.debug("Claude call: model={}, inputTokens={}, outputTokens={}, stopReason={}, durationMs={}",
                    response.model(),
                    response.usage().inputTokens(),
                    response.usage().outputTokens(),
                    response.stopReason(),
                    duration);
        }
        return text;
    }

    public JsonNode askJson(String systemPrompt, String userPrompt) {
        return askJson(systemPrompt, userPrompt, List.of());
    }

    public JsonNode askJson(String systemPrompt, String userPrompt, List<ChatMessage> history) {
        String text = ask(withJsonInstruction(systemPrompt), userPrompt, history, JSON_TEMPERATURE);
        return jsonExtractor.extractJson(text);
    }

    public JsonNode askJson(String systemPrompt, String userPrompt,
                            List<ChatMessage> history, double temperature) {
        String text = ask(withJsonInstruction(systemPrompt), userPrompt, history, temperature);
        return jsonExtractor.extractJson(text);
    }

    public <T> T askJson(String systemPrompt, String userPrompt, Class<T> type) {
        String text = ask(withJsonInstruction(systemPrompt), userPrompt, List.of(), JSON_TEMPERATURE);
        return jsonExtractor.extractJson(text, type);
    }

    private String withJsonInstruction(String systemPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return JSON_ONLY_SUFFIX.trim();
        }
        return systemPrompt + JSON_ONLY_SUFFIX;
    }

    private void guardApiKey() {
        String key = config.apiKey();
        if (key == null || key.isBlank() || "PLACEHOLDER".equals(key)) {
            throw new ClaudeApiException(
                    "ANTHROPIC_API_KEY not configured — set the env var and restart",
                    -1, null);
        }
    }

    private String extractText(ClaudeMessagesResponse response) {
        if (response == null || response.content() == null || response.content().isEmpty()) {
            throw new ClaudeApiException("No content blocks in Claude response", 200, null);
        }
        StringBuilder sb = new StringBuilder();
        for (ClaudeMessagesResponse.ContentBlock block : response.content()) {
            if ("text".equals(block.type()) && block.text() != null) {
                sb.append(block.text());
            }
        }
        if (sb.isEmpty()) {
            throw new ClaudeApiException("No text content in Claude response", 200, null);
        }
        return sb.toString();
    }

    private ClaudeMessagesResponse executeWithRetry(ClaudeMessagesRequest request) {
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return restClient.post()
                        .uri("/v1/messages")
                        .body(request)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (req, resp) -> {
                            int status = resp.getStatusCode().value();
                            String body = new String(resp.getBody().readAllBytes());
                            throw new RestClientResponseException(
                                    "Claude API error " + status,
                                    resp.getStatusCode(), resp.getStatusText(),
                                    resp.getHeaders(), body.getBytes(), null);
                        })
                        .body(ClaudeMessagesResponse.class);
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if (!RETRIABLE_STATUS.contains(status) || attempt == MAX_ATTEMPTS) {
                    throw new ClaudeApiException(
                            "Claude API returned HTTP " + status + ": " + e.getResponseBodyAsString(),
                            status, e.getResponseBodyAsString(), e);
                }
                lastError = e;
                log.warn("Claude API HTTP {} on attempt {}/{}, retrying", status, attempt, MAX_ATTEMPTS);
            } catch (ResourceAccessException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw new ClaudeApiException(
                            "Claude API unreachable: " + e.getMessage(), -1, null, e);
                }
                lastError = e;
                log.warn("Claude API I/O error on attempt {}/{}: {}", attempt, MAX_ATTEMPTS, e.getMessage());
            }

            sleep(BACKOFF_MS[attempt - 1]);
        }
        throw new ClaudeApiException(
                "Claude API failed after " + MAX_ATTEMPTS + " attempts", -1, null, lastError);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ClaudeApiException("Interrupted while retrying Claude API call", -1, null, ie);
        }
    }
}
