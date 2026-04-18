package com.spherecast.agnes.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptLoaderTest {

    private PromptLoader loader;

    @BeforeEach
    void setUp() {
        loader = new PromptLoader(new DefaultResourceLoader());
    }

    @Test
    void loadsExistingTemplate() {
        String content = loader.load("knowledge-schema-to-sql");
        assertThat(content).isNotBlank();
        assertThat(content).contains("SELECT");
    }

    @Test
    void throwsForMissingTemplate() {
        assertThatThrownBy(() -> loader.load("does-not-exist"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void renderSubstitutesAllPlaceholders() {
        String result = loader.render("knowledge-schema-to-sql", Map.of(
                "SCHEMA", "TABLE Company",
                "HISTORY", "(no prior turns)",
                "QUESTION", "How many companies?"
        ));
        assertThat(result).contains("TABLE Company");
        assertThat(result).contains("How many companies?");
        assertThat(result).doesNotContain("{{SCHEMA}}");
        assertThat(result).doesNotContain("{{HISTORY}}");
        assertThat(result).doesNotContain("{{QUESTION}}");
    }

    @Test
    void renderReplacesUnknownPlaceholderWithEmpty() {
        String result = loader.render("knowledge-schema-to-sql", Map.of(
                "HISTORY", "(no prior turns)",
                "QUESTION", "test"
                // SCHEMA intentionally missing
        ));
        assertThat(result).doesNotContain("{{SCHEMA}}");
    }
}
