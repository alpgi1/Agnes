package com.spherecast.agnes.handler.optimizers;

import com.spherecast.agnes.dto.ChatMessage;
import com.spherecast.agnes.handler.RouterDecision;

import java.util.List;

public record OptimizerContext(
        String userPrompt,
        RouterDecision.Scope scope,
        ScopedData data,
        List<ChatMessage> history,
        String sessionId
) {}
