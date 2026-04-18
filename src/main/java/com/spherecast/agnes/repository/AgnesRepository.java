package com.spherecast.agnes.repository;

import com.spherecast.agnes.service.QueryResult;
import com.spherecast.agnes.service.SqlExecutor;
import com.spherecast.agnes.util.SqlGuard;
import org.springframework.stereotype.Repository;

@Repository
public class AgnesRepository {

    private final SqlGuard sqlGuard;
    private final SqlExecutor sqlExecutor;

    public AgnesRepository(SqlGuard sqlGuard, SqlExecutor sqlExecutor) {
        this.sqlGuard = sqlGuard;
        this.sqlExecutor = sqlExecutor;
    }

    public QueryResult executeQuery(String sql) {
        sqlGuard.validate(sql);
        return sqlExecutor.executeReadOnly(sql);
    }
}
