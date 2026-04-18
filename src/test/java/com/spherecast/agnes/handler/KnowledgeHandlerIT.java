package com.spherecast.agnes.handler;

import com.spherecast.agnes.dto.KnowledgeRequest;
import com.spherecast.agnes.dto.KnowledgeResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = "sk-ant-.+")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KnowledgeHandlerIT {

    @Autowired
    KnowledgeHandler handler;

    @Test
    @org.junit.jupiter.api.Order(1)
    void countCompanies() {
        KnowledgeResponse res = handler.handle(new KnowledgeRequest(
                "How many companies are in the database?", null, null));

        assertThat(res.markdown()).contains("61");
        assertThat(res.sqlUsed().toUpperCase()).contains("SELECT");
        assertThat(res.sqlUsed().toUpperCase()).contains("COMPANY");
        assertThat(res.sessionId()).isNotBlank();
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void productsForSpecificCompany() throws InterruptedException {
        Thread.sleep(1000);
        KnowledgeResponse res = handler.handle(new KnowledgeRequest(
                "How many finished-good products does the company 'Aloha' have?", null, null));

        assertThat(res.markdown()).matches("(?s).*\\d+.*");
        assertThat(res.sqlUsed().toLowerCase()).contains("aloha");
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    void fuzzyIngredientQuery() throws InterruptedException {
        Thread.sleep(1000);
        KnowledgeResponse res = handler.handle(new KnowledgeRequest(
                "Which raw materials contain vitamin D?", null, null));

        assertThat(res.markdown().toLowerCase())
                .containsAnyOf("vitamin", "d3", "cholecalciferol");
    }
}
