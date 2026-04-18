package com.spherecast.agnes.service.claude;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.spherecast.agnes.dto.ChatMessage;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClaudeMessagesRequest(
        String model,
        @JsonProperty("max_tokens") int maxTokens,
        String system,
        Double temperature,
        List<ChatMessage> messages
) {
}
