package com.spherecast.agnes.handler;

import com.spherecast.agnes.dto.OptimizeRequest;
import com.spherecast.agnes.dto.OptimizeResponse;
import com.spherecast.agnes.handler.optimizers.Finding;
import com.spherecast.agnes.handler.optimizers.OptimizerType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;
import java.util.stream.Collectors;

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
    void genericRequestRunsAllFourAllReal() {
        OptimizeResponse resp = handler.handle(
                new OptimizeRequest("Optimiere das gesamte Portfolio.", null, null));

        assertThat(resp.optimizersRun())
                .containsExactly("SUBSTITUTION", "CONSOLIDATION", "REFORMULATION", "COMPLEXITY");
        // All four are now real — no more stubs
        assertThat(resp.markdown()).contains("Substitution");
        assertThat(resp.markdown()).contains("Consolidation");
        assertThat(resp.markdown()).contains("Reformulation");
        assertThat(resp.markdown()).contains("Complexity");
    }

    @Test
    void consolidationAloneForcesHiddenSubstitution() {
        OptimizeResponse resp = handler.handle(
                new OptimizeRequest("Bündele den Einkauf über alle Companies.", null, null));

        // Router may pick CONSOLIDATION only or all four — both are acceptable.
        if (resp.optimizersRun().contains("CONSOLIDATION")
                && !resp.optimizersRun().contains("SUBSTITUTION")) {
            // Substitution ran hidden — its findings should NOT be in the response
            assertThat(resp.findings())
                    .allMatch(f -> f.id() == null
                            || !f.id().toUpperCase().startsWith("SUB"));
            assertThat(resp.markdown()).doesNotContain("## Substitution");
        }

        // Consolidation findings (if any) must link back via derivedFrom
        resp.findings().stream()
                .filter(f -> f.id() != null && f.id().toLowerCase().startsWith("cons"))
                .forEach(f -> assertThat(f.derivedFrom())
                        .as("Consolidation finding %s must have derivedFrom", f.id())
                        .isNotEmpty());
    }

    @Test
    void bothRequestedShowsBoth() {
        OptimizeResponse resp = handler.handle(
                new OptimizeRequest(
                        "Find substitution clusters AND consolidation opportunities.",
                        null, null));

        // Both should appear in the visible output
        assertThat(resp.markdown()).contains("Substitution");
        assertThat(resp.markdown()).contains("Consolidation");

        // Consolidation findings link to substitution findings that ARE in the response
        Set<String> subIds = resp.findings().stream()
                .filter(f -> f.id() != null && f.id().toUpperCase().startsWith("SUB"))
                .map(Finding::id)
                .collect(Collectors.toSet());

        resp.findings().stream()
                .filter(f -> f.id() != null && f.id().toLowerCase().startsWith("cons"))
                .forEach(f -> {
                    assertThat(f.derivedFrom()).isNotEmpty();
                    // All derivedFrom IDs should be among the visible substitution findings
                    if (!subIds.isEmpty()) {
                        assertThat(subIds).containsAll(f.derivedFrom());
                    }
                });
    }

    @Test
    void reformulationAloneForcesHiddenSubstitution() {
        OptimizeResponse resp = handler.handle(
                new OptimizeRequest(
                        "Reformuliere das Portfolio, finde günstigere Alternativen.", null, null));

        // If router picks REFORMULATION only, substitution runs hidden
        if (resp.optimizersRun().contains("REFORMULATION")
                && !resp.optimizersRun().contains("SUBSTITUTION")) {
            assertThat(resp.markdown()).doesNotContain("## Substitution");
        }

        // All reformulation findings must flag chemistry change
        resp.findings().stream()
                .filter(f -> f.id() != null && f.id().toLowerCase().startsWith("refor"))
                .forEach(f -> {
                    assertThat(f.derivedFrom()).isNotEmpty();
                    assertThat(f.complianceRelevance().changesIngredientChemistry()).isTrue();
                });
    }

    @Test
    void threeOptimizersChainThroughSubstitution() {
        OptimizeResponse resp = handler.handle(
                new OptimizeRequest(
                        "Analysiere Substitutionen, Konsolidierung und Reformulierung.",
                        null, null));

        assertThat(resp.markdown()).contains("Substitution");
        assertThat(resp.markdown()).contains("Consolidation");
        assertThat(resp.markdown()).contains("Reformulation");

        // Both Consolidation and Reformulation findings link back to Substitution
        Set<String> subIds = resp.findings().stream()
                .filter(f -> f.id() != null && f.id().toUpperCase().startsWith("SUB"))
                .map(Finding::id)
                .collect(Collectors.toSet());

        resp.findings().stream()
                .filter(f -> f.id() != null
                        && (f.id().toLowerCase().startsWith("cons")
                        || f.id().toLowerCase().startsWith("refor")))
                .forEach(f -> {
                    assertThat(f.derivedFrom()).isNotEmpty();
                    if (!subIds.isEmpty()) {
                        assertThat(subIds).containsAll(f.derivedFrom());
                    }
                });
    }

    @Test
    void complexityHasNoDependency() {
        OptimizeResponse resp = handler.handle(
                new OptimizeRequest(
                        "Identifiziere redundante Zutaten in den BOMs.", null, null));

        // If router picks COMPLEXITY only, no hidden substitution runs
        if (resp.optimizersRun().size() == 1
                && resp.optimizersRun().contains("COMPLEXITY")) {
            assertThat(resp.markdown()).doesNotContain("## Substitution");
            assertThat(resp.markdown()).doesNotContain("## Consolidation");
            assertThat(resp.markdown()).doesNotContain("## Reformulation");
        }

        // Every complexity finding has empty derivedFrom and a redundancyPair
        resp.findings().stream()
                .filter(f -> f.id() != null && f.id().toLowerCase().startsWith("cpx"))
                .forEach(f -> {
                    assertThat(f.derivedFrom()).isEmpty();
                    assertThat(f.redundancyPair()).isNotNull();
                });
    }

    @Test
    void allFourOptimizersProduceDistinctSections() {
        OptimizeResponse resp = handler.handle(
                new OptimizeRequest(
                        "Optimiere alles: Substitution, Konsolidierung, Reformulierung und Vereinfachung.",
                        null, null));

        assertThat(resp.optimizersRun()).containsExactly(
                "SUBSTITUTION", "CONSOLIDATION", "REFORMULATION", "COMPLEXITY");

        // All four markdown sections present
        assertThat(resp.markdown()).contains("## Substitution");
        assertThat(resp.markdown()).contains("## Consolidation");
        assertThat(resp.markdown()).contains("## Reformulation");
        assertThat(resp.markdown()).contains("## Complexity");
    }
}
