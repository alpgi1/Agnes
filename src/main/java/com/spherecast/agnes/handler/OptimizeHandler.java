package com.spherecast.agnes.handler;

import com.spherecast.agnes.dto.ChatMessage;
import com.spherecast.agnes.dto.OptimizeRequest;
import com.spherecast.agnes.dto.OptimizeResponse;
import com.spherecast.agnes.handler.optimizers.Finding;
import com.spherecast.agnes.handler.optimizers.Optimizer;
import com.spherecast.agnes.handler.optimizers.OptimizerContext;
import com.spherecast.agnes.handler.optimizers.OptimizerRegistry;
import com.spherecast.agnes.handler.optimizers.OptimizerResult;
import com.spherecast.agnes.handler.optimizers.OptimizerType;
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

    public OptimizeHandler(OptimizerRouter router,
                           ScopedDataLoader scopedDataLoader,
                           OptimizerRegistry registry,
                           ResponseComposer composer) {
        this.router = router;
        this.scopedDataLoader = scopedDataLoader;
        this.registry = registry;
        this.composer = composer;
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
        log.info("sessionId={} optimizers={} scope={}:{}", sessionId,
                decision.optimizers(), decision.scope().type(), decision.scope().value());

        ScopedData data = scopedDataLoader.load(decision.scope(), request.prompt());
        log.info("sessionId={} scoped data rows={} truncated={}",
                sessionId, data.totalRows(), data.truncated());

        OptimizerContext ctx = new OptimizerContext(
                request.prompt(), decision.scope(), data, history, sessionId);

        List<OptimizerResult> results = new ArrayList<>();
        List<Finding> allFindings = new ArrayList<>();
        for (OptimizerType type : decision.optimizers()) {
            OptimizerResult result;
            try {
                Optimizer opt = registry.get(type);
                result = opt.run(ctx);
            } catch (Exception e) {
                log.warn("sessionId={} optimizer {} threw — recording stub. {}",
                        sessionId, type, e.getMessage());
                result = OptimizerResult.stub(type, e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            results.add(result);
            if (!result.stub() && result.findings() != null) {
                allFindings.addAll(result.findings());
            }
        }

        String markdown = composer.compose(decision, data, results, request.prompt());

        return new OptimizeResponse(
                sessionId,
                markdown,
                decision.optimizers().stream().map(Enum::name).toList(),
                new OptimizeResponse.ScopeInfo(
                        decision.scope().type().name(),
                        decision.scope().value()
                ),
                decision.reasoning(),
                allFindings,
                COMPLIANCE_PENDING,
                System.currentTimeMillis() - start
        );
    }
}
