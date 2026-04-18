package com.spherecast.agnes.service;

import java.util.List;
import java.util.Map;

public record QueryResult(
        List<Map<String, Object>> rows,
        List<String> columns,
        boolean truncated,
        long executionMillis
) {
}
