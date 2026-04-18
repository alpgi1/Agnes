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
        @JsonProperty("derived_from") List<String> derivedFrom,
        @JsonProperty("proposed_replacement") ProposedReplacement proposedReplacement,
        @JsonProperty("redundancy_pair") RedundancyPair redundancyPair
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
}
