package com.spherecast.agnes.handler;

import com.spherecast.agnes.dto.ChatMessage;
import com.spherecast.agnes.dto.OptimizeRequest;
import com.spherecast.agnes.dto.OptimizeResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OptimizeHandler {

    private final OptimizerRouter router;

    public OptimizeHandler(OptimizerRouter router) {
        this.router = router;
    }

    public OptimizeResponse handle(OptimizeRequest request) {
        long start = System.currentTimeMillis();
        String sessionId = request.sessionId() != null
                ? request.sessionId()
                : UUID.randomUUID().toString();
        List<ChatMessage> history = request.history() != null
                ? request.history()
                : List.of();

        RouterDecision decision = router.route(request.prompt(), history);

        List<String> optimizerNames = decision.optimizers().stream()
                .map(Enum::name)
                .toList();

        return new OptimizeResponse(
                sessionId,
                buildStubMarkdown(decision),
                optimizerNames,
                new OptimizeResponse.ScopeInfo(
                        decision.scope().type().name(),
                        decision.scope().value()
                ),
                decision.reasoning(),
                System.currentTimeMillis() - start
        );
    }

    private String buildStubMarkdown(RouterDecision decision) {
        return """
                ### Routing Decision (Phase 5 stub)

                **Optimizers to run:** %s
                **Scope:** %s%s
                **Reasoning:** %s

                _The actual optimizer pipeline will be implemented in Phase 6+._
                """.formatted(
                decision.optimizers().stream().map(Enum::name).collect(Collectors.joining(", ")),
                decision.scope().type(),
                decision.scope().value() != null ? " (" + decision.scope().value() + ")" : "",
                decision.reasoning()
        );
    }
}
