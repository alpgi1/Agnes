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
class ReformulationOptimizerIT {

    @Autowired
    ScopedDataLoader scopedDataLoader;

    @Autowired
    SubstitutionOptimizer substitutionOptimizer;

    @Autowired
    ReformulationOptimizer reformulationOptimizer;

    @Test
    void producesReformulationFindings() throws Exception {
        ScopedData data = scopedDataLoader.load(Scope.all(), "find cheaper ingredient alternatives");
        OptimizerContext ctx = OptimizerContext.initial(
                "Find cheaper ingredient alternatives across the portfolio.",
                Scope.all(), data, List.of(), "test-refor-it");

        OptimizerResult subResult = substitutionOptimizer.run(ctx);
        assertThat(subResult.findings()).isNotEmpty();

        OptimizerContext ctxWithSub = ctx.withPriorResult(OptimizerType.SUBSTITUTION, subResult);
        OptimizerResult reformResult = reformulationOptimizer.run(ctxWithSub);

        assertThat(reformResult.skipped()).isFalse();
        assertThat(reformResult.narrativeSummary()).isNotBlank();

        if (!reformResult.findings().isEmpty()) {
            reformResult.findings().forEach(f -> {
                // Every reformulation finding must derive from substitution
                assertThat(f.derivedFrom())
                        .as("Reformulation finding %s must have derivedFrom", f.id())
                        .isNotEmpty();

                // Compliance: changes_ingredient_chemistry must be true
                assertThat(f.complianceRelevance()).isNotNull();
                assertThat(f.complianceRelevance().changesIngredientChemistry())
                        .as("Reformulation finding %s must flag changesIngredientChemistry", f.id())
                        .isTrue();

                // proposed_replacement should be present
                if (f.proposedReplacement() != null) {
                    assertThat(f.proposedReplacement().ingredientName()).isNotBlank();
                }
            });
        }
    }

    @Test
    void gracefullyHandlesEmptySubstitution() {
        ScopedData data = scopedDataLoader.load(Scope.all(), "optimize");
        OptimizerContext ctx = OptimizerContext.initial(
                "reformulate titanium dioxide",
                Scope.all(), data, List.of(), "test-refor-empty");

        OptimizerResult emptyResult = new OptimizerResult(
                OptimizerType.SUBSTITUTION, List.of(), "no clusters", "",
                false, null, true
        );
        OptimizerContext ctxEmpty = ctx.withPriorResult(OptimizerType.SUBSTITUTION, emptyResult);

        OptimizerResult res = reformulationOptimizer.run(ctxEmpty);
        assertThat(res.skipped()).isFalse();
        assertThat(res.findings()).isEmpty();
        assertThat(res.narrativeSummary()).isNotBlank();
    }
}
