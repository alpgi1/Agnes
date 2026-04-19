package com.spherecast.agnes.dto;

import java.util.List;

public record GraphResponse(
        List<GraphNode> nodes,
        List<GraphEdge> edges,
        GraphMeta meta
) {
    public record GraphMeta(
            String view,
            int nodeCount,
            int edgeCount,
            long durationMs
    ) {}
}
