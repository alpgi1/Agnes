package com.spherecast.agnes.handler.optimizers;

import com.spherecast.agnes.service.PromptLoader;
import com.spherecast.agnes.service.claude.ClaudeClient;
import com.spherecast.agnes.service.claude.JsonExtractionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ComplexityOptimizer implements Optimizer {

    private static final Logger log = LoggerFactory.getLogger(ComplexityOptimizer.class);
    private static final double TEMPERATURE = 0.2;
    private static final int MAX_FINDINGS = 10;

    private final PromptLoader promptLoader;
    private final ClaudeClient claudeClient;

    public ComplexityOptimizer(PromptLoader promptLoader, ClaudeClient claudeClient) {
        this.promptLoader = promptLoader;
        this.claudeClient = claudeClient;
    }

    @Override
    public OptimizerType type() {
        return OptimizerType.COMPLEXITY;
    }

    @Override
    public OptimizerResult run(OptimizerContext ctx) {
        if (ctx.data() == null || ctx.data().rows() == null || ctx.data().rows().isEmpty()) {
            return new OptimizerResult(
                    OptimizerType.COMPLEXITY,
                    List.of(),
                    "No portfolio data to analyze for complexity reduction.",
                    "Empty portfolio slice.",
                    false, null, true
            );
        }

        // Build the BOM lookup for safety-net validation
        Map<String, Set<String>> ingredientToBoms = buildIngredientToBomLookup(ctx.data());

        String systemPrompt = promptLoader.render("optimizer-complexity", Map.of(
                "COMPLIANCE_AWARENESS", promptLoader.load("compliance-awareness"),
                "PORTFOLIO_DATA", ctx.data().asPromptString(),
                "USER_PROMPT", ctx.userPrompt() == null ? "" : ctx.userPrompt()
        ));

        try {
            JsonNode json = claudeClient.askJson(systemPrompt,
                    ctx.userPrompt() == null ? "Simplify BOM formulas." : ctx.userPrompt(),
                    ctx.history(), TEMPERATURE);

            List<Finding> findings = parseFindings(json, ingredientToBoms);
            if (findings.size() > MAX_FINDINGS) {
                findings = findings.subList(0, MAX_FINDINGS);
            }

            String narrative = json.path("narrative_summary").asText("");
            String reasoning = json.path("reasoning_trace").asText("");

            log.info("ComplexityOptimizer produced {} findings", findings.size());
            return new OptimizerResult(
                    OptimizerType.COMPLEXITY, findings, narrative, reasoning, false, null, true
            );
        } catch (JsonExtractionException e) {
            log.warn("ComplexityOptimizer could not parse Claude response: {}", e.getMessage());
            return OptimizerResult.stub(type(), "model output was not valid JSON");
        } catch (Exception e) {
            log.warn("ComplexityOptimizer failed: {}", e.getMessage());
            return OptimizerResult.stub(type(), e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Builds a lookup from ingredient slug → set of finished-good SKUs (BOMs)
     * that contain that ingredient. Used for same-BOM validation.
     */
    private Map<String, Set<String>> buildIngredientToBomLookup(ScopedData data) {
        Map<String, Set<String>> map = new HashMap<>();
        for (ScopedData.DenormRow row : data.rows()) {
            if (row.ingredient() == null || row.product() == null) continue;
            String ingredient = row.ingredient().toLowerCase();
            map.computeIfAbsent(ingredient, k -> new HashSet<>()).add(row.product());
        }
        return map;
    }

    private List<Finding> parseFindings(JsonNode json, Map<String, Set<String>> ingredientToBoms) {
        JsonNode findingsNode = json.path("findings");
        if (findingsNode.isMissingNode() || !findingsNode.isArray()) {
            return List.of();
        }

        List<Finding> findings = new ArrayList<>();
        for (JsonNode item : findingsNode) {
            try {
                Finding f = parseSingleFinding(item, ingredientToBoms);
                if (f != null) findings.add(f);
            } catch (Exception e) {
                log.warn("ComplexityOptimizer: skipping malformed finding: {}", e.getMessage());
            }
        }
        return findings;
    }

    private Finding parseSingleFinding(JsonNode item, Map<String, Set<String>> ingredientToBoms) {
        String id = firstNonBlank(item, "finding_id", "id");
        String title = firstNonBlank(item, "title", "canonical_name");
        String summary = item.path("summary").asText(null);
        String rationale = firstNonBlank(item, "rationale", "reasoning");
        String impact = item.path("estimated_impact").asText(null);
        String confidence = item.path("confidence").asText("medium");

        // Safety net (c): Complexity has no dependencies — force derivedFrom empty
        JsonNode dfNode = item.path("derived_from");
        if (dfNode.isArray() && dfNode.size() > 0) {
            log.warn("Complexity finding {} has non-empty derived_from — overriding to empty", id);
        }
        List<String> derivedFrom = List.of();

        // Parse redundancy_pair (required for complexity findings)
        Finding.RedundancyPair redundancyPair = parseRedundancyPair(item);
        if (redundancyPair == null) {
            log.warn("Complexity finding {} missing redundancy_pair — skipping", id);
            return null;
        }

        // Safety net (a): verify keep_sku and remove_sku exist in portfolio data
        Set<String> keepBoms = findBomsForSku(redundancyPair.keepSku(), ingredientToBoms);
        Set<String> removeBoms = findBomsForSku(redundancyPair.removeSku(), ingredientToBoms);
        if (keepBoms.isEmpty()) {
            log.warn("Complexity finding {}: keep_sku '{}' not found in portfolio — skipping",
                    id, redundancyPair.keepSku());
            return null;
        }
        if (removeBoms.isEmpty()) {
            log.warn("Complexity finding {}: remove_sku '{}' not found in portfolio — skipping",
                    id, redundancyPair.removeSku());
            return null;
        }

        // Safety net (b): verify keep_sku and remove_sku belong to the same BOM
        Set<String> sharedBoms = new HashSet<>(keepBoms);
        sharedBoms.retainAll(removeBoms);
        if (sharedBoms.isEmpty()) {
            log.warn("Complexity finding {}: keep_sku '{}' and remove_sku '{}' share no BOM — "
                            + "cross-BOM hallucination detected, skipping",
                    id, redundancyPair.keepSku(), redundancyPair.removeSku());
            return null;
        }

        // affected_skus
        List<Finding.AffectedSku> affectedSkus = new ArrayList<>();
        JsonNode skusNode = item.path("affected_skus");
        if (skusNode.isArray()) {
            for (JsonNode s : skusNode) {
                affectedSkus.add(new Finding.AffectedSku(
                        s.path("company").asText(null),
                        s.path("product").asText(null),
                        s.path("ingredient").asText(null),
                        s.path("note").asText(null)
                ));
            }
        }

        // compliance_relevance
        ComplianceRelevance cr = parseComplianceRelevance(item.path("compliance_relevance"));

        return new Finding(id, title, summary, rationale, affectedSkus,
                impact, confidence, cr, "pending", derivedFrom, null, redundancyPair, List.of());
    }

    private Finding.RedundancyPair parseRedundancyPair(JsonNode item) {
        JsonNode rpNode = item.path("redundancy_pair");
        if (rpNode.isMissingNode() || rpNode.isNull()) return null;

        String keepSku = rpNode.path("keep_sku").asText(null);
        String removeSku = rpNode.path("remove_sku").asText(null);
        if (keepSku == null || keepSku.isBlank() || removeSku == null || removeSku.isBlank()) {
            return null;
        }

        return new Finding.RedundancyPair(
                keepSku,
                removeSku,
                rpNode.path("shared_function").asText("unknown"),
                rpNode.path("keep_rationale").asText(null)
        );
    }

    /**
     * Finds which BOMs (finished goods) contain the given SKU by doing substring
     * matching: if the DenormRow ingredient slug is contained in the SKU string,
     * or vice versa, consider it a match.
     */
    private Set<String> findBomsForSku(String sku, Map<String, Set<String>> ingredientToBoms) {
        if (sku == null) return Set.of();
        String skuLower = sku.toLowerCase();
        Set<String> result = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : ingredientToBoms.entrySet()) {
            String ingredientSlug = entry.getKey();
            if (skuLower.contains(ingredientSlug) || ingredientSlug.contains(skuLower)) {
                result.addAll(entry.getValue());
            }
        }
        return result;
    }

    private ComplianceRelevance parseComplianceRelevance(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return ComplianceRelevance.empty();
        }
        return new ComplianceRelevance(
                jsonStringList(node.path("allergen_changes")),
                jsonStringList(node.path("animal_origin_changes")),
                node.path("novel_food_risk").asBoolean(false),
                node.path("label_claim_risk").asBoolean(false),
                jsonStringList(node.path("affected_claims")),
                jsonStringList(node.path("regulatory_axes")),
                node.path("notes").asText(null),
                node.path("changes_ingredient_chemistry").asBoolean(false),
                jsonStringList(node.path("ingredient_keywords_for_lookup")),
                jsonStringList(node.path("pre_filter_flags"))
        );
    }

    private List<String> jsonStringList(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<String> list = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual()) list.add(item.asText());
        }
        return list;
    }

    private String firstNonBlank(JsonNode obj, String... keys) {
        for (String key : keys) {
            String val = obj.path(key).asText(null);
            if (val != null && !val.isBlank()) return val;
        }
        return null;
    }
}
