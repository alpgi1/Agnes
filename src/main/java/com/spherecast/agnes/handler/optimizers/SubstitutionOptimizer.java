package com.spherecast.agnes.handler.optimizers;

import com.spherecast.agnes.service.PromptLoader;
import com.spherecast.agnes.service.claude.ClaudeClient;
import com.spherecast.agnes.service.claude.JsonExtractionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SubstitutionOptimizer implements Optimizer {

    private static final Logger log = LoggerFactory.getLogger(SubstitutionOptimizer.class);
    private static final double TEMPERATURE = 0.3;
    private static final int MAX_FINDINGS = 1;

    private final PromptLoader promptLoader;
    private final ClaudeClient claudeClient;

    public SubstitutionOptimizer(PromptLoader promptLoader, ClaudeClient claudeClient) {
        this.promptLoader = promptLoader;
        this.claudeClient = claudeClient;
    }

    @Override
    public OptimizerType type() {
        return OptimizerType.SUBSTITUTION;
    }

    @Override
    public OptimizerResult run(OptimizerContext ctx) {
        if (ctx.data() == null || ctx.data().rows() == null || ctx.data().rows().isEmpty()) {
            log.warn("SubstitutionOptimizer received empty scoped data — returning stub");
            return OptimizerResult.stub(type(), "no portfolio data available");
        }

        String compliance = promptLoader.load("compliance-awareness");
        String systemPrompt = promptLoader.render("optimizer-substitution", Map.of(
                "COMPLIANCE_AWARENESS", compliance,
                "PORTFOLIO_DATA", ctx.data().asPromptString(),
                "USER_PROMPT", ctx.userPrompt() == null ? "" : ctx.userPrompt()
        ));

        try {
            SubstitutionDto dto = claudeClient.askJson(systemPrompt,
                    ctx.userPrompt() == null ? "Identify substitution clusters." : ctx.userPrompt(),
                    SubstitutionDto.class, 1500);
            List<Finding> findings = dto.findings() == null ? List.of() : dto.findings();
            if (findings.size() > MAX_FINDINGS) {
                findings = findings.subList(0, MAX_FINDINGS);
            }
            findings = findings.stream().map(this::ensureDefaults).toList();
            String narrative = dto.narrative() != null ? dto.narrative()
                    : "Substitution analysis complete.";
            log.info("SubstitutionOptimizer produced {} findings", findings.size());
            return new OptimizerResult(type(), findings, narrative, "", false, null, true);
        } catch (JsonExtractionException e) {
            log.warn("SubstitutionOptimizer could not parse Claude response: {}", e.getMessage());
            return OptimizerResult.stub(type(), "model output was not valid JSON");
        } catch (Exception e) {
            log.warn("SubstitutionOptimizer failed: {}", e.getMessage());
            return OptimizerResult.stub(type(), e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private Finding ensureDefaults(Finding f) {
        ComplianceRelevance cr = f.complianceRelevance() != null
                ? f.complianceRelevance() : ComplianceRelevance.empty();
        List<String> derived = f.derivedFrom() != null ? f.derivedFrom() : List.of();
        if (cr == f.complianceRelevance() && derived == f.derivedFrom()) {
            return f;
        }
        return new Finding(f.id(), f.title(), f.summary(), f.rationale(),
                f.affectedSkus(), f.estimatedImpact(), f.confidence(), cr,
                "pending", derived, null, null, List.of());
    }

    public record SubstitutionDto(List<Finding> findings, String narrative) {}
}
