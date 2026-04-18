package com.spherecast.agnes.controller;

import com.spherecast.agnes.service.QueryExecutionException;
import com.spherecast.agnes.util.InvalidSqlException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidSqlException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidSql(InvalidSqlException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "invalid_sql",
                "reason", ex.getReason(),
                "sql", ex.getSql() == null ? "" : ex.getSql()
        ));
    }

    @ExceptionHandler(QueryExecutionException.class)
    public ResponseEntity<Map<String, Object>> handleQueryFailure(QueryExecutionException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "query_failed",
                "message", ex.getMessage() == null ? "" : ex.getMessage()
        ));
    }
}
