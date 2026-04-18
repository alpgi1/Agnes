package com.spherecast.agnes.dto;

import jakarta.validation.constraints.NotBlank;

public record DebugQueryRequest(@NotBlank String sql) {
}
