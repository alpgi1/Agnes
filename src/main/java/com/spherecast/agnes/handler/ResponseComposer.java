package com.spherecast.agnes.handler;

import com.spherecast.agnes.handler.optimizers.ComplianceRelevance;
import com.spherecast.agnes.handler.optimizers.Finding;
import com.spherecast.agnes.handler.optimizers.OptimizerResult;
import com.spherecast.agnes.handler.optimizers.OptimizerType;
import com.spherecast.agnes.handler.optimizers.ScopedData;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class ResponseComposer {

    public String compose(RouterDecision decision,
                          ScopedData data,
                          List<OptimizerResult> results,
                          String userPrompt,
                          String overallStatus) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Optimization Report\n\n");

        // Overall compliance top-line
        sb.append("**Overall compliance:** ").append(statusIcon(overallStatus)).append(" ")
                .append(overallStatus != null ? overallStatus : "pending");
        appendComplianceSummaryLine(sb, results, overallStatus);
        sb.append("\n\n");

        sb.append("**Request:** ").append(userPrompt == null ? "(none)" : userPrompt).append("\n\n");
        sb.append("**Scope:** ").append(formatScope(decision.scope())).append("\n");
        sb.append("**Optimizers run:** ")
                .append(decision.optimizers().stream().map(Enum::name).toList())
                .append("\n");
        if (data != null) {
            sb.append("**Data rows:** ").append(data.totalRows());
            if (data.truncated()) sb.append(" (truncated — more exist)");
            sb.append("\n");
        }
        if (decision.reasoning() != null && !decision.reasoning().isBlank()) {
            sb.append("**Router reasoning:** ").append(decision.reasoning()).append("\n");
        }
        sb.append("\n---\n\n");

        for (OptimizerResult result : results) {
            sb.append("## ").append(titleFor(result.optimizer())).append("\n\n");
            if (result.skipped()) {
                sb.append("⏳ _pending_ — ").append(result.skipReason()).append("\n\n");
                continue;
            }
            if (result.narrativeSummary() != null && !result.narrativeSummary().isBlank()) {
                sb.append(result.narrativeSummary()).append("\n\n");
            }
            List<Finding> findings = result.findings() == null ? List.of() : result.findings();
            if (findings.isEmpty()) {
                if (result.narrativeSummary() == null || result.narrativeSummary().isBlank()) {
                    sb.append("_No findings._\n\n");
                }
                continue;
            }
            for (Finding f : findings) {
                appendFinding(sb, f);
            }
        }
        return sb.toString().stripTrailing() + "\n";
    }

    // Backward-compatible overload for tests that don't pass overallStatus
    public String compose(RouterDecision decision, ScopedData data,
                          List<OptimizerResult> results, String userPrompt) {
        return compose(decision, data, results, userPrompt, "pending");
    }

    private void appendFinding(StringBuilder sb, Finding f) {
        sb.append("### ").append(nn(f.id(), "FIND"))
                .append(" — ").append(nn(f.title(), "(untitled)")).append("\n");
        if (f.summary() != null) sb.append(f.summary()).append("\n\n");
        if (f.rationale() != null) sb.append("**Why:** ").append(f.rationale()).append("\n\n");
        if (f.derivedFrom() != null && !f.derivedFrom().isEmpty()) {
            sb.append("**Derived from:** Substitution finding ")
                    .append(String.join(", ", f.derivedFrom())).append("\n\n");
        }
        if (f.proposedReplacement() != null) {
            Finding.ProposedReplacement pr = f.proposedReplacement();
            sb.append("**Proposed swap:** → **").append(pr.ingredientName()).append("**");
            if (pr.equivalenceClass() != null) {
                sb.append(" (").append(pr.equivalenceClass()).append(")");
            }
            sb.append("\n");
            if (pr.shortJustification() != null) {
                sb.append("  _").append(pr.shortJustification()).append("_\n");
            }
            sb.append("\n");
        }
        if (f.redundancyPair() != null) {
            Finding.RedundancyPair rp = f.redundancyPair();
            sb.append("**Shared function:** ").append(nn(rp.sharedFunction(), "unknown")).append("\n");
            sb.append("**Keep:** `").append(nn(rp.keepSku(), "?")).append("`\n");
            sb.append("**Remove:** `").append(nn(rp.removeSku(), "?")).append("`\n");
            if (rp.keepRationale() != null) {
                sb.append("  _").append(rp.keepRationale()).append("_\n");
            }
            sb.append("\n");
        }
        if (f.affectedSkus() != null && !f.affectedSkus().isEmpty()) {
            sb.append("**Affected SKUs:**\n");
            for (Finding.AffectedSku s : f.affectedSkus()) {
                sb.append("- ")
                        .append(nn(s.company(), "?")).append(" / ")
                        .append(nn(s.product(), "?")).append(" / ")
                        .append(nn(s.ingredient(), "?"));
                if (s.note() != null && !s.note().isBlank()) {
                    sb.append(" _(").append(s.note()).append(")_");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }
        if (f.estimatedImpact() != null) sb.append("**Impact:** ").append(f.estimatedImpact()).append("\n\n");
        if (f.confidence() != null) sb.append("**Confidence:** ").append(f.confidence()).append("\n\n");
        appendPreFilterFlags(sb, f.complianceRelevance());

        // Compliance verdict + evidence
        appendComplianceVerdict(sb, f);
    }

    private void appendComplianceVerdict(StringBuilder sb, Finding f) {
        if (f.complianceStatus() == null || "pending".equals(f.complianceStatus())) return;

        sb.append("**Compliance:** ").append(statusIcon(f.complianceStatus()))
                .append(" ").append(f.complianceStatus()).append("\n");

        if (f.complianceEvidence() != null && !f.complianceEvidence().isEmpty()) {
            sb.append("**Evidence:**\n");
            for (Finding.EvidenceItem ev : f.complianceEvidence()) {
                sb.append("- ");
                if (ev.url() != null && !ev.url().isBlank()) {
                    sb.append("[").append(nn(ev.sourceRef(), ev.sourceType())).append("](")
                            .append(ev.url()).append(")");
                } else {
                    sb.append(nn(ev.sourceRef(), ev.sourceType()));
                }
                if (ev.note() != null && !ev.note().isBlank()) {
                    sb.append(" — ").append(ev.note());
                }
                sb.append("\n");
            }
            sb.append("\n");
        }
    }

    private void appendPreFilterFlags(StringBuilder sb, ComplianceRelevance cr) {
        if (cr == null) return;
        List<String> flags = new java.util.ArrayList<>();
        if (cr.allergenChanges() != null && !cr.allergenChanges().isEmpty()) {
            flags.add("allergen-change: " + String.join(", ", cr.allergenChanges()));
        }
        if (cr.animalOriginChanges() != null && !cr.animalOriginChanges().isEmpty()) {
            flags.add("animal-origin-change: " + String.join(", ", cr.animalOriginChanges()));
        }
        if (Boolean.TRUE.equals(cr.novelFoodRisk())) flags.add("novel-food-risk");
        if (Boolean.TRUE.equals(cr.labelClaimRisk())) {
            String claims = (cr.affectedClaims() == null || cr.affectedClaims().isEmpty())
                    ? "" : " (" + String.join(", ", cr.affectedClaims()) + ")";
            flags.add("label-claim-risk" + claims);
        }
        if (Boolean.TRUE.equals(cr.changesIngredientChemistry())) {
            flags.add("changes-ingredient-chemistry");
        }
        if (!flags.isEmpty()) {
            sb.append("**Pre-filter flags:** ").append(String.join("; ", flags)).append("\n\n");
        }
        if (cr.preFilterFlags() != null && !cr.preFilterFlags().isEmpty()) {
            sb.append("**Compliance notes:**\n");
            for (String flag : cr.preFilterFlags()) {
                sb.append("- ").append(flag).append("\n");
            }
            sb.append("\n");
        }
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

    private String titleFor(OptimizerType type) {
        return switch (type) {
            case SUBSTITUTION -> "Substitution";
            case CONSOLIDATION -> "Consolidation";
            case REFORMULATION -> "Reformulation";
            case COMPLEXITY -> "Complexity Reduction";
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
