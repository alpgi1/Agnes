package com.spherecast.agnes.dto;

import com.spherecast.agnes.handler.optimizers.Finding;

import java.util.List;

public record OptimizeResponse(
        String sessionId,
        String markdown,
        List<String> optimizersRun,
        ScopeInfo scope,
        String routerReasoning,
        List<Finding> findings,
        String complianceStatus,
        long durationMs
) {
    public record ScopeInfo(String type, String value) {}
}
