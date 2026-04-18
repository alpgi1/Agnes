package com.spherecast.agnes.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record KnowledgeRequest(
        @NotBlank String prompt,
        List<ChatMessage> history,
        String sessionId
) {
}
