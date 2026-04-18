package com.spherecast.agnes.handler.optimizers;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OptimizerDependenciesTest {

    private final OptimizerDependencies deps = new OptimizerDependencies();

    @Test
    void consolidationAloneForcesSubstitutionFirst() {
        var plan = deps.plan(List.of(OptimizerType.CONSOLIDATION));
        assertThat(plan).hasSize(2);
        assertThat(plan.get(0).type()).isEqualTo(OptimizerType.SUBSTITUTION);
        assertThat(plan.get(0).userVisible()).isFalse();
        assertThat(plan.get(1).type()).isEqualTo(OptimizerType.CONSOLIDATION);
        assertThat(plan.get(1).userVisible()).isTrue();
    }

    @Test
    void bothRequestedKeepsSubstitutionVisible() {
        var plan = deps.plan(List.of(OptimizerType.SUBSTITUTION, OptimizerType.CONSOLIDATION));
        assertThat(plan).hasSize(2);
        assertThat(plan.get(0).type()).isEqualTo(OptimizerType.SUBSTITUTION);
        assertThat(plan.get(0).userVisible()).isTrue();
        assertThat(plan.get(1).type()).isEqualTo(OptimizerType.CONSOLIDATION);
        assertThat(plan.get(1).userVisible()).isTrue();
    }

    @Test
    void substitutionAloneHasNoExpansion() {
        var plan = deps.plan(List.of(OptimizerType.SUBSTITUTION));
        assertThat(plan).hasSize(1);
        assertThat(plan.get(0).type()).isEqualTo(OptimizerType.SUBSTITUTION);
        assertThat(plan.get(0).userVisible()).isTrue();
    }

    @Test
    void allFourPreservesCanonicalOrder() {
        var plan = deps.plan(List.of(
                OptimizerType.COMPLEXITY, OptimizerType.CONSOLIDATION,
                OptimizerType.REFORMULATION, OptimizerType.SUBSTITUTION
        ));
        assertThat(plan.stream().map(OptimizerDependencies.ExecutionStep::type).toList())
                .containsExactly(OptimizerType.SUBSTITUTION, OptimizerType.CONSOLIDATION,
                        OptimizerType.REFORMULATION, OptimizerType.COMPLEXITY);
        assertThat(plan).allMatch(OptimizerDependencies.ExecutionStep::userVisible);
    }

    @Test
    void reformulationAloneForcesSubstitutionFirst() {
        var plan = deps.plan(List.of(OptimizerType.REFORMULATION));
        assertThat(plan).hasSize(2);
        assertThat(plan.get(0).type()).isEqualTo(OptimizerType.SUBSTITUTION);
        assertThat(plan.get(0).userVisible()).isFalse();
        assertThat(plan.get(1).type()).isEqualTo(OptimizerType.REFORMULATION);
        assertThat(plan.get(1).userVisible()).isTrue();
    }

    @Test
    void consolidationAndReformulationShareSubstitutionPrecursor() {
        var plan = deps.plan(List.of(OptimizerType.CONSOLIDATION, OptimizerType.REFORMULATION));
        assertThat(plan).hasSize(3);
        assertThat(plan.get(0).type()).isEqualTo(OptimizerType.SUBSTITUTION);
        assertThat(plan.get(0).userVisible()).isFalse();
        assertThat(plan.get(1).type()).isEqualTo(OptimizerType.CONSOLIDATION);
        assertThat(plan.get(2).type()).isEqualTo(OptimizerType.REFORMULATION);
    }
}
