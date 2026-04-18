package com.spherecast.agnes.controller;

import com.spherecast.agnes.config.ClaudeConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AgnesController {

    private final ClaudeConfig claudeConfig;

    public AgnesController(ClaudeConfig claudeConfig) {
        this.claudeConfig = claudeConfig;
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
}
