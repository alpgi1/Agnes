package com.spherecast.agnes.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SchemaProvider {

    private static final Logger log = LoggerFactory.getLogger(SchemaProvider.class);

    private static final String SEMANTIC_NOTES = """
            === Semantic Notes ===
            - Product.Type is either 'finished-good' or 'raw-material'.
            - Every finished-good has exactly one BOM (BOM.ProducedProductId → Product.Id).
            - BOM_Component links a BOM to raw-material Products (BOM_Component.ConsumedProductId
              → Product.Id WHERE Product.Type = 'raw-material').
            - Supplier_Product links a Supplier to a raw-material Product they can deliver.
            - Raw-material SKU strings encode the ingredient name as a slug,
              e.g. "RM-C1-vitamin-d3-cholecalciferol-67efce0f" → ingredient "vitamin-d3".
            - The database contains NO prices, quantities, lead times, or compliance metadata.
            """;

    private final JdbcTemplate jdbcTemplate;

    public SchemaProvider(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Cacheable("schema")
    public String getSchemaAsPromptString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Agnes Database Schema ===\n\n");

        List<String> tables = getTableNames();
        for (String table : tables) {
            long rowCount = rowCount(table);
            sb.append("Table: ").append(table)
                    .append(" (").append(rowCount).append(" rows)\n");

            List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                    "PRAGMA table_info(\"" + table + "\")");
            for (Map<String, Object> col : columns) {
                sb.append("  - ").append(col.get("name"))
                        .append(" ").append(col.get("type"));
                int pk = ((Number) col.get("pk")).intValue();
                int notnull = ((Number) col.get("notnull")).intValue();
                if (pk > 0) {
                    sb.append(" PRIMARY KEY");
                }
                if (notnull == 1) {
                    sb.append(" NOT NULL");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        sb.append(SEMANTIC_NOTES);
        String schema = sb.toString();
        log.info("Generated schema string ({} chars):\n{}", schema.length(), schema);
        return schema;
    }

    public List<String> getTableNames() {
        return jdbcTemplate.queryForList(
                "SELECT name FROM sqlite_master WHERE type='table' "
                        + "AND name NOT LIKE 'sqlite_%' ORDER BY name",
                String.class);
    }

    private long rowCount(String table) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"" + table + "\"", Long.class);
        return count == null ? 0 : count;
    }
}
