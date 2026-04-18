package com.spherecast.agnes.repository;

import com.spherecast.agnes.service.QueryResult;
import com.spherecast.agnes.util.InvalidSqlException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class AgnesRepositoryIT {

    @Autowired
    private AgnesRepository repository;

    @Test
    void countCompaniesReturns61() {
        QueryResult result = repository.executeQuery("SELECT COUNT(*) AS n FROM Company");

        assertThat(result.rows()).hasSize(1);
        assertThat(result.truncated()).isFalse();
        Object n = result.rows().get(0).get("n");
        assertThat(((Number) n).longValue()).isEqualTo(61L);
    }

    @Test
    void dropTableIsRejectedByGuard() {
        assertThrows(InvalidSqlException.class,
                () -> repository.executeQuery("DROP TABLE Company"));
    }

    @Test
    void largeResultSetIsTruncatedAt200() {
        QueryResult result = repository.executeQuery("SELECT * FROM Product");

        assertThat(result.truncated()).isTrue();
        assertThat(result.rows()).hasSize(200);
    }
}
