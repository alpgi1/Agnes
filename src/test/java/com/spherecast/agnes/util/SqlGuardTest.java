package com.spherecast.agnes.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlGuardTest {

    private final SqlGuard guard = new SqlGuard();

    @Test
    void acceptsSimpleSelect() {
        assertDoesNotThrow(() -> guard.validate("SELECT 1"));
    }

    @Test
    void acceptsLowercaseSelect() {
        assertDoesNotThrow(() -> guard.validate("select * from Company"));
    }

    @Test
    void acceptsWithCte() {
        assertDoesNotThrow(() ->
                guard.validate("WITH x AS (SELECT 1) SELECT * FROM x"));
    }

    @Test
    void acceptsTrailingSemicolon() {
        assertDoesNotThrow(() ->
                guard.validate("SELECT * FROM Product WHERE Type = 'raw-material';"));
    }

    @Test
    void acceptsLineComments() {
        assertDoesNotThrow(() ->
                guard.validate("-- fetch all companies\nSELECT * FROM Company"));
    }

    @Test
    void acceptsBlockComments() {
        assertDoesNotThrow(() ->
                guard.validate("/* header */ SELECT * FROM Company /* footer */"));
    }

    @Test
    void rejectsDrop() {
        assertThrows(InvalidSqlException.class,
                () -> guard.validate("DROP TABLE Company"));
    }

    @Test
    void rejectsInsert() {
        assertThrows(InvalidSqlException.class,
                () -> guard.validate("INSERT INTO Company VALUES (1,'x')"));
    }

    @Test
    void rejectsMultipleStatements() {
        assertThrows(InvalidSqlException.class,
                () -> guard.validate("SELECT 1; DROP TABLE x"));
    }

    @Test
    void rejectsUpdate() {
        assertThrows(InvalidSqlException.class,
                () -> guard.validate("UPDATE Product SET SKU = 'x'"));
    }

    @Test
    void rejectsPragma() {
        assertThrows(InvalidSqlException.class,
                () -> guard.validate("PRAGMA table_info(x)"));
    }

    @Test
    void rejectsEmptyString() {
        assertThrows(InvalidSqlException.class,
                () -> guard.validate(""));
    }

    @Test
    void rejectsNull() {
        assertThrows(InvalidSqlException.class,
                () -> guard.validate(null));
    }

    @Test
    void rejectsAttachDatabase() {
        assertThrows(InvalidSqlException.class,
                () -> guard.validate("ATTACH DATABASE 'other.db' AS other"));
    }

    @Test
    void rejectsCommentedMalice() {
        assertThrows(InvalidSqlException.class,
                () -> guard.validate("SELECT 1; /* safe */ DROP TABLE x"));
    }
}
