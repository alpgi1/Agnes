package com.spherecast.agnes.dto;

public record KnowledgeResponse(
        String sessionId,
        String markdown,
        String sqlUsed,
        int rowCount,
        boolean truncated,
        long durationMs
) {
}
