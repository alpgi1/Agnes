package com.spherecast.agnes.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "compliance")
public record ComplianceConfig(
        String regulationPath
) {
    public ComplianceConfig {
        if (regulationPath == null || regulationPath.isBlank()) {
            regulationPath = "classpath:compliance/eu_1169_2011.json";
        }
    }
}
