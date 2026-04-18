package com.spherecast.agnes.handler.optimizers;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.List;

public enum OptimizerType {
    SUBSTITUTION,
    CONSOLIDATION,
    REFORMULATION,
    COMPLEXITY;

    public static final List<OptimizerType> CANONICAL_ORDER =
            List.of(SUBSTITUTION, CONSOLIDATION, REFORMULATION, COMPLEXITY);

    @JsonCreator
    public static OptimizerType fromJson(String value) {
        if (value == null) return null;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
