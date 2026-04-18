package com.spherecast.agnes.handler.optimizers;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ComplianceRelevance(
        @JsonProperty("allergen_changes") List<String> allergenChanges,
        @JsonProperty("animal_origin_changes") List<String> animalOriginChanges,
        @JsonProperty("novel_food_risk") Boolean novelFoodRisk,
        @JsonProperty("label_claim_risk") Boolean labelClaimRisk,
        @JsonProperty("affected_claims") List<String> affectedClaims,
        @JsonProperty("regulatory_axes") List<String> regulatoryAxes,
        @JsonProperty("notes") String notes,
        @JsonProperty("changes_ingredient_chemistry") Boolean changesIngredientChemistry,
        @JsonProperty("ingredient_keywords_for_lookup") List<String> ingredientKeywordsForLookup,
        @JsonProperty("pre_filter_flags") List<String> preFilterFlags
) {
    public static ComplianceRelevance empty() {
        return new ComplianceRelevance(
                List.of(), List.of(), false, false, List.of(), List.of(), null,
                false, List.of(), List.of());
    }
}
