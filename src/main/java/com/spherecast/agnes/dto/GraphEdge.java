package com.spherecast.agnes.dto;

import java.util.Map;

public record GraphEdge(
        String from,
        String to,
        String type,
        Map<String, Object> properties
) {}
