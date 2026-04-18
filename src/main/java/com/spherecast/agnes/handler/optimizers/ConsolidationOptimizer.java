package com.spherecast.agnes.handler.optimizers;

import org.springframework.stereotype.Component;

@Component
public class ConsolidationOptimizer implements Optimizer {

    @Override
    public OptimizerType type() {
        return OptimizerType.CONSOLIDATION;
    }

    @Override
    public OptimizerResult run(OptimizerContext ctx) {
        return OptimizerResult.stub(type(), "not yet implemented — planned for Phase 7");
    }
}
