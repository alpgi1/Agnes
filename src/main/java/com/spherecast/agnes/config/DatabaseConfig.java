package com.spherecast.agnes.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteOpenMode;

import javax.sql.DataSource;

@Configuration
public class DatabaseConfig {

    @Bean
    public DataSource dataSource() {
        SQLiteConfig sqliteConfig = new SQLiteConfig();
        sqliteConfig.setReadOnly(true);
        sqliteConfig.setOpenMode(SQLiteOpenMode.READONLY);

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl("jdbc:sqlite:./db.sqlite");
        hikari.setDriverClassName("org.sqlite.JDBC");
        hikari.setMaximumPoolSize(4);
        hikari.setMinimumIdle(1);
        hikari.setPoolName("agnes-sqlite-ro");
        hikari.setReadOnly(true);
        hikari.setDataSourceProperties(sqliteConfig.toProperties());

        return new HikariDataSource(hikari);
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource ds) {
        return new JdbcTemplate(ds);
    }
}
