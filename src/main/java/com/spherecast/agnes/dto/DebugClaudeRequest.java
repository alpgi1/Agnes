package com.spherecast.agnes.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record DebugClaudeRequest(
        String system,
        @NotBlank String prompt,
        List<ChatMessage> history,
        Double temperature
) {
}
