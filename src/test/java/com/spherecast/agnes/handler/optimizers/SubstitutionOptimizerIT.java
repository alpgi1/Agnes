package com.spherecast.agnes.handler.optimizers;

import com.spherecast.agnes.handler.RouterDecision.Scope;
import com.spherecast.agnes.service.ScopedDataLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = "sk-ant-.+")
class SubstitutionOptimizerIT {

    @Autowired
    SubstitutionOptimizer optimizer;

    @Autowired
    ScopedDataLoader scopedDataLoader;

    @Test
    void runsAgainstAllPortfolioAndReturnsFindings() {
        ScopedData data = scopedDataLoader.load(Scope.all(), "find substitution clusters");
        OptimizerContext ctx = OptimizerContext.initial(
                "Find substitution candidates across the portfolio.",
                Scope.all(), data, List.of(), "test-session");

        OptimizerResult result = optimizer.run(ctx);

        assertThat(result.optimizer()).isEqualTo(OptimizerType.SUBSTITUTION);
        assertThat(result.skipped()).isFalse();
        assertThat(result.narrativeSummary()).isNotBlank();
        if (!result.findings().isEmpty()) {
            Finding first = result.findings().get(0);
            assertThat(first.complianceRelevance()).isNotNull();
            assertThat(first.id()).isNotBlank();
            assertThat(first.derivedFrom()).isNotNull();
        }
    }

    @Test
    void emptyDataReturnsStub() {
        ScopedData empty = new ScopedData(List.of(), 0, false, "-", "(empty)");
        OptimizerContext ctx = OptimizerContext.initial(
                "anything", Scope.all(), empty, List.of(), "test-session");

        OptimizerResult result = optimizer.run(ctx);

        assertThat(result.skipped()).isTrue();
        assertThat(result.skipReason()).contains("no portfolio data");
    }
}
