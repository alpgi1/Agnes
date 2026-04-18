package com.spherecast.agnes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChatMessage(
        @NotBlank
        @Pattern(regexp = "user|assistant", message = "role must be 'user' or 'assistant'")
        String role,
        @NotBlank String content
) {
    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }
}
