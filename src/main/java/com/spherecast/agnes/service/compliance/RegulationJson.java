package com.spherecast.agnes.service.compliance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RegulationJson(
        Regulation regulation,
        List<Article> articles,
        List<Annex> annexes
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Regulation(String id, String title,
                             @JsonProperty("consolidated_version") String consolidatedVersion,
                             String source) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Article(
            int number,
            String title,
            String chapter,
            String section,
            String summary,
            @JsonProperty("full_text") String fullText,
            @JsonProperty("relevance_tags") List<String> relevanceTags
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Annex(
            String id,
            String title,
            String type,
            List<AllergenItem> items,
            List<DesignationPart> parts,
            String content
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record AllergenItem(int number, String name, String details,
                                   List<String> examples) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record DesignationPart(String letter, String title,
                                      List<Provision> provisions) {
            @JsonIgnoreProperties(ignoreUnknown = true)
            public record Provision(String topic, String rule) {}
        }
    }
}
