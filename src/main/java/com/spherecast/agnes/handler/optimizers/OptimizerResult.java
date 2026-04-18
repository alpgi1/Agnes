package com.spherecast.agnes.handler.optimizers;

import java.util.List;

public record OptimizerResult(
        OptimizerType type,
        List<Finding> findings,
        String narrative,
        boolean stub,
        String stubReason
) {
    public static OptimizerResult stub(OptimizerType type, String reason) {
        return new OptimizerResult(type, List.of(), null, true, reason);
    }
}
