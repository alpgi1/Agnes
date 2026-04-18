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
public class ReformulationOptimizer implements Optimizer {

    private static final Logger log = LoggerFactory.getLogger(ReformulationOptimizer.class);
    private static final double TEMPERATURE = 0.4;
    private static final int MAX_FINDINGS = 10;

    private final PromptLoader promptLoader;
    private final ClaudeClient claudeClient;
    private final ClusterFormatter clusterFormatter;

    public ReformulationOptimizer(PromptLoader promptLoader, ClaudeClient claudeClient,
                                   ClusterFormatter clusterFormatter) {
        this.promptLoader = promptLoader;
        this.claudeClient = claudeClient;
        this.clusterFormatter = clusterFormatter;
    }

    @Override
    public OptimizerType type() {
        return OptimizerType.REFORMULATION;
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
                    OptimizerType.REFORMULATION,
                    List.of(),
                    "No substitution clusters available — reformulation operates on "
                            + "ingredient clusters. " + reason,
                    reason,
                    false, null, true
            );
        }

        String clustersBlock = clusterFormatter.format(substitutionResult.findings(), ctx.data());

        String systemPrompt = promptLoader.render("optimizer-reformulation", Map.of(
                "COMPLIANCE_AWARENESS", promptLoader.load("compliance-awareness"),
                "PORTFOLIO_DATA", ctx.data().asPromptString(),
                "SUBSTITUTION_CLUSTERS", clustersBlock,
                "USER_PROMPT", ctx.userPrompt() == null ? "" : ctx.userPrompt()
        ));

        try {
            JsonNode json = claudeClient.askJson(systemPrompt,
                    ctx.userPrompt() == null ? "Find reformulation opportunities." : ctx.userPrompt(),
                    ctx.history(), TEMPERATURE);

            List<Finding> findings = parseFindings(json);
            if (findings.size() > MAX_FINDINGS) {
                findings = findings.subList(0, MAX_FINDINGS);
            }

            String narrative = json.path("narrative_summary").asText("");
            String reasoning = json.path("reasoning_trace").asText("");

            log.info("ReformulationOptimizer produced {} findings", findings.size());
            return new OptimizerResult(
                    OptimizerType.REFORMULATION, findings, narrative, reasoning, false, null, true
            );
        } catch (JsonExtractionException e) {
            log.warn("ReformulationOptimizer could not parse Claude response: {}", e.getMessage());
            return OptimizerResult.stub(type(), "model output was not valid JSON");
        } catch (Exception e) {
            log.warn("ReformulationOptimizer failed: {}", e.getMessage());
            return OptimizerResult.stub(type(), e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

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
                log.warn("ReformulationOptimizer: skipping malformed finding: {}", e.getMessage());
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

        // derived_from: mandatory
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
            log.warn("Reformulation finding {} has empty derived_from", id);
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

        // proposed_replacement
        Finding.ProposedReplacement replacement = parseProposedReplacement(item);
        String type = item.path("type").asText(null);
        if (replacement == null && "reformulation_viable".equals(type)) {
            log.warn("Reformulation finding {} typed '{}' has no proposed_replacement — skipping", id, type);
            throw new IllegalArgumentException("reformulation_viable finding missing proposed_replacement");
        }

        // compliance_relevance — enforce changes_ingredient_chemistry = true
        ComplianceRelevance cr = parseComplianceRelevance(item.path("compliance_relevance"));
        if ("reformulation_viable".equals(type) && !Boolean.TRUE.equals(cr.changesIngredientChemistry())) {
            log.warn("Reformulation finding {} has changes_ingredient_chemistry=false on a viable "
                    + "reformulation — overriding to true (safety net)", id);
            cr = new ComplianceRelevance(
                    cr.allergenChanges(), cr.animalOriginChanges(),
                    cr.novelFoodRisk(), cr.labelClaimRisk(),
                    cr.affectedClaims(), cr.regulatoryAxes(), cr.notes(),
                    true, cr.ingredientKeywordsForLookup(), cr.preFilterFlags()
            );
        }

        return new Finding(id, title, summary, rationale, affectedSkus,
                impact, confidence, cr, "pending", derivedFrom, replacement, null, List.of());
    }

    private Finding.ProposedReplacement parseProposedReplacement(JsonNode item) {
        JsonNode prNode = item.path("proposed_replacement");
        if (prNode.isMissingNode() || prNode.isNull()) return null;

        String name = prNode.path("ingredient_name").asText(null);
        if (name == null || name.isBlank()) return null;

        return new Finding.ProposedReplacement(
                name,
                prNode.path("short_justification").asText(null),
                prNode.path("equivalence_class").asText(null)
        );
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
