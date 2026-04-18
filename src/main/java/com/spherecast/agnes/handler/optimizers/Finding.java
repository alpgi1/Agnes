package com.spherecast.agnes.handler.optimizers;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record Finding(
        @JsonProperty("id") String id,
        @JsonProperty("title") String title,
        @JsonProperty("summary") String summary,
        @JsonProperty("rationale") String rationale,
        @JsonProperty("affected_skus") List<AffectedSku> affectedSkus,
        @JsonProperty("estimated_impact") String estimatedImpact,
        @JsonProperty("confidence") String confidence,
        @JsonProperty("compliance_relevance") ComplianceRelevance complianceRelevance,
        @JsonProperty("compliance_status") String complianceStatus,
        @JsonProperty("derived_from") List<String> derivedFrom,
        @JsonProperty("proposed_replacement") ProposedReplacement proposedReplacement,
        @JsonProperty("redundancy_pair") RedundancyPair redundancyPair,
        @JsonProperty("compliance_evidence") List<EvidenceItem> complianceEvidence
) {
    public record AffectedSku(
            @JsonProperty("company") String company,
            @JsonProperty("product") String product,
            @JsonProperty("ingredient") String ingredient,
            @JsonProperty("note") String note
    ) {}

    public record ProposedReplacement(
            @JsonProperty("ingredient_name") String ingredientName,
            @JsonProperty("short_justification") String shortJustification,
            @JsonProperty("equivalence_class") String equivalenceClass
    ) {}

    public record RedundancyPair(
            @JsonProperty("keep_sku") String keepSku,
            @JsonProperty("remove_sku") String removeSku,
            @JsonProperty("shared_function") String sharedFunction,
            @JsonProperty("keep_rationale") String keepRationale
    ) {}

    public record EvidenceItem(
            @JsonProperty("source_type") String sourceType,
            @JsonProperty("source_ref") String sourceRef,
            @JsonProperty("note") String note,
            @JsonProperty("url") String url
    ) {}

    /** Attach a compliance verdict + evidence to an existing Finding. */
    public Finding withComplianceVerdict(String status, List<EvidenceItem> evidence) {
        return new Finding(id, title, summary, rationale, affectedSkus,
                estimatedImpact, confidence, complianceRelevance,
                status, derivedFrom, proposedReplacement, redundancyPair, evidence);
    }
}
