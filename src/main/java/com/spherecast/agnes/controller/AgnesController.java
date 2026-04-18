package com.spherecast.agnes.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.spherecast.agnes.config.ClaudeConfig;
import com.spherecast.agnes.dto.DebugClaudeRequest;
import com.spherecast.agnes.dto.DebugQueryRequest;
import com.spherecast.agnes.dto.KnowledgeRequest;
import com.spherecast.agnes.dto.KnowledgeResponse;
import com.spherecast.agnes.dto.OptimizeRequest;
import com.spherecast.agnes.dto.OptimizeResponse;
import com.spherecast.agnes.handler.KnowledgeHandler;
import com.spherecast.agnes.handler.OptimizeHandler;
import com.spherecast.agnes.repository.AgnesRepository;
import com.spherecast.agnes.service.QueryResult;
import com.spherecast.agnes.service.SchemaProvider;
import com.spherecast.agnes.service.claude.ClaudeClient;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class AgnesController {

    private final ClaudeConfig claudeConfig;
    private final SchemaProvider schemaProvider;
    private final AgnesRepository agnesRepository;
    private final ClaudeClient claudeClient;
    private final KnowledgeHandler knowledgeHandler;
    private final OptimizeHandler optimizeHandler;

    public AgnesController(ClaudeConfig claudeConfig,
                           SchemaProvider schemaProvider,
                           AgnesRepository agnesRepository,
                           ClaudeClient claudeClient,
                           KnowledgeHandler knowledgeHandler,
                           OptimizeHandler optimizeHandler) {
        this.claudeConfig = claudeConfig;
        this.schemaProvider = schemaProvider;
        this.agnesRepository = agnesRepository;
        this.claudeClient = claudeClient;
        this.knowledgeHandler = knowledgeHandler;
        this.optimizeHandler = optimizeHandler;
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

    @PostMapping("/knowledge")
    public KnowledgeResponse knowledge(@Valid @RequestBody KnowledgeRequest req) {
        return knowledgeHandler.handle(req);
    }

    @PostMapping("/optimize")
    public OptimizeResponse optimize(@Valid @RequestBody OptimizeRequest req) {
        return optimizeHandler.handle(req);
    }

    // dev-only: round-trips a prompt through ClaudeClient
    @PostMapping("/debug/claude")
    public Map<String, Object> debugClaude(@Valid @RequestBody DebugClaudeRequest req) {
        long start = System.currentTimeMillis();
        String response = claudeClient.ask(
                req.system(),
                req.prompt(),
                req.history() != null ? req.history() : List.of(),
                req.temperature()
        );
        return Map.of(
                "response", response,
                "durationMs", System.currentTimeMillis() - start
        );
    }
}
