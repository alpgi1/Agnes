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
                          String userPrompt) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Optimization Report\n\n");

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
            sb.append("## ").append(titleFor(result.type())).append("\n\n");
            if (result.stub()) {
                sb.append("⏳ _pending_ — ").append(result.stubReason()).append("\n\n");
                continue;
            }
            if (result.narrative() != null && !result.narrative().isBlank()) {
                sb.append(result.narrative()).append("\n\n");
            }
            List<Finding> findings = result.findings() == null ? List.of() : result.findings();
            if (findings.isEmpty()) {
                sb.append("_No findings._\n\n");
                continue;
            }
            for (Finding f : findings) {
                appendFinding(sb, f);
            }
        }
        return sb.toString().stripTrailing() + "\n";
    }

    private void appendFinding(StringBuilder sb, Finding f) {
        sb.append("### ").append(nn(f.id(), "FIND"))
                .append(" — ").append(nn(f.title(), "(untitled)")).append("\n");
        if (f.summary() != null) sb.append(f.summary()).append("\n\n");
        if (f.rationale() != null) sb.append("**Why:** ").append(f.rationale()).append("\n\n");
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
        if (!flags.isEmpty()) {
            sb.append("**Pre-filter flags:** ").append(String.join("; ", flags)).append("\n\n");
        }
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
