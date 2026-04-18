package com.spherecast.agnes.handler;

import com.spherecast.agnes.handler.optimizers.OptimizerType;

import java.util.List;

public record RouterDecision(
        List<OptimizerType> optimizers,
        Scope scope,
        String reasoning
) {
    public record Scope(ScopeType type, String value) {

        public enum ScopeType { ALL, COMPANY, PRODUCT, INGREDIENT }

        public static Scope all() {
            return new Scope(ScopeType.ALL, null);
        }
    }
}
