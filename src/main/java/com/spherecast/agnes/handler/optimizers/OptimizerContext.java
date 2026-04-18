package com.spherecast.agnes.handler.optimizers;

import com.spherecast.agnes.dto.ChatMessage;
import com.spherecast.agnes.handler.RouterDecision;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record OptimizerContext(
        String userPrompt,
        RouterDecision.Scope scope,
        ScopedData data,
        List<ChatMessage> history,
        String sessionId,
        Map<OptimizerType, OptimizerResult> priorResults
) {
    public OptimizerContext withPriorResult(OptimizerType type, OptimizerResult result) {
        var next = new EnumMap<>(priorResults);
        next.put(type, result);
        return new OptimizerContext(userPrompt, scope, data, history, sessionId, Map.copyOf(next));
    }

    public static OptimizerContext initial(String userPrompt, RouterDecision.Scope scope,
                                           ScopedData data, List<ChatMessage> history,
                                           String sessionId) {
        return new OptimizerContext(userPrompt, scope, data, history, sessionId, Map.of());
    }
}
