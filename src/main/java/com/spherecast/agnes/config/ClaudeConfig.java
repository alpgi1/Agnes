package com.spherecast.agnes.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "claude")
public record ClaudeConfig(
        String apiKey,
        @DefaultValue("claude-sonnet-4-6") String model,
        @DefaultValue("4096") int maxTokens,
        @DefaultValue("https://api.anthropic.com") String baseUrl,
        @DefaultValue("2023-06-01") String anthropicVersion
) {
}
