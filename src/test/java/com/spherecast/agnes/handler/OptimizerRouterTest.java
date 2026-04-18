package com.spherecast.agnes.handler;

import com.spherecast.agnes.handler.RouterDecision.Scope.ScopeType;
import com.spherecast.agnes.handler.optimizers.OptimizerType;
import com.spherecast.agnes.service.HistoryFormatter;
import com.spherecast.agnes.service.PromptLoader;
import com.spherecast.agnes.service.claude.ClaudeApiException;
import com.spherecast.agnes.service.claude.ClaudeClient;
import com.spherecast.agnes.service.claude.JsonExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OptimizerRouterTest {

    @Mock PromptLoader promptLoader;
    @Mock ClaudeClient claudeClient;

    private OptimizerRouter router;

    @BeforeEach
    void setUp() {
        when(promptLoader.render(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));
        router = new OptimizerRouter(promptLoader, claudeClient,
                new JsonExtractor(new ObjectMapper()), new HistoryFormatter());
    }

    private void mockAsk(String json) {
        when(claudeClient.ask(anyString(), anyString(), anyList(), anyDouble()))
                .thenReturn(json);
    }

    @Test
    void singleOptimizerAllScope() {
        mockAsk("""
                {"optimizers":["SUBSTITUTION"],"scope":{"type":"ALL","value":null},"reasoning":"explicit"}
                """);
        RouterDecision d = router.route("Find substitution candidates.", List.of());
        assertThat(d.optimizers()).containsExactly(OptimizerType.SUBSTITUTION);
        assertThat(d.scope().type()).isEqualTo(ScopeType.ALL);
        assertThat(d.scope().value()).isNull();
    }

    @Test
    void allFourReturnedInCanonicalOrderRegardlessOfInputOrder() {
        mockAsk("""
                {"optimizers":["COMPLEXITY","REFORMULATION","SUBSTITUTION","CONSOLIDATION"],
                 "scope":{"type":"ALL","value":null},"reasoning":"all"}
                """);
        RouterDecision d = router.route("Optimize everything.", List.of());
        assertThat(d.optimizers()).containsExactlyElementsOf(OptimizerType.CANONICAL_ORDER);
    }

    @Test
    void unknownOptimizerNameIsDropped() {
        mockAsk("""
                {"optimizers":["MAGIC","SUBSTITUTION"],"scope":{"type":"ALL","value":null},"reasoning":"x"}
                """);
        RouterDecision d = router.route("Find something.", List.of());
        assertThat(d.optimizers()).containsExactly(OptimizerType.SUBSTITUTION);
    }

    @Test
    void emptyOptimizersArrayFallsBackToCanonical() {
        mockAsk("""
                {"optimizers":[],"scope":{"type":"ALL","value":null},"reasoning":"empty"}
                """);
        RouterDecision d = router.route("Do something.", List.of());
        assertThat(d.optimizers()).containsExactlyElementsOf(OptimizerType.CANONICAL_ORDER);
    }

    @Test
    void nonsenseScopeTypeFallsBackToAll() {
        mockAsk("""
                {"optimizers":["SUBSTITUTION"],"scope":{"type":"NONSENSE","value":"x"},"reasoning":"x"}
                """);
        RouterDecision d = router.route("test", List.of());
        assertThat(d.scope().type()).isEqualTo(ScopeType.ALL);
    }

    @Test
    void nonAllScopeWithNullValueFallsBackToAll() {
        mockAsk("""
                {"optimizers":["SUBSTITUTION"],"scope":{"type":"COMPANY","value":null},"reasoning":"x"}
                """);
        RouterDecision d = router.route("test", List.of());
        assertThat(d.scope().type()).isEqualTo(ScopeType.ALL);
    }

    @Test
    void companyScopeExtracted() {
        mockAsk("""
                {"optimizers":["SUBSTITUTION","CONSOLIDATION","REFORMULATION","COMPLEXITY"],
                 "scope":{"type":"COMPANY","value":"Aloha"},"reasoning":"company scope"}
                """);
        RouterDecision d = router.route("Optimize Aloha.", List.of());
        assertThat(d.scope().type()).isEqualTo(ScopeType.COMPANY);
        assertThat(d.scope().value()).isEqualTo("Aloha");
    }

    @Test
    void claudeApiExceptionFallsBackSafely() {
        when(claudeClient.ask(anyString(), anyString(), anyList(), anyDouble()))
                .thenThrow(new ClaudeApiException("API down", 502, null));
        RouterDecision d = router.route("Do something.", List.of());
        assertThat(d.optimizers()).containsExactlyElementsOf(OptimizerType.CANONICAL_ORDER);
        assertThat(d.scope().type()).isEqualTo(ScopeType.ALL);
        assertThat(d.reasoning()).containsIgnoringCase("Router failed");
    }

    @Test
    void malformedJsonFallsBackSafely() {
        mockAsk("This is just prose, no JSON at all.");
        RouterDecision d = router.route("Do something.", List.of());
        assertThat(d.optimizers()).containsExactlyElementsOf(OptimizerType.CANONICAL_ORDER);
        assertThat(d.scope().type()).isEqualTo(ScopeType.ALL);
    }
}
