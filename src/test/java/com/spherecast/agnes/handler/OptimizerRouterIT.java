package com.spherecast.agnes.handler;

import com.spherecast.agnes.dto.ChatMessage;
import com.spherecast.agnes.handler.RouterDecision.Scope.ScopeType;
import com.spherecast.agnes.handler.optimizers.OptimizerType;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = "sk-ant-.+")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OptimizerRouterIT {

    @Autowired
    OptimizerRouter router;

    @Test
    @Order(1)
    void explicitSubstitutionEnglish() {
        RouterDecision d = router.route("Find substitution candidates across all companies.", List.of());
        assertThat(d.optimizers()).containsExactly(OptimizerType.SUBSTITUTION);
        assertThat(d.scope().type()).isEqualTo(ScopeType.ALL);
    }

    @Test
    @Order(2)
    void genericGerman() throws InterruptedException {
        Thread.sleep(1000);
        RouterDecision d = router.route("Optimiere das gesamte Portfolio.", List.of());
        assertThat(d.optimizers()).containsExactlyElementsOf(OptimizerType.CANONICAL_ORDER);
        assertThat(d.scope().type()).isEqualTo(ScopeType.ALL);
    }

    @Test
    @Order(3)
    void reformulationWithProductScope() throws InterruptedException {
        Thread.sleep(1000);
        RouterDecision d = router.route("Reformulate product FG-iherb-10421 to reduce cost.", List.of());
        assertThat(d.optimizers()).containsExactly(OptimizerType.REFORMULATION);
        assertThat(d.scope().type()).isEqualTo(ScopeType.PRODUCT);
        assertThat(d.scope().value()).contains("FG-iherb-10421");
    }

    @Test
    @Order(4)
    void genericWithCompanyScopeGerman() throws InterruptedException {
        Thread.sleep(1000);
        RouterDecision d = router.route("Was können wir bei Company Aloha verbessern?", List.of());
        assertThat(d.optimizers()).hasSize(4);
        assertThat(d.scope().type()).isEqualTo(ScopeType.COMPANY);
        assertThat(d.scope().value().toLowerCase()).contains("aloha");
    }

    @Test
    @Order(5)
    void consolidationWithIngredientScopeGerman() throws InterruptedException {
        Thread.sleep(1000);
        RouterDecision d = router.route("Bündele den Magnesium-Einkauf.", List.of());
        assertThat(d.optimizers()).containsExactly(OptimizerType.CONSOLIDATION);
        assertThat(d.scope().type()).isEqualTo(ScopeType.INGREDIENT);
        assertThat(d.scope().value().toLowerCase()).contains("magnesium");
    }

    @Test
    @Order(6)
    void complexityWithProductScopeGerman() throws InterruptedException {
        Thread.sleep(1000);
        RouterDecision d = router.route("Welche redundanten Zutaten gibt es in FG-iherb-14689?", List.of());
        assertThat(d.optimizers()).containsExactly(OptimizerType.COMPLEXITY);
        assertThat(d.scope().type()).isEqualTo(ScopeType.PRODUCT);
    }

    @Test
    @Order(7)
    void followUpResolvesWithHistory() throws InterruptedException {
        Thread.sleep(1000);
        List<ChatMessage> history = List.of(
                ChatMessage.user("Find substitution candidates for Aloha."),
                ChatMessage.assistant("Found 3 substitution candidates...")
        );
        RouterDecision d = router.route("Same for 21st Century please.", history);
        assertThat(d.optimizers()).containsExactly(OptimizerType.SUBSTITUTION);
        assertThat(d.scope().type()).isEqualTo(ScopeType.COMPANY);
        assertThat(d.scope().value()).containsIgnoringCase("21st Century");
    }
}
