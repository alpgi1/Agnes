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
        OptimizerContext ctx = new OptimizerContext(
                "Find substitution candidates across the portfolio.",
                Scope.all(), data, List.of(), "test-session");

        OptimizerResult result = optimizer.run(ctx);

        assertThat(result.type()).isEqualTo(OptimizerType.SUBSTITUTION);
        assertThat(result.stub()).isFalse();
        assertThat(result.narrative()).isNotBlank();
        if (!result.findings().isEmpty()) {
            Finding first = result.findings().get(0);
            assertThat(first.complianceRelevance()).isNotNull();
            assertThat(first.id()).isNotBlank();
        }
    }

    @Test
    void emptyDataReturnsStub() {
        ScopedData empty = new ScopedData(List.of(), 0, false, "-", "(empty)");
        OptimizerContext ctx = new OptimizerContext(
                "anything", Scope.all(), empty, List.of(), "test-session");

        OptimizerResult result = optimizer.run(ctx);

        assertThat(result.stub()).isTrue();
        assertThat(result.stubReason()).contains("no portfolio data");
    }
}
