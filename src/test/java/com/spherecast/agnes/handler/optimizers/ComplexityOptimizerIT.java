package com.spherecast.agnes.handler.optimizers;

import com.spherecast.agnes.handler.RouterDecision.Scope;
import com.spherecast.agnes.service.ScopedDataLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = "sk-ant-.+")
class ComplexityOptimizerIT {

    @Autowired
    ScopedDataLoader scopedDataLoader;

    @Autowired
    ComplexityOptimizer complexityOptimizer;

    @Test
    void producesComplexityFindings() throws Exception {
        ScopedData data = scopedDataLoader.load(Scope.all(), "simplify the portfolio's formulas");
        OptimizerContext ctx = OptimizerContext.initial(
                "Identify redundant ingredients in the BOMs.",
                Scope.all(), data, List.of(), "test-cpx-it");

        OptimizerResult result = complexityOptimizer.run(ctx);

        assertThat(result.skipped()).isFalse();
        assertThat(result.narrativeSummary()).isNotBlank();

        if (!result.findings().isEmpty()) {
            result.findings().forEach(f -> {
                // Complexity has no deps — derivedFrom must be empty
                assertThat(f.derivedFrom())
                        .as("Complexity finding %s must have empty derivedFrom", f.id())
                        .isEmpty();

                // Must have a redundancy pair
                assertThat(f.redundancyPair())
                        .as("Complexity finding %s must have redundancyPair", f.id())
                        .isNotNull();
                assertThat(f.redundancyPair().keepSku()).isNotBlank();
                assertThat(f.redundancyPair().removeSku()).isNotBlank();
                assertThat(f.redundancyPair().sharedFunction()).isNotBlank();

                // Compliance relevance should be present
                assertThat(f.complianceRelevance()).isNotNull();
            });
        }
    }

    @Test
    void keepAndRemoveSkusBelongToSameBom() {
        ScopedData data = scopedDataLoader.load(Scope.all(), "simplify formulas");
        OptimizerContext ctx = OptimizerContext.initial(
                "Find BOM complexity reduction opportunities.",
                Scope.all(), data, List.of(), "test-cpx-bom");

        OptimizerResult result = complexityOptimizer.run(ctx);

        // Build a map: ingredient slug → set of finished goods (BOMs)
        Map<String, Set<String>> ingredientToBoms = new HashMap<>();
        for (ScopedData.DenormRow row : data.rows()) {
            if (row.ingredient() == null || row.product() == null) continue;
            ingredientToBoms.computeIfAbsent(row.ingredient().toLowerCase(), k -> new HashSet<>())
                    .add(row.product());
        }

        // For each finding, verify keep and remove SKUs share at least one BOM
        result.findings().forEach(f -> {
            Finding.RedundancyPair pair = f.redundancyPair();
            assertThat(pair).isNotNull();

            Set<String> keepBoms = findBoms(pair.keepSku(), ingredientToBoms);
            Set<String> removeBoms = findBoms(pair.removeSku(), ingredientToBoms);
            Set<String> shared = new HashSet<>(keepBoms);
            shared.retainAll(removeBoms);

            assertThat(shared)
                    .as("keep '%s' and remove '%s' SKUs must share at least one BOM",
                            pair.keepSku(), pair.removeSku())
                    .isNotEmpty();
        });
    }

    private Set<String> findBoms(String sku, Map<String, Set<String>> ingredientToBoms) {
        if (sku == null) return Set.of();
        String skuLower = sku.toLowerCase();
        Set<String> result = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : ingredientToBoms.entrySet()) {
            if (skuLower.contains(entry.getKey()) || entry.getKey().contains(skuLower)) {
                result.addAll(entry.getValue());
            }
        }
        return result;
    }
}
