package com.spherecast.agnes.handler.optimizers;

import org.springframework.stereotype.Component;

@Component
public class ComplexityOptimizer implements Optimizer {

    @Override
    public OptimizerType type() {
        return OptimizerType.COMPLEXITY;
    }

    @Override
    public OptimizerResult run(OptimizerContext ctx) {
        return OptimizerResult.stub(type(), "not yet implemented — planned for Phase 7c");
    }
}
