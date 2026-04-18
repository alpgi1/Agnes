package com.spherecast.agnes.service.claude;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonExtractorTest {

    private JsonExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new JsonExtractor(new ObjectMapper());
    }

    @Test
    void parsesRawObject() {
        JsonNode node = extractor.extractJson("{\"a\":1}");
        assertThat(node.get("a").asInt()).isEqualTo(1);
    }

    @Test
    void parsesRawObjectWithWhitespace() {
        JsonNode node = extractor.extractJson("   {\"a\":1}   ");
        assertThat(node.get("a").asInt()).isEqualTo(1);
    }

    @Test
    void parsesFencedJsonWithLanguage() {
        JsonNode node = extractor.extractJson("```json\n{\"a\":1}\n```");
        assertThat(node.get("a").asInt()).isEqualTo(1);
    }

    @Test
    void parsesFencedJsonWithoutLanguage() {
        JsonNode node = extractor.extractJson("```\n{\"a\":1}\n```");
        assertThat(node.get("a").asInt()).isEqualTo(1);
    }

    @Test
    void parsesJsonEmbeddedInProse() {
        JsonNode node = extractor.extractJson("Here is the result: {\"a\": 1}. Hope this helps.");
        assertThat(node.get("a").asInt()).isEqualTo(1);
    }

    @Test
    void parsesNestedObject() {
        JsonNode node = extractor.extractJson("{\"outer\": {\"inner\": [1,2,3]}}");
        assertThat(node.get("outer").get("inner").get(2).asInt()).isEqualTo(3);
    }

    @Test
    void parsesTopLevelArray() {
        JsonNode node = extractor.extractJson("[1,2,3]");
        assertThat(node.isArray()).isTrue();
        assertThat(node.get(1).asInt()).isEqualTo(2);
    }

    @Test
    void parsesArrayEmbeddedInProse() {
        JsonNode node = extractor.extractJson("Result: [10, 20, 30]!");
        assertThat(node.isArray()).isTrue();
        assertThat(node.get(0).asInt()).isEqualTo(10);
    }

    @Test
    void parsesToTypedRecord() {
        record Sample(int a, String b) {
        }
        Sample s = extractor.extractJson("{\"a\": 42, \"b\": \"hi\"}", Sample.class);
        assertThat(s.a()).isEqualTo(42);
        assertThat(s.b()).isEqualTo("hi");
    }

    @Test
    void throwsOnEmpty() {
        assertThatThrownBy(() -> extractor.extractJson(""))
                .isInstanceOf(JsonExtractionException.class);
    }

    @Test
    void throwsOnNull() {
        assertThatThrownBy(() -> extractor.extractJson(null))
                .isInstanceOf(JsonExtractionException.class);
    }

    @Test
    void throwsOnProseWithoutJson() {
        assertThatThrownBy(() -> extractor.extractJson("just prose no json"))
                .isInstanceOf(JsonExtractionException.class);
    }

    @Test
    void throwsOnUnbalancedJson() {
        assertThatThrownBy(() -> extractor.extractJson("{\"a\": 1"))
                .isInstanceOf(JsonExtractionException.class);
    }
}
