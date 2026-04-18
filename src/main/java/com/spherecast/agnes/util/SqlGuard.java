package com.spherecast.agnes.util;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

@Component
public class SqlGuard {

    private static final Pattern LINE_COMMENT = Pattern.compile("--[^\\r\\n]*");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern FIRST_WORD = Pattern.compile("^\\s*(\\w+)");

    private static final Set<String> FORBIDDEN = Set.of(
            "insert", "update", "delete", "drop", "create", "alter",
            "attach", "detach", "vacuum", "replace", "pragma",
            "begin", "commit", "rollback", "savepoint",
            "truncate", "grant", "revoke", "reindex", "analyze"
    );

    public void validate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new InvalidSqlException("SQL is null or blank", sql);
        }

        String stripped = BLOCK_COMMENT.matcher(sql).replaceAll(" ");
        stripped = LINE_COMMENT.matcher(stripped).replaceAll(" ");
        stripped = stripped.trim();

        if (stripped.isEmpty()) {
            throw new InvalidSqlException("SQL is empty after stripping comments", sql);
        }

        if (stripped.endsWith(";")) {
            stripped = stripped.substring(0, stripped.length() - 1).trim();
        }

        if (stripped.contains(";")) {
            throw new InvalidSqlException("multi-statement SQL is not allowed", sql);
        }

        var firstWordMatcher = FIRST_WORD.matcher(stripped);
        if (!firstWordMatcher.find()) {
            throw new InvalidSqlException("could not parse first keyword", sql);
        }
        String firstKeyword = firstWordMatcher.group(1).toLowerCase();
        if (!firstKeyword.equals("select") && !firstKeyword.equals("with")) {
            throw new InvalidSqlException(
                    "SQL must start with SELECT or WITH (got: " + firstKeyword + ")", sql);
        }

        String lower = stripped.toLowerCase();
        for (String forbidden : FORBIDDEN) {
            Pattern p = Pattern.compile("\\b" + forbidden + "\\b");
            if (p.matcher(lower).find()) {
                throw new InvalidSqlException(
                        "forbidden keyword: " + forbidden, sql);
            }
        }
    }
}
