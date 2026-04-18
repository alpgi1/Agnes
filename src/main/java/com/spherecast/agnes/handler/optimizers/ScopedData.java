package com.spherecast.agnes.handler.optimizers;

import java.util.List;

public record ScopedData(
        List<DenormRow> rows,
        int totalRows,
        boolean truncated,
        String sqlUsed,
        String asPromptString
) {
    public record DenormRow(
            String company,
            String product,
            String ingredient,
            String category,
            String supplier,
            String country,
            Double percentage,
            String notes
    ) {}
}
