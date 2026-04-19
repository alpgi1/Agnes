package com.spherecast.agnes.handler;

import com.spherecast.agnes.handler.optimizers.ComplianceRelevance;
import com.spherecast.agnes.handler.optimizers.Finding;
import com.spherecast.agnes.handler.optimizers.OptimizerResult;
import com.spherecast.agnes.handler.optimizers.ScopedData;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class ResponseComposer {

    public String compose(RouterDecision decision,
                          ScopedData data,
                          List<OptimizerResult> results,
                          String userPrompt,
                          String overallStatus) {
        StringBuilder sb = new StringBuilder();

        // Header block
        sb.append("**Optimization Report**\n");
        String optimizerNames = decision.optimizers() == null ? "—"
                : decision.optimizers().stream().map(Enum::name)
                        .collect(Collectors.joining(" & "));
        sb.append("* **Scope:** ").append(optimizerNames).append(" — ").append(formatScope(decision.scope())).append("\n");
        if (data != null) {
            sb.append("* **Data analyzed:** ").append(data.totalRows());
            if (data.truncated()) sb.append(" (truncated)");
            sb.append(" rows\n");
        }
        sb.append("* **Overall Compliance:** ")
                .append(statusIcon(overallStatus)).append(" ")
                .append(formatStatusLabel(overallStatus));
        appendComplianceSummaryLine(sb, results, overallStatus);
        sb.append("\n\n");

        // Flat finding list — no optimizer section headers
        for (OptimizerResult result : results) {
            if (result.skipped() || result.findings() == null || result.findings().isEmpty()) continue;
            for (Finding f : result.findings()) {
                appendFinding(sb, f);
            }
        }

        return sb.toString().stripTrailing() + "\n";
    }

    // Backward-compatible overload
    public String compose(RouterDecision decision, ScopedData data,
                          List<OptimizerResult> results, String userPrompt) {
        return compose(decision, data, results, userPrompt, "pending");
    }

    private void appendFinding(StringBuilder sb, Finding f) {
        // Finding header: **SUB-001: Title**
        sb.append("**").append(nn(f.id(), "FIND")).append(": ")
                .append(nn(f.title(), "(untitled)")).append("**\n");

        // Observation = summary (+ rationale folded in if different)
        String observation = nn(f.summary(), "");
        if (f.rationale() != null && !f.rationale().isBlank()
                && !f.rationale().equals(f.summary())) {
            observation = observation.isBlank()
                    ? f.rationale()
                    : observation + " " + f.rationale();
        }
        sb.append("* **Observation:** ").append(observation).append("\n");

        // Impact
        if (f.estimatedImpact() != null && !f.estimatedImpact().isBlank()) {
            sb.append("* **Impact:** ").append(f.estimatedImpact()).append("\n");
        }

        // Proposed swap (Reformulation)
        if (f.proposedReplacement() != null) {
            Finding.ProposedReplacement pr = f.proposedReplacement();
            sb.append("* **Proposed swap:** → **").append(pr.ingredientName()).append("**");
            if (pr.equivalenceClass() != null) sb.append(" (").append(pr.equivalenceClass()).append(")");
            if (pr.shortJustification() != null) sb.append(" — ").append(pr.shortJustification());
            sb.append("\n");
        }

        // Redundancy pair (Complexity)
        if (f.redundancyPair() != null) {
            Finding.RedundancyPair rp = f.redundancyPair();
            sb.append("* **Keep:** ").append(nn(rp.keepSku(), "?")).append("\n");
            sb.append("* **Remove:** ").append(nn(rp.removeSku(), "?")).append("\n");
        }

        // Affected SKUs
        if (f.affectedSkus() != null && !f.affectedSkus().isEmpty()) {
            sb.append("* **Affected SKUs (Examples):**\n");
            for (Finding.AffectedSku s : f.affectedSkus()) {
                sb.append("    * ").append(nn(s.company(), "?"))
                        .append(" / ").append(nn(s.product(), "?"))
                        .append(" / ").append(nn(s.ingredient(), "?"));
                if (s.note() != null && !s.note().isBlank()) {
                    sb.append(" _(").append(s.note()).append(")_");
                }
                sb.append("\n");
            }
        }

        // Compliance & Risk Assessment section
        sb.append("\n**Compliance & Risk Assessment**\n");
        appendComplianceSection(sb, f);
        sb.append("\n");
    }

    private void appendComplianceSection(StringBuilder sb, Finding f) {
        String status = f.complianceStatus();
        if (status == null || "pending".equals(status)) {
            sb.append("* **Status:** ⏳ Pending\n");
            return;
        }

        sb.append("* **Status:** ").append(statusIcon(status)).append(" ")
                .append(formatComplianceStatus(status)).append("\n");

        // Flags from ComplianceRelevance
        String flags = buildFlagsLine(f.complianceRelevance());
        sb.append("* **Flags:** ").append(flags.isBlank() ? "None" : flags).append("\n");

        // Map evidence items to 3 semantic sections
        List<Finding.EvidenceItem> evidence = f.complianceEvidence() == null
                ? List.of() : f.complianceEvidence();

        String regulatoryContext = evidence.stream()
                .filter(e -> "eu_regulation".equals(e.sourceType()) || "iherb".equals(e.sourceType()))
                .map(e -> {
                    String note = nn(e.note(), "");
                    String ref = nn(e.sourceRef(), "");
                    return ref.isBlank() ? note : ref + ": " + note;
                })
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(" "));

        String specificRisk = evidence.stream()
                .filter(e -> "claude_reasoning".equals(e.sourceType())
                        && "compliance_checker".equals(e.sourceRef()))
                .map(e -> nn(e.note(), ""))
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(" "));

        String requiredAction = evidence.stream()
                .filter(e -> "pre_filter".equals(e.sourceType()))
                .map(e -> nn(e.note(), ""))
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(" "));

        // Fallback for required action from preFilterFlags
        if (requiredAction.isBlank() && f.complianceRelevance() != null) {
            List<String> pf = f.complianceRelevance().preFilterFlags();
            if (pf != null && !pf.isEmpty()) {
                requiredAction = String.join("; ", pf);
            }
        }

        sb.append("* **Regulatory Context:** ")
                .append(regulatoryContext.isBlank() ? "No specific regulatory context provided." : regulatoryContext)
                .append("\n");
        sb.append("* **Specific Risk:** ")
                .append(specificRisk.isBlank() ? "No specific risk identified." : specificRisk)
                .append("\n");
        sb.append("* **Required Action:** ")
                .append(requiredAction.isBlank() ? "No action required." : requiredAction)
                .append("\n");
    }

    private String buildFlagsLine(ComplianceRelevance cr) {
        if (cr == null) return "";
        List<String> flags = new ArrayList<>();
        if (cr.allergenChanges() != null && !cr.allergenChanges().isEmpty())
            flags.add("allergen-change: " + String.join(", ", cr.allergenChanges()));
        if (cr.animalOriginChanges() != null && !cr.animalOriginChanges().isEmpty())
            flags.add("animal-origin: " + String.join(", ", cr.animalOriginChanges()));
        if (Boolean.TRUE.equals(cr.novelFoodRisk())) flags.add("novel-food-risk");
        if (Boolean.TRUE.equals(cr.changesIngredientChemistry())) flags.add("chemistry-change");
        if (Boolean.TRUE.equals(cr.labelClaimRisk())) {
            String claims = (cr.affectedClaims() == null || cr.affectedClaims().isEmpty())
                    ? "" : ": " + String.join(", ", cr.affectedClaims());
            flags.add("label-claim-risk" + claims);
        }
        return String.join(", ", flags);
    }

    private void appendComplianceSummaryLine(StringBuilder sb,
                                              List<OptimizerResult> results,
                                              String overallStatus) {
        if (results == null || results.isEmpty()) return;
        long total = results.stream()
                .filter(r -> r.findings() != null)
                .mapToLong(r -> r.findings().size())
                .sum();
        long needVerification = results.stream()
                .filter(r -> r.findings() != null)
                .flatMap(r -> r.findings().stream())
                .filter(f -> "uncertain".equals(f.complianceStatus())
                        || "non-compliant".equals(f.complianceStatus()))
                .count();
        long clear = total - needVerification;
        if (total > 0) {
            sb.append(" — ").append(needVerification).append(" finding(s) need verification, ")
                    .append(clear).append(" clear");
        }
    }

    private String formatComplianceStatus(String status) {
        return switch (status) {
            case "compliant" -> "Compliant";
            case "uncertain" -> "Verification Required";
            case "non-compliant" -> "Non-Compliant";
            default -> status;
        };
    }

    private String formatStatusLabel(String status) {
        if (status == null) return "Pending";
        return switch (status) {
            case "compliant" -> "Compliant";
            case "uncertain" -> "Uncertain";
            case "non-compliant" -> "Non-Compliant";
            case "not_applicable" -> "N/A";
            default -> status;
        };
    }

    private String statusIcon(String status) {
        if (status == null) return "⏳";
        return switch (status) {
            case "compliant" -> "✅";
            case "uncertain" -> "⚠️";
            case "non-compliant" -> "❌";
            case "not_applicable" -> "ℹ️";
            default -> "⏳";
        };
    }

    private String formatScope(RouterDecision.Scope scope) {
        if (scope == null) return "ALL";
        String type = scope.type().name().toLowerCase(Locale.ROOT);
        return scope.value() == null ? type : type + " = " + scope.value();
    }

    private String nn(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }
}
