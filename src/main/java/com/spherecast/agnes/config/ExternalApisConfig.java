package com.spherecast.agnes.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "iherb")
public record ExternalApisConfig(
        String rapidapiKey,
        String rapidapiHost,
        String baseUrl
) {
    public boolean isConfigured() {
        return rapidapiKey != null
                && !rapidapiKey.isBlank()
                && !"PLACEHOLDER".equals(rapidapiKey);
    }
}
