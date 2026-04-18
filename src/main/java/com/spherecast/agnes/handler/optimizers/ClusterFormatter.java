package com.spherecast.agnes.handler.optimizers;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared utility for formatting substitution clusters into prompt text.
 * Used by ConsolidationOptimizer and ReformulationOptimizer.
 */
@Component
public class ClusterFormatter {

    /**
     * Renders substitution findings as a structured block for downstream prompts,
     * enriched with supplier information from the portfolio data.
     */
    public String format(List<Finding> substitutionFindings, ScopedData data) {
        Map<String, Set<String>> supplierLookup = buildSupplierLookup(data);

        StringBuilder sb = new StringBuilder();
        for (Finding f : substitutionFindings) {
            sb.append("[Cluster ").append(f.id() != null ? f.id() : "?").append("] ");
            sb.append(f.title() != null ? f.title() : "(untitled)").append("\n");
            if (f.summary() != null) {
                sb.append("  Summary: ").append(f.summary()).append("\n");
            }

            if (f.affectedSkus() != null && !f.affectedSkus().isEmpty()) {
                sb.append("  SKUs:\n");
                for (Finding.AffectedSku sku : f.affectedSkus()) {
                    sb.append("    - ").append(sku.ingredient() != null ? sku.ingredient() : "?");
                    sb.append(" (").append(sku.company() != null ? sku.company() : "?").append(")");

                    Set<String> suppliers = findSuppliers(sku, supplierLookup);
                    if (!suppliers.isEmpty()) {
                        sb.append(" — suppliers: ").append(String.join(", ", suppliers));
                    }
                    sb.append("\n");
                }
            }

            sb.append("\n");
        }
        return sb.toString();
    }

    private Map<String, Set<String>> buildSupplierLookup(ScopedData data) {
        Map<String, Set<String>> map = new HashMap<>();
        if (data == null || data.rows() == null) return map;

        for (ScopedData.DenormRow row : data.rows()) {
            if (row.supplier() == null || row.supplier().isBlank()) continue;

            String ingredient = row.ingredient() != null ? row.ingredient().toLowerCase() : "";
            String company = row.company() != null ? row.company().toLowerCase() : "";
            String key = company + "|" + ingredient;
            map.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(row.supplier());
            map.computeIfAbsent(ingredient, k -> new LinkedHashSet<>()).add(row.supplier());
        }
        return map;
    }

    private Set<String> findSuppliers(Finding.AffectedSku sku, Map<String, Set<String>> lookup) {
        String company = sku.company() != null ? sku.company().toLowerCase() : "";
        String ingredient = sku.ingredient() != null ? sku.ingredient().toLowerCase() : "";

        // Try exact match with company + ingredient
        Set<String> result = lookup.get(company + "|" + ingredient);
        if (result != null && !result.isEmpty()) return result;

        // Try matching by checking if any lookup key's ingredient is contained in the SKU
        Set<String> found = new LinkedHashSet<>();
        for (Map.Entry<String, Set<String>> entry : lookup.entrySet()) {
            String key = entry.getKey();
            if (!key.contains("|")) continue;
            String[] parts = key.split("\\|", 2);
            if (parts[0].equals(company) && ingredient.contains(parts[1]) && !parts[1].isEmpty()) {
                found.addAll(entry.getValue());
            }
        }
        if (!found.isEmpty()) return found;

        // Fallback: ingredient-only lookup (any company)
        for (Map.Entry<String, Set<String>> entry : lookup.entrySet()) {
            if (entry.getKey().contains("|")) continue;
            if (ingredient.contains(entry.getKey()) && !entry.getKey().isEmpty()) {
                found.addAll(entry.getValue());
            }
        }
        return found;
    }
}
