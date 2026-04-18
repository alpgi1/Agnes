package com.spherecast.agnes.controller;

import com.spherecast.agnes.config.ClaudeConfig;
import com.spherecast.agnes.dto.DebugQueryRequest;
import com.spherecast.agnes.repository.AgnesRepository;
import com.spherecast.agnes.service.QueryResult;
import com.spherecast.agnes.service.SchemaProvider;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AgnesController {

    private final ClaudeConfig claudeConfig;
    private final SchemaProvider schemaProvider;
    private final AgnesRepository agnesRepository;

    public AgnesController(ClaudeConfig claudeConfig,
                           SchemaProvider schemaProvider,
                           AgnesRepository agnesRepository) {
        this.claudeConfig = claudeConfig;
        this.schemaProvider = schemaProvider;
        this.agnesRepository = agnesRepository;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "ok",
                "timestamp", Instant.now().toString()
        );
    }

    @GetMapping("/health/config")
    public Map<String, Object> healthConfig() {
        String apiKey = claudeConfig.apiKey();
        boolean apiKeyPresent = apiKey != null
                && !apiKey.isBlank()
                && !"PLACEHOLDER".equals(apiKey);
        return Map.of(
                "model", claudeConfig.model(),
                "apiKeyPresent", apiKeyPresent
        );
    }

    // dev-only: dumps the cached schema string
    @GetMapping(value = "/debug/schema", produces = MediaType.TEXT_PLAIN_VALUE)
    public String schema() {
        return schemaProvider.getSchemaAsPromptString();
    }

    // dev-only: runs a read-only SQL query through SqlGuard + SqlExecutor
    @PostMapping("/debug/query")
    public QueryResult debugQuery(@Valid @RequestBody DebugQueryRequest req) {
        return agnesRepository.executeQuery(req.sql());
    }
}
