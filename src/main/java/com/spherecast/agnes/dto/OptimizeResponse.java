package com.spherecast.agnes.dto;

import java.util.List;

public record OptimizeResponse(
        String sessionId,
        String markdown,
        List<String> optimizersRun,
        ScopeInfo scope,
        String routerReasoning,
        long durationMs
) {
    public record ScopeInfo(String type, String value) {}
}
