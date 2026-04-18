package com.spherecast.agnes.service.compliance;

import com.spherecast.agnes.config.ExternalApisConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IHerbClientTest {

    private IHerbClient clientWithPlaceholder() {
        return new IHerbClient(new ExternalApisConfig("PLACEHOLDER", "host", "url"));
    }

    @Test
    void fallsBackToStubWhenKeyIsPlaceholder() {
        var client = clientWithPlaceholder();
        var result = client.search("vitamin d3");

        assertThat(result.fromStub()).isTrue();
        assertThat(result.stubReason()).contains("not configured");
        assertThat(result.products()).isNotEmpty();
        assertThat(result.products().get(0).certifications()).isNotEmpty();
    }

    @Test
    void stubReturnsEmptyForUnknownKeyword() {
        var client = clientWithPlaceholder();
        var result = client.search("totally-made-up-ingredient-xyz");

        assertThat(result.fromStub()).isTrue();
        assertThat(result.products()).isEmpty();
    }

    @Test
    void searchAllDedupesKeywords() {
        var client = clientWithPlaceholder();
        var results = client.searchAll(List.of("vitamin d3", "Vitamin D3", "VITAMIN D3"));
        assertThat(results).hasSize(1); // all deduped to "vitamin d3"
    }

    @Test
    void stubHasCorrectProductData() {
        var client = clientWithPlaceholder();
        var result = client.search("magnesium glycinate");

        assertThat(result.products()).isNotEmpty();
        var p = result.products().get(0);
        assertThat(p.title()).containsIgnoringCase("magnesium");
        assertThat(p.url()).isNotNull();
    }

    @Test
    void partialMatchWorks() {
        var client = clientWithPlaceholder();
        // "cholecalciferol" is in the catalog; "vitamin d3 cholecalciferol" should partial-match
        var result = client.search("cholecalciferol supplement");
        // Should partial-match "cholecalciferol"
        assertThat(result.fromStub()).isTrue();
        assertThat(result.products()).isNotEmpty();
    }
}
