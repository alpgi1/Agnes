package com.spherecast.agnes.handler;

import com.spherecast.agnes.dto.OptimizeRequest;
import com.spherecast.agnes.dto.OptimizeResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = "sk-ant-.+")
class OptimizeHandlerIT {

    @Autowired
    OptimizeHandler handler;

    @Test
    void explicitSubstitutionProducesReport() {
        OptimizeResponse resp = handler.handle(
                new OptimizeRequest("Find substitution candidates across all companies.", null, null));

        assertThat(resp.markdown()).contains("# Optimization Report");
        assertThat(resp.optimizersRun()).contains("SUBSTITUTION");
        assertThat(resp.complianceStatus()).containsIgnoringCase("pending");
        assertThat(resp.sessionId()).isNotBlank();
        assertThat(resp.durationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void genericRequestRunsAllFourWithStubsForNonSubstitution() {
        OptimizeResponse resp = handler.handle(
                new OptimizeRequest("Optimiere das gesamte Portfolio.", null, null));

        assertThat(resp.optimizersRun())
                .containsExactly("SUBSTITUTION", "CONSOLIDATION", "REFORMULATION", "COMPLEXITY");
        assertThat(resp.markdown()).contains("⏳");
        assertThat(resp.markdown()).contains("Consolidation");
        assertThat(resp.markdown()).contains("Reformulation");
        assertThat(resp.markdown()).contains("Complexity");
    }
}
