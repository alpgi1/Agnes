package com.spherecast.agnes.handler.optimizers;

public interface Optimizer {
    OptimizerType type();

    OptimizerResult run(OptimizerContext ctx);
}
