package com.spherecast.agnes.handler.optimizers;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class OptimizerDependencies {

    private static final Map<OptimizerType, List<OptimizerType>> REQUIRES = Map.of(
            OptimizerType.CONSOLIDATION, List.of(OptimizerType.SUBSTITUTION),
            OptimizerType.REFORMULATION, List.of(OptimizerType.SUBSTITUTION)
            // Phase 7c: COMPLEXITY has no dependencies
    );

    /** Returns the direct prerequisites for an optimizer, empty if none. */
    public List<OptimizerType> requirements(OptimizerType type) {
        return REQUIRES.getOrDefault(type, List.of());
    }

    /**
     * Given the optimizers the router chose, return the full execution plan:
     * includes all required precursors, in canonical order, with a flag marking
     * which were user-requested (visible) vs. forced (hidden).
     */
    public List<ExecutionStep> plan(List<OptimizerType> userRequested) {
        Set<OptimizerType> userRequestedSet = new LinkedHashSet<>(userRequested);
        Set<OptimizerType> allNeeded = new LinkedHashSet<>(userRequestedSet);

        // Transitively pull in all prerequisites
        boolean changed = true;
        while (changed) {
            changed = false;
            for (OptimizerType t : new LinkedHashSet<>(allNeeded)) {
                for (OptimizerType req : requirements(t)) {
                    if (allNeeded.add(req)) changed = true;
                }
            }
        }

        // Order by canonical order
        return OptimizerType.CANONICAL_ORDER.stream()
                .filter(allNeeded::contains)
                .map(t -> new ExecutionStep(t, userRequestedSet.contains(t)))
                .toList();
    }

    public record ExecutionStep(OptimizerType type, boolean userVisible) {}
}
