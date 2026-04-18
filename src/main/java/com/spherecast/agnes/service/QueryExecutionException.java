package com.spherecast.agnes.service;

public class QueryExecutionException extends RuntimeException {

    private final String sql;

    public QueryExecutionException(String sql, String message, Throwable cause) {
        super(message, cause);
        this.sql = sql;
    }

    public String getSql() {
        return sql;
    }
}
