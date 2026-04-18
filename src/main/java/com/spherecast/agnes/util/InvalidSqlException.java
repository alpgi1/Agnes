package com.spherecast.agnes.util;

public class InvalidSqlException extends RuntimeException {

    private final String reason;
    private final String sql;

    public InvalidSqlException(String reason, String sql) {
        super("Invalid SQL: " + reason);
        this.reason = reason;
        this.sql = sql;
    }

    public String getReason() {
        return reason;
    }

    public String getSql() {
        return sql;
    }
}
