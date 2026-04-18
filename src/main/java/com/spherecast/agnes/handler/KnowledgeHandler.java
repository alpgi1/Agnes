package com.spherecast.agnes.handler;

import com.spherecast.agnes.dto.ChatMessage;
import com.spherecast.agnes.dto.KnowledgeRequest;
import com.spherecast.agnes.dto.KnowledgeResponse;
import com.spherecast.agnes.repository.AgnesRepository;
import com.spherecast.agnes.service.HistoryFormatter;
import com.spherecast.agnes.service.PromptLoader;
import com.spherecast.agnes.service.QueryExecutionException;
import com.spherecast.agnes.service.QueryResult;
import com.spherecast.agnes.service.SchemaProvider;
import com.spherecast.agnes.service.claude.ClaudeClient;
import com.spherecast.agnes.util.InvalidSqlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class KnowledgeHandler {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeHandler.class);

    private static final Pattern SQL_FENCE = Pattern.compile(
            "^```(?:sql)?\\s*(.+?)\\s*```$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final int MAX_ROWS_FOR_PROMPT = 50;
    private static final int MAX_PROMPT_JSON_CHARS = 50_000;

    private final SchemaProvider schemaProvider;
    private final PromptLoader promptLoader;
    private final ClaudeClient claudeClient;
    private final AgnesRepository repository;
    private final ObjectMapper objectMapper;
    private final HistoryFormatter historyFormatter;

    public KnowledgeHandler(SchemaProvider schemaProvider,
                            PromptLoader promptLoader,
                            ClaudeClient claudeClient,
                            AgnesRepository repository,
                            ObjectMapper objectMapper,
                            HistoryFormatter historyFormatter) {
        this.schemaProvider = schemaProvider;
        this.promptLoader = promptLoader;
        this.claudeClient = claudeClient;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.historyFormatter = historyFormatter;
    }

    public KnowledgeResponse handle(KnowledgeRequest request) {
        String sessionId = request.sessionId() != null
                ? request.sessionId()
                : UUID.randomUUID().toString();
        List<ChatMessage> history = request.history() != null ? request.history() : List.of();
        long startTime = System.currentTimeMillis();

        log.info("sessionId={} knowledge.prompt=\"{}\"", sessionId, request.prompt());

        String systemPrompt = promptLoader.render("knowledge-schema-to-sql", Map.of(
                "SCHEMA", schemaProvider.getSchemaAsPromptString(),
                "HISTORY", historyFormatter.format(history),
                "QUESTION", request.prompt()
        ));
        String rawSql = claudeClient.ask(systemPrompt, request.prompt(), history, 0.2);
        String sql = stripSqlFences(rawSql.trim());

        ExecResult execResult = executeWithRepair(sql, request.prompt(), sessionId);

        if (execResult == null) {
            return new KnowledgeResponse(
                    sessionId,
                    "I wasn't able to query the database for that. Please try rephrasing.",
                    sql,
                    0,
                    false,
                    System.currentTimeMillis() - startTime
            );
        }

        QueryResult queryResult = execResult.result();
        sql = execResult.finalSql();

        List<Map<String, Object>> rowsForPrompt = queryResult.rows();
        boolean rowsTruncatedForPrompt = false;
        if (rowsForPrompt.size() > MAX_ROWS_FOR_PROMPT) {
            rowsForPrompt = new ArrayList<>(rowsForPrompt.subList(0, MAX_ROWS_FOR_PROMPT));
            rowsTruncatedForPrompt = true;
        }

        String rowsJson;
        try {
            String candidate = objectMapper.writeValueAsString(rowsForPrompt);
            if (candidate.length() > MAX_PROMPT_JSON_CHARS) {
                rowsForPrompt = rowsForPrompt.subList(0, Math.max(1, rowsForPrompt.size() / 2));
                rowsTruncatedForPrompt = true;
                candidate = objectMapper.writeValueAsString(rowsForPrompt);
            }
            rowsJson = candidate;
        } catch (Exception e) {
            rowsJson = "[]";
        }

        String truncatedNote = (queryResult.truncated() || rowsTruncatedForPrompt)
                ? ", truncated — more exist"
                : "";

        String answerPrompt = promptLoader.render("knowledge-data-to-answer", Map.of(
                "HISTORY", historyFormatter.format(history),
                "QUESTION", request.prompt(),
                "SQL", sql,
                "ROW_COUNT", String.valueOf(queryResult.rows().size()),
                "TRUNCATED_NOTE", truncatedNote,
                "ROWS_JSON", rowsJson
        ));
        String markdown = claudeClient.ask(answerPrompt, request.prompt(), history, 0.5);

        return new KnowledgeResponse(
                sessionId,
                markdown,
                sql,
                queryResult.rows().size(),
                queryResult.truncated(),
                System.currentTimeMillis() - startTime
        );
    }

    private record ExecResult(QueryResult result, String finalSql) {}

    private ExecResult executeWithRepair(String sql, String question, String sessionId) {
        try {
            return new ExecResult(repository.executeQuery(sql), sql);
        } catch (InvalidSqlException | QueryExecutionException e) {
            log.warn("sessionId={} SQL failed ({}), attempting repair. sql={}", sessionId, e.getMessage(), sql);
            String repairPrompt = promptLoader.render("knowledge-sql-repair", Map.of(
                    "SCHEMA", schemaProvider.getSchemaAsPromptString(),
                    "QUESTION", question,
                    "PREVIOUS_SQL", sql,
                    "ERROR", e.getMessage()
            ));
            String repairedRaw = claudeClient.ask(repairPrompt, question, List.of(), 0.2);
            String repairedSql = stripSqlFences(repairedRaw.trim());
            try {
                return new ExecResult(repository.executeQuery(repairedSql), repairedSql);
            } catch (InvalidSqlException | QueryExecutionException e2) {
                log.warn("sessionId={} Repaired SQL also failed ({}). sql={}", sessionId, e2.getMessage(), repairedSql);
                return null;
            }
        }
    }

    private String stripSqlFences(String s) {
        var m = SQL_FENCE.matcher(s);
        if (m.matches()) {
            return m.group(1).trim();
        }
        return s;
    }
}
