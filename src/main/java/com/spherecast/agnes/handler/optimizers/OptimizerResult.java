package com.spherecast.agnes.handler.optimizers;

import java.util.List;

public record OptimizerResult(
        OptimizerType optimizer,
        List<Finding> findings,
        String narrativeSummary,
        String reasoningTrace,
        boolean skipped,
        String skipReason,
        boolean userVisible
) {
    public static OptimizerResult stub(OptimizerType type, String reason) {
        return new OptimizerResult(type, List.of(),
                "(" + type + " not yet implemented)", "", true, reason, true);
    }

    public OptimizerResult asHidden() {
        return new OptimizerResult(optimizer, findings, narrativeSummary,
                reasoningTrace, skipped, skipReason, false);
    }
}
