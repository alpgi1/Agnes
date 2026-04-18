package com.spherecast.agnes.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SqlExecutor {

    private static final int QUERY_TIMEOUT_SECONDS = 5;
    private static final int MAX_ROWS_WITH_SENTINEL = 201;
    private static final int MAX_ROWS_RETURNED = 200;

    private final JdbcTemplate jdbcTemplate;

    public SqlExecutor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public QueryResult executeReadOnly(String sql) {
        long start = System.nanoTime();
        try {
            ResultSetExtractor<QueryResult> extractor = rs -> extract(rs, start);
            return jdbcTemplate.query(
                    conn -> {
                        PreparedStatement ps = conn.prepareStatement(sql);
                        ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                        ps.setMaxRows(MAX_ROWS_WITH_SENTINEL);
                        return ps;
                    },
                    extractor
            );
        } catch (org.springframework.dao.DataAccessException e) {
            Throwable cause = e.getCause() instanceof SQLException ? e.getCause() : e;
            throw new QueryExecutionException(sql, cause.getMessage(), cause);
        }
    }

    private QueryResult extract(ResultSet rs, long start) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();
        List<String> columns = new ArrayList<>(colCount);
        for (int i = 1; i <= colCount; i++) {
            columns.add(meta.getColumnLabel(i));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>(colCount);
            for (int i = 1; i <= colCount; i++) {
                row.put(columns.get(i - 1), rs.getObject(i));
            }
            rows.add(row);
        }

        boolean truncated = rows.size() > MAX_ROWS_RETURNED;
        if (truncated) {
            rows = new ArrayList<>(rows.subList(0, MAX_ROWS_RETURNED));
        }

        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        return new QueryResult(rows, columns, truncated, elapsedMs);
    }
}
