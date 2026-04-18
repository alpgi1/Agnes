package com.spherecast.agnes.handler;

import com.spherecast.agnes.dto.ChatMessage;
import com.spherecast.agnes.dto.OptimizeRequest;
import com.spherecast.agnes.dto.OptimizeResponse;
import com.spherecast.agnes.handler.optimizers.Finding;
import com.spherecast.agnes.handler.optimizers.OptimizerContext;
import com.spherecast.agnes.handler.optimizers.OptimizerDependencies;
import com.spherecast.agnes.handler.optimizers.OptimizerRegistry;
import com.spherecast.agnes.handler.optimizers.OptimizerResult;
import com.spherecast.agnes.handler.optimizers.ScopedData;
import com.spherecast.agnes.service.ScopedDataLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OptimizeHandler {

    private static final Logger log = LoggerFactory.getLogger(OptimizeHandler.class);
    private static final String COMPLIANCE_PENDING = "pending — ComplianceChecker lands in Phase 9";

    private final OptimizerRouter router;
    private final ScopedDataLoader scopedDataLoader;
    private final OptimizerRegistry registry;
    private final ResponseComposer composer;
    private final OptimizerDependencies dependencies;

    public OptimizeHandler(OptimizerRouter router,
                           ScopedDataLoader scopedDataLoader,
                           OptimizerRegistry registry,
                           ResponseComposer composer,
                           OptimizerDependencies dependencies) {
        this.router = router;
        this.scopedDataLoader = scopedDataLoader;
        this.registry = registry;
        this.composer = composer;
        this.dependencies = dependencies;
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
        ScopedData data = scopedDataLoader.load(decision.scope(), request.prompt());

        // Expand the router's choice into a full execution plan (with forced precursors)
        List<OptimizerDependencies.ExecutionStep> plan = dependencies.plan(decision.optimizers());

        log.info("sessionId={} execution plan: {}", sessionId,
                plan.stream()
                        .map(s -> s.type() + (s.userVisible() ? "" : "(hidden)"))
                        .toList());

        log.info("sessionId={} scope={}:{} data rows={} truncated={}",
                sessionId, decision.scope().type(), decision.scope().value(),
                data.totalRows(), data.truncated());

        // Run optimizers in order, threading priorResults through the context
        OptimizerContext ctx = OptimizerContext.initial(
                request.prompt(), decision.scope(), data, history, sessionId);
        List<OptimizerResult> results = new ArrayList<>();

        for (var step : plan) {
            OptimizerResult result;
            try {
                result = registry.get(step.type()).run(ctx);
            } catch (Exception e) {
                log.warn("sessionId={} optimizer {} threw: {}",
                        sessionId, step.type(), e.toString());
                result = OptimizerResult.stub(step.type(), "Failed: " + e.getMessage());
            }

            // Mark hidden if this optimizer was only a forced precursor
            if (!step.userVisible()) {
                result = result.asHidden();
            }

            results.add(result);
            ctx = ctx.withPriorResult(step.type(), result);
        }

        // Only user-visible results end up in the report and response
        List<OptimizerResult> visibleResults = results.stream()
                .filter(OptimizerResult::userVisible)
                .toList();

        List<Finding> visibleFindings = visibleResults.stream()
                .flatMap(r -> r.findings() == null ? List.<Finding>of().stream() : r.findings().stream())
                .toList();

        String markdown = composer.compose(decision, data, visibleResults, request.prompt());

        return new OptimizeResponse(
                sessionId,
                markdown,
                visibleResults.stream().map(r -> r.optimizer().name()).toList(),
                new OptimizeResponse.ScopeInfo(
                        decision.scope().type().name(),
                        decision.scope().value()
                ),
                decision.reasoning(),
                visibleFindings,
                COMPLIANCE_PENDING,
                System.currentTimeMillis() - start
        );
    }
}
