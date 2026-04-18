package com.spherecast.agnes.service.compliance;

import com.spherecast.agnes.handler.optimizers.ComplianceRelevance;
import com.spherecast.agnes.handler.optimizers.Finding;
import com.spherecast.agnes.service.PromptLoader;
import com.spherecast.agnes.service.claude.ClaudeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ComplianceChecker {

    private static final Logger log = LoggerFactory.getLogger(ComplianceChecker.class);
    private static final Set<String> VALID_STATUSES = Set.of("compliant", "uncertain", "non-compliant");

    private final ComplianceLookupService lookupService;
    private final IHerbClient iherbClient;
    private final PromptLoader promptLoader;
    private final ClaudeClient claudeClient;

    public ComplianceChecker(ComplianceLookupService lookupService,
                             IHerbClient iherbClient,
                             PromptLoader promptLoader,
                             ClaudeClient claudeClient) {
        this.lookupService = lookupService;
        this.iherbClient = iherbClient;
        this.promptLoader = promptLoader;
        this.claudeClient = claudeClient;
    }

    /**
     * Verify a list of findings. Returns the same findings with updated
     * complianceStatus and complianceEvidence. Never throws.
     */
    public List<Finding> verify(List<Finding> findings) {
        if (findings == null || findings.isEmpty()) return List.of();

        try {
            // 1. Build per-finding legal context
            Map<String, List<ComplianceLookupService.LookupResult>> legalContext = new LinkedHashMap<>();
            for (Finding f : findings) {
                String fid = f.id() != null ? f.id() : "unknown";
                legalContext.put(fid, lookupService.lookupForFinding(f.complianceRelevance()));
            }

            // 2. Collect unique keywords for iHerb lookup
            Set<String> uniqueKeywords = findings.stream()
                    .filter(f -> f.complianceRelevance() != null)
                    .flatMap(f -> f.complianceRelevance().ingredientKeywordsForLookup() == null
                            ? java.util.stream.Stream.empty()
                            : f.complianceRelevance().ingredientKeywordsForLookup().stream())
                    .collect(Collectors.toSet());

            // 3. Batch iHerb lookup
            Map<String, IHerbClient.IHerbSearchResult> iherbMap =
                    uniqueKeywords.isEmpty() ? Map.of() : iherbClient.searchAll(uniqueKeywords);

            // 4. Build prompt blocks
            String findingsBlock = buildFindingsBlock(findings);
            String legalBlock = buildLegalBlock(findings, legalContext);
            String iherbBlock = buildIHerbBlock(findings, iherbMap);

            // 5. Call Claude
            String system = promptLoader.render("compliance-checker", Map.of(
                    "FINDINGS_BLOCK", findingsBlock,
                    "LEGAL_CONTEXT_BLOCK", legalBlock,
                    "IHERB_EVIDENCE_BLOCK", iherbBlock
            ));

            JsonNode response = claudeClient.askJson(system,
                    "Verify all findings.", List.of(), 0.2);

            // 6. Parse verdicts
            Map<String, VerdictDto> verdictMap = parseVerdicts(response);

            // 7. Apply verdicts to findings
            List<Finding> result = new ArrayList<>();
            for (Finding f : findings) {
                String fid = f.id() != null ? f.id() : "unknown";
                VerdictDto verdict = verdictMap.get(fid);
                if (verdict != null) {
                    String status = VALID_STATUSES.contains(verdict.status)
                            ? verdict.status : "uncertain";
                    if (!VALID_STATUSES.contains(verdict.status)) {
                        log.warn("ComplianceChecker: invalid status '{}' for {} — coercing to uncertain",
                                verdict.status, fid);
                    }
                    result.add(f.withComplianceVerdict(status, verdict.evidence));
                } else {
                    log.warn("ComplianceChecker: no verdict for finding {} — defaulting to uncertain", fid);
                    result.add(f.withComplianceVerdict("uncertain", List.of(
                            new Finding.EvidenceItem("claude_reasoning", "checker",
                                    "Compliance checker did not return a verdict for this finding.", null)
                    )));
                }
            }

            log.info("ComplianceChecker verified {} findings", result.size());
            return result;

        } catch (Exception e) {
            log.error("ComplianceChecker failed entirely: {}", e.toString());
            return findings.stream()
                    .map(f -> f.withComplianceVerdict("uncertain", List.of(
                            new Finding.EvidenceItem("claude_reasoning", "checker_unavailable",
                                    "Compliance verification could not be completed: "
                                            + e.getClass().getSimpleName(), null)
                    )))
                    .toList();
        }
    }

    /** Aggregate finding-level verdicts into an overall status. */
    public String aggregateStatus(List<Finding> findings) {
        if (findings == null || findings.isEmpty()) return "not_applicable";
        boolean anyNonCompliant = findings.stream()
                .anyMatch(f -> "non-compliant".equals(f.complianceStatus()));
        if (anyNonCompliant) return "non-compliant";
        boolean anyUncertain = findings.stream()
                .anyMatch(f -> "uncertain".equals(f.complianceStatus()));
        if (anyUncertain) return "uncertain";
        return "compliant";
    }

    // ---- Prompt block builders ----

    private String buildFindingsBlock(List<Finding> findings) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < findings.size(); i++) {
            Finding f = findings.get(i);
            sb.append("  {\n");
            sb.append("    \"finding_id\": \"").append(esc(f.id())).append("\",\n");
            sb.append("    \"title\": \"").append(esc(f.title())).append("\",\n");
            sb.append("    \"summary\": \"").append(esc(f.summary())).append("\",\n");
            sb.append("    \"rationale\": \"").append(esc(f.rationale())).append("\",\n");
            sb.append("    \"estimated_impact\": \"").append(esc(f.estimatedImpact())).append("\",\n");
            appendComplianceRelevance(sb, f.complianceRelevance());
            if (f.proposedReplacement() != null) {
                sb.append("    \"proposed_replacement\": {");
                sb.append("\"ingredient_name\": \"").append(esc(f.proposedReplacement().ingredientName())).append("\",");
                sb.append("\"equivalence_class\": \"").append(esc(f.proposedReplacement().equivalenceClass())).append("\"");
                sb.append("},\n");
            }
            if (f.redundancyPair() != null) {
                sb.append("    \"redundancy_pair\": {");
                sb.append("\"keep_sku\": \"").append(esc(f.redundancyPair().keepSku())).append("\",");
                sb.append("\"remove_sku\": \"").append(esc(f.redundancyPair().removeSku())).append("\",");
                sb.append("\"shared_function\": \"").append(esc(f.redundancyPair().sharedFunction())).append("\"");
                sb.append("},\n");
            }
            sb.append("    \"confidence\": \"").append(esc(f.confidence())).append("\"\n");
            sb.append("  }");
            if (i < findings.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]");
        return sb.toString();
    }

    private void appendComplianceRelevance(StringBuilder sb, ComplianceRelevance cr) {
        if (cr == null) {
            sb.append("    \"compliance_relevance\": {},\n");
            return;
        }
        sb.append("    \"compliance_relevance\": {");
        sb.append("\"allergen_changes\": ").append(jsonList(cr.allergenChanges())).append(",");
        sb.append("\"animal_origin_changes\": ").append(jsonList(cr.animalOriginChanges())).append(",");
        sb.append("\"novel_food_risk\": ").append(Boolean.TRUE.equals(cr.novelFoodRisk())).append(",");
        sb.append("\"label_claim_risk\": ").append(Boolean.TRUE.equals(cr.labelClaimRisk())).append(",");
        sb.append("\"affected_claims\": ").append(jsonList(cr.affectedClaims())).append(",");
        sb.append("\"changes_ingredient_chemistry\": ").append(Boolean.TRUE.equals(cr.changesIngredientChemistry()));
        if (cr.preFilterFlags() != null && !cr.preFilterFlags().isEmpty()) {
            sb.append(",\"pre_filter_flags\": ").append(jsonList(cr.preFilterFlags()));
        }
        sb.append("},\n");
    }

    private String buildLegalBlock(List<Finding> findings,
                                   Map<String, List<ComplianceLookupService.LookupResult>> legalContext) {
        StringBuilder sb = new StringBuilder();
        for (Finding f : findings) {
            String fid = f.id() != null ? f.id() : "unknown";
            sb.append("[Finding ").append(fid).append("]\n");
            List<ComplianceLookupService.LookupResult> results = legalContext.getOrDefault(fid, List.of());
            if (results.isEmpty()) {
                sb.append("  (no matching legal context)\n");
            } else {
                for (ComplianceLookupService.LookupResult lr : results) {
                    sb.append("  - ").append(lr.sourceRef()).append(" (").append(lr.summary()).append("): ");
                    String body = lr.body();
                    if (body != null && body.length() > 500) body = body.substring(0, 500) + "...";
                    sb.append(body != null ? body : "").append("\n");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String buildIHerbBlock(List<Finding> findings,
                                   Map<String, IHerbClient.IHerbSearchResult> iherbMap) {
        StringBuilder sb = new StringBuilder();
        for (Finding f : findings) {
            String fid = f.id() != null ? f.id() : "unknown";
            sb.append("[Finding ").append(fid).append("]\n");
            List<String> keywords = f.complianceRelevance() != null
                    && f.complianceRelevance().ingredientKeywordsForLookup() != null
                    ? f.complianceRelevance().ingredientKeywordsForLookup()
                    : List.of();
            if (keywords.isEmpty()) {
                sb.append("  (no ingredient keywords for iHerb lookup)\n");
            } else {
                for (String kw : keywords) {
                    IHerbClient.IHerbSearchResult result = iherbMap.get(kw.toLowerCase().trim());
                    if (result == null) {
                        sb.append("  Keyword \"").append(kw).append("\": no data\n");
                        continue;
                    }
                    sb.append("  Keyword \"").append(kw).append("\"");
                    if (result.fromStub()) {
                        sb.append(" (stub data, reason: ").append(result.stubReason()).append(")");
                    }
                    sb.append(":\n");
                    for (IHerbClient.IHerbProduct p : result.products()) {
                        sb.append("    - ").append(p.title()).append(" — certifications: ")
                                .append(String.join(", ", p.certifications()));
                        if (p.ingredientSource() != null) {
                            sb.append("; source: ").append(p.ingredientSource());
                        }
                        if (p.url() != null) {
                            sb.append(" [").append(p.url()).append("]");
                        }
                        sb.append("\n");
                    }
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ---- Verdict parsing ----

    private record VerdictDto(String status, String reasoning,
                              List<Finding.EvidenceItem> evidence,
                              List<String> blockingIssues) {}

    private Map<String, VerdictDto> parseVerdicts(JsonNode response) {
        Map<String, VerdictDto> map = new LinkedHashMap<>();
        JsonNode verdicts = response.path("verdicts");
        if (!verdicts.isArray()) return map;

        for (JsonNode v : verdicts) {
            String findingId = v.path("finding_id").asText(null);
            if (findingId == null) continue;

            String status = v.path("status").asText("uncertain");
            String reasoning = v.path("reasoning").asText(null);

            List<Finding.EvidenceItem> evidence = new ArrayList<>();
            JsonNode evNode = v.path("evidence");
            if (evNode.isArray()) {
                for (JsonNode e : evNode) {
                    evidence.add(new Finding.EvidenceItem(
                            e.path("source_type").asText("claude_reasoning"),
                            e.path("source_ref").asText(null),
                            e.path("note").asText(null),
                            e.path("url").asText(null)
                    ));
                }
            }
            // Add reasoning as evidence item
            if (reasoning != null && !reasoning.isBlank()) {
                evidence.add(new Finding.EvidenceItem(
                        "claude_reasoning", "compliance_checker", reasoning, null));
            }

            List<String> blockingIssues = new ArrayList<>();
            JsonNode biNode = v.path("blocking_issues");
            if (biNode.isArray()) {
                for (JsonNode bi : biNode) {
                    if (bi.isTextual()) blockingIssues.add(bi.asText());
                }
            }

            map.put(findingId, new VerdictDto(status, reasoning, evidence, blockingIssues));
        }
        return map;
    }

    // ---- Helpers ----

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", "");
    }

    private String jsonList(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        return "[" + list.stream().map(s -> "\"" + esc(s) + "\"").collect(Collectors.joining(",")) + "]";
    }
}
