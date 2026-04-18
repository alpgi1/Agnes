package com.spherecast.agnes.handler.optimizers;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class OptimizerRegistry {

    private final Map<OptimizerType, Optimizer> byType = new EnumMap<>(OptimizerType.class);

    public OptimizerRegistry(List<Optimizer> optimizers) {
        for (Optimizer o : optimizers) {
            Optimizer previous = byType.put(o.type(), o);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate Optimizer bean for type " + o.type()
                                + ": " + previous.getClass().getName()
                                + " and " + o.getClass().getName());
            }
        }
    }

    public Optimizer get(OptimizerType type) {
        Optimizer o = byType.get(type);
        if (o == null) {
            throw new IllegalStateException("No Optimizer registered for type " + type);
        }
        return o;
    }

    public boolean has(OptimizerType type) {
        return byType.containsKey(type);
    }
}
