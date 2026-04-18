package com.spherecast.agnes.service.compliance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.spherecast.agnes.handler.optimizers.ComplianceRelevance;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ComplianceLookupServiceTest {

    @Autowired
    ComplianceLookupService lookup;

    @Test
    void loadsRegulationOnStartup() {
        assertThat(lookup.isLoaded()).isTrue();
        assertThat(lookup.articleCount()).isGreaterThan(0);
    }

    @Test
    void articleByNumber18Exists() {
        var art = lookup.articleByNumber(18);
        assertThat(art).isNotNull();
        assertThat(art.title()).containsIgnoringCase("ingredients");
    }

    @Test
    void allergenAnnexHasFourteenItems() {
        var annex = lookup.allergenAnnex();
        assertThat(annex).isNotNull();
        assertThat(annex.items()).hasSize(14);
    }

    @Test
    void findAllergenMatchOnMilkKeyword() {
        assertThat(lookup.findAllergenMatch("lactose")).isPresent();
        assertThat(lookup.findAllergenMatch("whey protein")).isEmpty(); // not in examples
        assertThat(lookup.findAllergenMatch("Milk")).isPresent();
        assertThat(lookup.findAllergenMatch("random-non-allergen")).isEmpty();
    }

    @Test
    void findAllergenMatchOnGluten() {
        assertThat(lookup.findAllergenMatch("wheat")).isPresent();
        assertThat(lookup.findAllergenMatch("barley")).isPresent();
    }

    @Test
    void lookupForFindingReturnsRelevantArticles() {
        var cr = new ComplianceRelevance(
                List.of("milk"), List.of(), false, true, List.of("vitamin D source"),
                List.of(), null, false, List.of("vitamin d3"), List.of()
        );
        var results = lookup.lookupForFinding(cr);
        assertThat(results).isNotEmpty();
        assertThat(results.stream().map(ComplianceLookupService.LookupResult::sourceRef))
                .anyMatch(ref -> ref.contains("Article"));
    }

    @Test
    void lookupCapsAtFiveResults() {
        // A very broad request should still return at most 5
        var cr = new ComplianceRelevance(
                List.of("milk"), List.of("lanolin"), true, true,
                List.of("vitamin D", "calcium"),
                List.of("allergens", "labelling", "ingredients", "claims"),
                "everything", true, List.of("milk", "lactose", "wheat"), List.of()
        );
        var results = lookup.lookupForFinding(cr);
        assertThat(results).hasSizeLessThanOrEqualTo(5);
    }

    @Test
    void articlesByTagsReturnsAllergenArticles() {
        var articles = lookup.articlesByTags(java.util.Set.of("allergens"));
        assertThat(articles).isNotEmpty();
        assertThat(articles.stream().map(RegulationJson.Article::number))
                .contains(21); // Article 21 is about allergen labelling
    }
}
