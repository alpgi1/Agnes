package com.spherecast.agnes.handler;

import com.spherecast.agnes.dto.ChatMessage;
import com.spherecast.agnes.handler.RouterDecision.Scope;
import com.spherecast.agnes.handler.RouterDecision.Scope.ScopeType;
import com.spherecast.agnes.handler.optimizers.OptimizerType;
import com.spherecast.agnes.service.HistoryFormatter;
import com.spherecast.agnes.service.PromptLoader;
import com.spherecast.agnes.service.claude.ClaudeClient;
import com.spherecast.agnes.service.claude.JsonExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class OptimizerRouter {

    private static final Logger log = LoggerFactory.getLogger(OptimizerRouter.class);

    private final PromptLoader promptLoader;
    private final ClaudeClient claudeClient;
    private final JsonExtractor jsonExtractor;
    private final HistoryFormatter historyFormatter;

    public OptimizerRouter(PromptLoader promptLoader,
                           ClaudeClient claudeClient,
                           JsonExtractor jsonExtractor,
                           HistoryFormatter historyFormatter) {
        this.promptLoader = promptLoader;
        this.claudeClient = claudeClient;
        this.jsonExtractor = jsonExtractor;
        this.historyFormatter = historyFormatter;
    }

    public RouterDecision route(String userPrompt, List<ChatMessage> history) {
        try {
            String systemPrompt = promptLoader.render("optimizer-router", Map.of(
                    "HISTORY", historyFormatter.format(history),
                    "PROMPT", userPrompt
            ));
            String raw = claudeClient.ask(systemPrompt, userPrompt, history, 0.1);
            RouterDto dto = jsonExtractor.extractJson(raw, RouterDto.class);
            RouterDecision decision = normalize(dto, userPrompt);
            log.info("Router decision: prompt=\"{}\", optimizers={}, scope={}:{}, reasoning={}",
                    userPrompt, decision.optimizers(),
                    decision.scope().type(), decision.scope().value(),
                    decision.reasoning());
            return decision;
        } catch (Exception e) {
            log.warn("Router failed for prompt \"{}\": {} — defaulting to all optimizers", userPrompt, e.getMessage());
            return new RouterDecision(
                    OptimizerType.CANONICAL_ORDER,
                    Scope.all(),
                    "Router failed (" + e.getClass().getSimpleName() + ") — defaulted to all optimizers with ALL scope."
            );
        }
    }

    private RouterDecision normalize(RouterDto dto, String userPrompt) {
        List<OptimizerType> opts = List.of();
        if (dto.optimizers() != null) {
            opts = dto.optimizers().stream()
                    .map(OptimizerType::fromJson)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        }

        if (opts.isEmpty()) {
            log.warn("Router returned zero valid optimizers for prompt: {} — defaulting to all four", userPrompt);
            opts = OptimizerType.CANONICAL_ORDER;
        } else if (opts.size() == OptimizerType.values().length) {
            opts = OptimizerType.CANONICAL_ORDER;
        }

        Scope scope = parseScope(dto.scope());
        return new RouterDecision(opts, scope, dto.reasoning() != null ? dto.reasoning() : "");
    }

    private Scope parseScope(RouterDto.ScopeDto scopeDto) {
        if (scopeDto == null || scopeDto.type() == null) {
            return Scope.all();
        }
        try {
            ScopeType type = ScopeType.valueOf(scopeDto.type().trim().toUpperCase());
            String value = scopeDto.value();
            if (type != ScopeType.ALL && (value == null || value.isBlank())) {
                log.warn("Scope type {} but no value provided — defaulting to ALL", type);
                return Scope.all();
            }
            return new Scope(type, type == ScopeType.ALL ? null : value.trim());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown scope type '{}' — defaulting to ALL", scopeDto.type());
            return Scope.all();
        }
    }
}
