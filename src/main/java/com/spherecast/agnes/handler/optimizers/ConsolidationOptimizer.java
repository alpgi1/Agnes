package com.spherecast.agnes.handler.optimizers;

import com.spherecast.agnes.service.PromptLoader;
import com.spherecast.agnes.service.claude.ClaudeClient;
import com.spherecast.agnes.service.claude.JsonExtractionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ConsolidationOptimizer implements Optimizer {

    private static final Logger log = LoggerFactory.getLogger(ConsolidationOptimizer.class);
    private static final double TEMPERATURE = 0.3;
    private static final int MAX_FINDINGS = 10;

    private final PromptLoader promptLoader;
    private final ClaudeClient claudeClient;
    private final ClusterFormatter clusterFormatter;

    public ConsolidationOptimizer(PromptLoader promptLoader, ClaudeClient claudeClient,
                                  ClusterFormatter clusterFormatter) {
        this.promptLoader = promptLoader;
        this.claudeClient = claudeClient;
        this.clusterFormatter = clusterFormatter;
    }

    @Override
    public OptimizerType type() {
        return OptimizerType.CONSOLIDATION;
    }

    @Override
    public OptimizerResult run(OptimizerContext ctx) {
        OptimizerResult substitutionResult = ctx.priorResults().get(OptimizerType.SUBSTITUTION);

        if (substitutionResult == null || substitutionResult.skipped()
                || substitutionResult.findings().isEmpty()) {
            String reason = substitutionResult == null
                    ? "Substitution did not run."
                    : substitutionResult.skipped()
                    ? "Substitution was skipped: " + substitutionResult.skipReason()
                    : "Substitution produced no findings.";
            return new OptimizerResult(
                    OptimizerType.CONSOLIDATION,
                    List.of(),
                    "No substitution clusters available — consolidation requires at least one "
                            + "cluster spanning multiple companies. " + reason,
                    reason,
                    false, null, true
            );
        }

        String clustersBlock = clusterFormatter.format(substitutionResult.findings(), ctx.data());

        String systemPrompt = promptLoader.render("optimizer-consolidation", Map.of(
                "COMPLIANCE_AWARENESS", promptLoader.load("compliance-awareness"),
                "PORTFOLIO_DATA", ctx.data().asPromptString(),
                "SUBSTITUTION_CLUSTERS", clustersBlock,
                "USER_PROMPT", ctx.userPrompt() == null ? "" : ctx.userPrompt()
        ));

        try {
            JsonNode json = claudeClient.askJson(systemPrompt,
                    ctx.userPrompt() == null ? "Consolidate purchasing." : ctx.userPrompt(),
                    ctx.history(), TEMPERATURE);

            List<Finding> findings = parseFindings(json);
            if (findings.size() > MAX_FINDINGS) {
                findings = findings.subList(0, MAX_FINDINGS);
            }

            String narrative = json.path("narrative_summary").asText("");
            String reasoning = json.path("reasoning_trace").asText("");

            log.info("ConsolidationOptimizer produced {} findings", findings.size());
            return new OptimizerResult(
                    OptimizerType.CONSOLIDATION, findings, narrative, reasoning, false, null, true
            );
        } catch (JsonExtractionException e) {
            log.warn("ConsolidationOptimizer could not parse Claude response: {}", e.getMessage());
            return OptimizerResult.stub(type(), "model output was not valid JSON");
        } catch (Exception e) {
            log.warn("ConsolidationOptimizer failed: {}", e.getMessage());
            return OptimizerResult.stub(type(), e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }



    /**
     * Defensively parses the Claude JSON response into Finding records.
     * Maps consolidation-specific fields to the existing Finding structure.
     */
    private List<Finding> parseFindings(JsonNode json) {
        JsonNode findingsNode = json.path("findings");
        if (findingsNode.isMissingNode() || !findingsNode.isArray()) {
            return List.of();
        }

        List<Finding> findings = new ArrayList<>();
        for (JsonNode item : findingsNode) {
            try {
                Finding f = parseSingleFinding(item);
                findings.add(f);
            } catch (Exception e) {
                log.warn("ConsolidationOptimizer: skipping malformed finding: {}", e.getMessage());
            }
        }
        return findings;
    }

    private Finding parseSingleFinding(JsonNode item) {
        String id = firstNonBlank(item, "finding_id", "id");
        String title = firstNonBlank(item, "title", "type");
        String summary = item.path("summary").asText(null);
        String rationale = firstNonBlank(item, "rationale", "reasoning");
        String impact = item.path("estimated_impact").asText(null);
        String confidence = item.path("confidence").asText("medium");

        // derived_from: mandatory for consolidation findings
        List<String> derivedFrom = new ArrayList<>();
        JsonNode dfNode = item.path("derived_from");
        if (dfNode.isArray()) {
            for (JsonNode d : dfNode) {
                if (d.isTextual() && !d.asText().isBlank()) {
                    derivedFrom.add(d.asText());
                }
            }
        }
        if (derivedFrom.isEmpty()) {
            log.warn("Consolidation finding {} has empty derived_from", id);
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
                impact, confidence, cr, derivedFrom, null);
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
