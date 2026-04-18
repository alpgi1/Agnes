package com.spherecast.agnes.handler.optimizers;

import org.springframework.stereotype.Component;

@Component
public class ReformulationOptimizer implements Optimizer {

    @Override
    public OptimizerType type() {
        return OptimizerType.REFORMULATION;
    }

    @Override
    public OptimizerResult run(OptimizerContext ctx) {
        return OptimizerResult.stub(type(), "not yet implemented — planned for Phase 8");
    }
}
