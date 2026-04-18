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
import com.spherecast.agnes.service.compliance.ComplianceChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class OptimizeHandler {

    private static final Logger log = LoggerFactory.getLogger(OptimizeHandler.class);

    private final OptimizerRouter router;
    private final ScopedDataLoader scopedDataLoader;
    private final OptimizerRegistry registry;
    private final ResponseComposer composer;
    private final OptimizerDependencies dependencies;
    private final ComplianceChecker complianceChecker;

    public OptimizeHandler(OptimizerRouter router,
                           ScopedDataLoader scopedDataLoader,
                           OptimizerRegistry registry,
                           ResponseComposer composer,
                           OptimizerDependencies dependencies,
                           ComplianceChecker complianceChecker) {
        this.router = router;
        this.scopedDataLoader = scopedDataLoader;
        this.registry = registry;
        this.composer = composer;
        this.dependencies = dependencies;
        this.complianceChecker = complianceChecker;
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

        // Expand the router's choice into parallel waves (topological sort)
        List<List<OptimizerDependencies.ExecutionStep>> waves = dependencies.waves(decision.optimizers());

        log.info("sessionId={} execution waves: {}", sessionId,
                waves.stream()
                        .map(w -> "[" + w.stream()
                                .map(s -> s.type() + (s.userVisible() ? "" : "(hidden)"))
                                .toList() + "]")
                        .toList());

        log.info("sessionId={} scope={}:{} data rows={} truncated={}",
                sessionId, decision.scope().type(), decision.scope().value(),
                data.totalRows(), data.truncated());

        // Run each wave in parallel, threading priorResults into the next wave
        OptimizerContext ctx = OptimizerContext.initial(
                request.prompt(), decision.scope(), data, history, sessionId);
        List<OptimizerResult> results = new ArrayList<>();

        ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();
        try {
            for (List<OptimizerDependencies.ExecutionStep> wave : waves) {
                final OptimizerContext ctxSnap = ctx;

                List<CompletableFuture<OptimizerResult>> futures = wave.stream()
                        .map(step -> CompletableFuture.supplyAsync(() -> {
                            OptimizerResult r;
                            try {
                                r = registry.get(step.type()).run(ctxSnap);
                            } catch (Exception e) {
                                log.warn("sessionId={} optimizer {} threw: {}",
                                        sessionId, step.type(), e.toString());
                                r = OptimizerResult.stub(step.type(), "Failed: " + e.getMessage());
                            }
                            return step.userVisible() ? r : r.asHidden();
                        }, exec))
                        .toList();

                List<OptimizerResult> waveResults = futures.stream()
                        .map(CompletableFuture::join)
                        .toList();

                results.addAll(waveResults);
                for (int i = 0; i < wave.size(); i++) {
                    ctx = ctx.withPriorResult(wave.get(i).type(), waveResults.get(i));
                }
            }
        } finally {
            exec.shutdown();
        }

        // Only user-visible results end up in the report and response
        List<OptimizerResult> visibleResults = results.stream()
                .filter(OptimizerResult::userVisible)
                .toList();

        // Collect all visible findings for compliance verification
        List<Finding> rawFindings = visibleResults.stream()
                .flatMap(r -> r.findings() == null ? List.<Finding>of().stream() : r.findings().stream())
                .toList();

        // Run compliance checker (replaces "pending" with real verdicts)
        List<Finding> verifiedFindings = complianceChecker.verify(rawFindings);

        // Rebuild visibleResults so each contains verified findings
        Map<String, Finding> verifiedById = verifiedFindings.stream()
                .filter(f -> f.id() != null)
                .collect(Collectors.toMap(Finding::id, f -> f, (a, b) -> a));

        List<OptimizerResult> verifiedResults = visibleResults.stream()
                .map(r -> new OptimizerResult(
                        r.optimizer(),
                        r.findings() == null ? List.of() :
                                r.findings().stream()
                                        .map(f -> f.id() != null ? verifiedById.getOrDefault(f.id(), f) : f)
                                        .toList(),
                        r.narrativeSummary(),
                        r.reasoningTrace(),
                        r.skipped(),
                        r.skipReason(),
                        r.userVisible()
                ))
                .toList();

        String overallStatus = complianceChecker.aggregateStatus(verifiedFindings);
        String markdown = composer.compose(decision, data, verifiedResults, request.prompt(), overallStatus);

        return new OptimizeResponse(
                sessionId,
                markdown,
                visibleResults.stream().map(r -> r.optimizer().name()).toList(),
                new OptimizeResponse.ScopeInfo(
                        decision.scope().type().name(),
                        decision.scope().value()
                ),
                decision.reasoning(),
                verifiedFindings,
                overallStatus,
                System.currentTimeMillis() - start
        );
    }
}
