package com.spherecast.agnes.handler;

import com.spherecast.agnes.dto.KnowledgeRequest;
import com.spherecast.agnes.dto.KnowledgeResponse;
import com.spherecast.agnes.repository.AgnesRepository;
import com.spherecast.agnes.service.PromptLoader;
import com.spherecast.agnes.service.QueryExecutionException;
import com.spherecast.agnes.service.QueryResult;
import com.spherecast.agnes.service.SchemaProvider;
import com.spherecast.agnes.service.claude.ClaudeClient;
import com.spherecast.agnes.util.InvalidSqlException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeHandlerTest {

    @Mock SchemaProvider schemaProvider;
    @Mock PromptLoader promptLoader;
    @Mock ClaudeClient claudeClient;
    @Mock AgnesRepository repository;

    private KnowledgeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new KnowledgeHandler(schemaProvider, promptLoader, claudeClient, repository, new ObjectMapper());
        when(schemaProvider.getSchemaAsPromptString()).thenReturn("schema-string");
        // PromptLoader.render just returns a predictable string for any call
        when(promptLoader.render(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void happyPath() {
        when(claudeClient.ask(anyString(), anyString(), anyList(), eq(0.2)))
                .thenReturn("SELECT COUNT(*) FROM Company");
        QueryResult qr = new QueryResult(
                List.of(Map.of("COUNT(*)", 61)),
                List.of("COUNT(*)"),
                false,
                12L
        );
        when(repository.executeQuery("SELECT COUNT(*) FROM Company")).thenReturn(qr);
        when(claudeClient.ask(anyString(), anyString(), anyList(), eq(0.5)))
                .thenReturn("There are **61** companies.");

        KnowledgeResponse resp = handler.handle(
                new KnowledgeRequest("How many companies?", null, null));

        assertThat(resp.markdown()).contains("61");
        assertThat(resp.sqlUsed()).startsWith("SELECT");
        assertThat(resp.rowCount()).isEqualTo(1);
        assertThat(resp.sessionId()).isNotBlank();
        assertThat(resp.durationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void sqlRepairTriggeredOnInvalidSql() {
        // First SQL call → invalid SQL that fails validation
        when(claudeClient.ask(anyString(), anyString(), anyList(), eq(0.2)))
                .thenReturn("DROP TABLE x")    // first SQL attempt
                .thenReturn("SELECT 1");        // repair SQL attempt

        when(repository.executeQuery("DROP TABLE x"))
                .thenThrow(new InvalidSqlException("DDL not allowed", "DROP TABLE x"));

        QueryResult qr = new QueryResult(
                List.of(Map.of("1", 1)), List.of("1"), false, 1L);
        when(repository.executeQuery("SELECT 1")).thenReturn(qr);

        when(claudeClient.ask(anyString(), anyString(), anyList(), eq(0.5)))
                .thenReturn("Repair worked.");

        KnowledgeResponse resp = handler.handle(
                new KnowledgeRequest("Do something bad", null, null));

        assertThat(resp.markdown()).isEqualTo("Repair worked.");
        assertThat(resp.sqlUsed()).isEqualTo("SELECT 1");
    }

    @Test
    void gracefulFallbackWhenBothSqlAttemptsFail() {
        when(claudeClient.ask(anyString(), anyString(), anyList(), eq(0.2)))
                .thenReturn("BAD SQL 1")
                .thenReturn("BAD SQL 2");

        when(repository.executeQuery("BAD SQL 1"))
                .thenThrow(new InvalidSqlException("invalid", "BAD SQL 1"));
        when(repository.executeQuery("BAD SQL 2"))
                .thenThrow(new QueryExecutionException("BAD SQL 2", "syntax error", null));

        KnowledgeResponse resp = handler.handle(
                new KnowledgeRequest("banana purple delete", null, null));

        assertThat(resp.markdown()).containsIgnoringCase("wasn't able to query");
        // No answer-generation Claude call should have been made
        verify(claudeClient, never()).ask(anyString(), anyString(), anyList(), eq(0.5));
    }

    @Test
    void sessionIdEchoedWhenProvided() {
        when(claudeClient.ask(anyString(), anyString(), anyList(), eq(0.2)))
                .thenReturn("SELECT 1");
        when(repository.executeQuery("SELECT 1"))
                .thenReturn(new QueryResult(List.of(), List.of(), false, 1L));
        when(claudeClient.ask(anyString(), anyString(), anyList(), eq(0.5)))
                .thenReturn("no rows");

        KnowledgeResponse resp = handler.handle(
                new KnowledgeRequest("q", null, "my-session-id"));

        assertThat(resp.sessionId()).isEqualTo("my-session-id");
    }
}
