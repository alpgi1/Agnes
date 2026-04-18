package com.spherecast.agnes.service.claude;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = "sk-ant-.+")
class ClaudeClientIT {

    @Autowired
    ClaudeClient client;

    @Test
    void realApiSmokeTest() {
        String response = client.ask(
                "You are a terse assistant. Reply with exactly one word.",
                "What is the capital of France? One word only."
        );
        assertThat(response.toLowerCase()).contains("paris");
    }

    @Test
    void realApiJsonTest() {
        JsonNode json = client.askJson(
                "You output JSON describing a country.",
                "Describe France. Keys: capital, continent."
        );
        assertThat(json.get("capital").asString().toLowerCase()).contains("paris");
    }
}
