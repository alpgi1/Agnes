package com.spherecast.agnes.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SchemaProviderTest {

    @Autowired
    private SchemaProvider schemaProvider;

    @Test
    void schemaContainsAllTablesAndRowCounts() {
        String schema = schemaProvider.getSchemaAsPromptString();

        assertThat(schema).contains("Table: BOM (149 rows)");
        assertThat(schema).contains("Table: BOM_Component (1528 rows)");
        assertThat(schema).contains("Table: Company (61 rows)");
        assertThat(schema).contains("Table: Product (1025 rows)");
        assertThat(schema).contains("Table: Supplier (40 rows)");
        assertThat(schema).contains("Table: Supplier_Product (1633 rows)");

        assertThat(schema).contains("=== Semantic Notes ===");
        assertThat(schema).contains("finished-good");
        assertThat(schema).contains("raw-material");
    }
}
