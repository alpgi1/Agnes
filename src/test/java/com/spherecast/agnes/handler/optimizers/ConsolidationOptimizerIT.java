package com.spherecast.agnes.handler.optimizers;

import com.spherecast.agnes.handler.RouterDecision.Scope;
import com.spherecast.agnes.service.ScopedDataLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = "sk-ant-.+")
class ConsolidationOptimizerIT {

    @Autowired
    ScopedDataLoader scopedDataLoader;

    @Autowired
    SubstitutionOptimizer substitutionOptimizer;

    @Autowired
    ConsolidationOptimizer consolidationOptimizer;

    @Test
    void producesFindingsLinkedToSubstitution() throws Exception {
        ScopedData data = scopedDataLoader.load(Scope.all(), "optimize");
        OptimizerContext ctx = OptimizerContext.initial(
                "Find consolidation opportunities across all companies.",
                Scope.all(), data, List.of(), "test-cons-it");

        // Run substitution first, thread it into the context
        OptimizerResult subResult = substitutionOptimizer.run(ctx);
        assertThat(subResult.findings()).isNotEmpty();

        OptimizerContext ctxWithSub = ctx.withPriorResult(OptimizerType.SUBSTITUTION, subResult);
        OptimizerResult consResult = consolidationOptimizer.run(ctxWithSub);

        assertThat(consResult.skipped()).isFalse();
        assertThat(consResult.narrativeSummary()).isNotBlank();

        if (!consResult.findings().isEmpty()) {
            // Every consolidation finding must derive from at least one substitution finding
            Set<String> subIds = subResult.findings().stream()
                    .map(Finding::id).collect(Collectors.toSet());

            consResult.findings().forEach(f -> {
                assertThat(f.derivedFrom())
                        .as("Consolidation finding %s must have derivedFrom", f.id())
                        .isNotEmpty();
                // Every derivedFrom ID must exist among substitution findings
                assertThat(subIds).containsAll(f.derivedFrom());
                assertThat(f.complianceRelevance()).isNotNull();
            });
        }
    }

    @Test
    void gracefullyHandlesEmptySubstitution() {
        ScopedData data = scopedDataLoader.load(Scope.all(), "optimize");
        OptimizerContext ctx = OptimizerContext.initial(
                "consolidate titanium dioxide",
                Scope.all(), data, List.of(), "test-cons-empty");

        OptimizerResult emptyStub = new OptimizerResult(
                OptimizerType.SUBSTITUTION, List.of(), "no clusters", "",
                false, null, true
        );
        OptimizerContext ctxEmpty = ctx.withPriorResult(OptimizerType.SUBSTITUTION, emptyStub);

        OptimizerResult res = consolidationOptimizer.run(ctxEmpty);
        assertThat(res.skipped()).isFalse();
        assertThat(res.findings()).isEmpty();
        assertThat(res.narrativeSummary()).isNotBlank();
    }
}
