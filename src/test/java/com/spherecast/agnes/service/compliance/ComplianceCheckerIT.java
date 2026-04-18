package com.spherecast.agnes.service.compliance;

import com.spherecast.agnes.handler.optimizers.ComplianceRelevance;
import com.spherecast.agnes.handler.optimizers.Finding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = "sk-ant-.+")
class ComplianceCheckerIT {

    @Autowired
    ComplianceChecker checker;

    @Test
    void substitutionFindingGetsVerdict() {
        var finding = sampleSubstitutionFinding();
        var results = checker.verify(List.of(finding));

        assertThat(results).hasSize(1);
        var verified = results.get(0);
        assertThat(verified.complianceStatus()).isIn("compliant", "uncertain", "non-compliant");
        assertThat(verified.complianceEvidence()).isNotEmpty();
    }

    @Test
    void multipleFindings() {
        var f1 = sampleSubstitutionFinding();
        var f2 = sampleReformulationFinding();
        var results = checker.verify(List.of(f1, f2));

        assertThat(results).hasSize(2);
        results.forEach(f -> {
            assertThat(f.complianceStatus()).isIn("compliant", "uncertain", "non-compliant");
            assertThat(f.complianceEvidence()).isNotEmpty();
        });
    }

    @Test
    void aggregateStatusFollowsRules() {
        var f1 = sampleSubstitutionFinding().withComplianceVerdict("compliant", List.of());
        var f2 = sampleSubstitutionFinding().withComplianceVerdict("uncertain", List.of());
        var f3 = sampleSubstitutionFinding().withComplianceVerdict("non-compliant", List.of());

        assertThat(checker.aggregateStatus(List.of())).isEqualTo("not_applicable");
        assertThat(checker.aggregateStatus(List.of(f1))).isEqualTo("compliant");
        assertThat(checker.aggregateStatus(List.of(f1, f2))).isEqualTo("uncertain");
        assertThat(checker.aggregateStatus(List.of(f1, f2, f3))).isEqualTo("non-compliant");
    }

    @Test
    void emptyFindingsReturnsEmpty() {
        var results = checker.verify(List.of());
        assertThat(results).isEmpty();
    }

    private Finding sampleSubstitutionFinding() {
        return new Finding("sub-test-001", "D3 cluster normalization",
                "Normalize vitamin D3 names across suppliers.",
                "Multiple naming conventions for the same ingredient.",
                List.of(new Finding.AffectedSku("BioVita", "FG-001", "vitamin-d3", "test")),
                "minor", "high",
                new ComplianceRelevance(List.of(), List.of("lanolin"), false, false,
                        List.of(), List.of(), null, false,
                        List.of("vitamin d3", "cholecalciferol"), List.of("verify D3 source")),
                "pending", List.of(), null, null, List.of());
    }

    private Finding sampleReformulationFinding() {
        return new Finding("ref-test-001", "MgO → Mg glycinate",
                "Replace magnesium oxide with magnesium glycinate.",
                "Higher bioavailability.",
                List.of(new Finding.AffectedSku("NutriCorp", "FG-002", "mg-oxide", "test")),
                "significant", "medium",
                new ComplianceRelevance(List.of(), List.of(), false, true,
                        List.of("bioavailability claim"), List.of(), null, true,
                        List.of("magnesium oxide", "magnesium glycinate"),
                        List.of("chemistry change", "verify bioequivalence")),
                "pending", List.of("sub-test-001"),
                new Finding.ProposedReplacement("magnesium glycinate",
                        "Higher absorption", "magnesium salts"),
                null, List.of());
    }
}
