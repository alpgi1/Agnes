package com.spherecast.agnes.service;

import com.spherecast.agnes.handler.RouterDecision.Scope;
import com.spherecast.agnes.handler.RouterDecision.Scope.ScopeType;
import com.spherecast.agnes.handler.optimizers.ScopedData;
import com.spherecast.agnes.handler.optimizers.ScopedData.DenormRow;
import com.spherecast.agnes.repository.AgnesRepository;
import com.spherecast.agnes.service.claude.ClaudeClient;
import com.spherecast.agnes.util.InvalidSqlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ScopedDataLoader {

    private static final Logger log = LoggerFactory.getLogger(ScopedDataLoader.class);

    private static final int SCOPED_MAX_ROWS = 10_000;
    private static final int PROMPT_STRING_CAP = 60_000;
    private static final Pattern SQL_FENCE = Pattern.compile(
            "^```(?:sql)?\\s*(.+?)\\s*```$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern INGREDIENT_SLUG = Pattern.compile("^RM-[A-Za-z0-9]+-(.+)-[0-9a-f]{6,}$");

    private static final String ALL_PORTFOLIO_SQL = """
            SELECT
                c.Name  AS company,
                fg.SKU  AS product,
                rm.SKU  AS ingredient_sku,
                s.Name  AS supplier
            FROM Company c
            JOIN Product fg       ON fg.CompanyId = c.Id AND fg.Type = 'finished-good'
            JOIN BOM b            ON b.ProducedProductId = fg.Id
            JOIN BOM_Component bc ON bc.BOMId = b.Id
            JOIN Product rm       ON rm.Id = bc.ConsumedProductId AND rm.Type = 'raw-material'
            LEFT JOIN Supplier_Product sp ON sp.ProductId = rm.Id
            LEFT JOIN Supplier s          ON s.Id = sp.SupplierId
            ORDER BY c.Name, fg.SKU, rm.SKU
            """;

    private final AgnesRepository repository;
    private final ClaudeClient claudeClient;
    private final PromptLoader promptLoader;
    private final SchemaProvider schemaProvider;

    public ScopedDataLoader(AgnesRepository repository,
                            ClaudeClient claudeClient,
                            PromptLoader promptLoader,
                            SchemaProvider schemaProvider) {
        this.repository = repository;
        this.claudeClient = claudeClient;
        this.promptLoader = promptLoader;
        this.schemaProvider = schemaProvider;
    }

    public ScopedData load(Scope scope, String userPrompt) {
        if (scope == null || scope.type() == ScopeType.ALL) {
            return loadSmartAll(userPrompt);
        }
        return loadScoped(scope, userPrompt);
    }

    private ScopedData loadSmartAll(String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) return loadAll();
        String sql;
        try {
            String systemPrompt = promptLoader.render("scoped-data-sql", Map.of(
                    "SCHEMA", schemaProvider.getSchemaAsPromptString(),
                    "SCOPE_TYPE", "ALL",
                    "SCOPE_VALUE", "",
                    "USER_PROMPT", userPrompt
            ));
            String raw = claudeClient.ask(systemPrompt, userPrompt, List.of(), 0.2, 300);
            sql = stripSqlFences(raw.trim());
        } catch (Exception e) {
            log.warn("Smart SQL generation failed — falling back to ALL: {}", e.getMessage());
            return loadAll();
        }

        try {
            QueryResult result = repository.executeScopedQuery(sql, SCOPED_MAX_ROWS);
            List<DenormRow> rows = mapRows(result.rows());
            if (rows.isEmpty()) {
                log.warn("Smart SQL returned 0 rows — falling back to ALL. sql={}", sql);
                return loadAll();
            }
            log.info("Smart SQL scoping: {} rows (vs full portfolio). sql={}", rows.size(), sql);
            String promptString = buildPromptString(rows, result.truncated());
            return new ScopedData(rows, rows.size(), result.truncated(), sql, promptString);
        } catch (Exception e) {
            log.warn("Smart SQL execution failed — falling back to ALL: {} sql={}", e.getMessage(), sql);
            return loadAll();
        }
    }

    private ScopedData loadAll() {
        log.info("ScopedDataLoader loading ALL portfolio");
        QueryResult result = repository.executeScopedQuery(ALL_PORTFOLIO_SQL, SCOPED_MAX_ROWS);
        List<DenormRow> rows = mapRows(result.rows());
        String promptString = buildPromptString(rows, result.truncated());
        return new ScopedData(rows, rows.size(), result.truncated(), ALL_PORTFOLIO_SQL.trim(), promptString);
    }

    private ScopedData loadScoped(Scope scope, String userPrompt) {
        String sql;
        try {
            String systemPrompt = promptLoader.render("scoped-data-sql", Map.of(
                    "SCHEMA", schemaProvider.getSchemaAsPromptString(),
                    "SCOPE_TYPE", scope.type().name(),
                    "SCOPE_VALUE", scope.value() == null ? "" : scope.value(),
                    "USER_PROMPT", userPrompt == null ? "" : userPrompt
            ));
            String raw = claudeClient.ask(systemPrompt, userPrompt == null ? "" : userPrompt, List.of(), 0.2);
            sql = stripSqlFences(raw.trim());
        } catch (Exception e) {
            log.warn("Scoped SQL generation failed for scope {}:{} — falling back to ALL portfolio. {}",
                    scope.type(), scope.value(), e.getMessage());
            return loadAll();
        }

        try {
            QueryResult result = repository.executeScopedQuery(sql, SCOPED_MAX_ROWS);
            List<DenormRow> rows = mapRows(result.rows());
            if (rows.isEmpty()) {
                log.warn("Scoped query returned 0 rows — falling back to ALL. sql={}", sql);
                return loadAll();
            }
            String promptString = buildPromptString(rows, result.truncated());
            return new ScopedData(rows, rows.size(), result.truncated(), sql, promptString);
        } catch (InvalidSqlException | QueryExecutionException e) {
            log.warn("Scoped SQL failed ({}) — falling back to ALL. sql={}", e.getMessage(), sql);
            return loadAll();
        }
    }

    private List<DenormRow> mapRows(List<Map<String, Object>> raw) {
        List<DenormRow> out = new ArrayList<>(raw.size());
        for (Map<String, Object> r : raw) {
            String company = asString(r.get("company"));
            String product = firstNonBlank(asString(r.get("product_name")), asString(r.get("product")));
            String ingredientSku = asString(r.get("ingredient_sku"));
            String ingredientName = firstNonBlank(asString(r.get("ingredient_name")),
                    extractIngredientSlug(ingredientSku), ingredientSku);
            String supplier = asString(r.get("supplier"));
            String country = asString(r.get("country"));
            String category = asString(r.get("category"));
            Double percentage = asDouble(r.get("percentage"));
            String notes = asString(r.get("notes"));
            out.add(new DenormRow(company, product, ingredientName, category, supplier, country, percentage, notes));
        }
        return out;
    }

    private String buildPromptString(List<DenormRow> rows, boolean truncated) {
        Map<String, List<DenormRow>> byCompany = new LinkedHashMap<>();
        for (DenormRow row : rows) {
            byCompany.computeIfAbsent(row.company() == null ? "(unknown)" : row.company(),
                    k -> new ArrayList<>()).add(row);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Portfolio rows: ").append(rows.size());
        if (truncated) {
            sb.append(" (truncated — more exist)");
        }
        sb.append("\n\n");

        for (Map.Entry<String, List<DenormRow>> e : byCompany.entrySet()) {
            if (sb.length() > PROMPT_STRING_CAP) {
                sb.append("... [truncated at ").append(PROMPT_STRING_CAP).append(" chars]\n");
                break;
            }
            sb.append("## Company: ").append(e.getKey()).append("\n");
            Map<String, List<DenormRow>> byProduct = new LinkedHashMap<>();
            for (DenormRow row : e.getValue()) {
                byProduct.computeIfAbsent(row.product() == null ? "(unknown)" : row.product(),
                        k -> new ArrayList<>()).add(row);
            }
            for (Map.Entry<String, List<DenormRow>> p : byProduct.entrySet()) {
                sb.append("  Product: ").append(p.getKey()).append("\n");
                for (DenormRow row : p.getValue()) {
                    if (sb.length() > PROMPT_STRING_CAP) break;
                    sb.append("    - ").append(nullToDash(row.ingredient()));
                    if (row.supplier() != null) {
                        sb.append(" [supplier=").append(row.supplier());
                        if (row.country() != null) sb.append(", ").append(row.country());
                        sb.append("]");
                    }
                    sb.append("\n");
                }
            }
            sb.append("\n");
        }
        if (sb.length() > PROMPT_STRING_CAP) {
            return sb.substring(0, PROMPT_STRING_CAP) + "\n... [truncated]";
        }
        return sb.toString();
    }

    private String stripSqlFences(String s) {
        Matcher m = SQL_FENCE.matcher(s);
        if (m.matches()) return m.group(1).trim();
        return s;
    }

    private String extractIngredientSlug(String sku) {
        if (sku == null) return null;
        Matcher m = INGREDIENT_SLUG.matcher(sku);
        if (m.matches()) return m.group(1);
        return null;
    }

    private String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private Double asDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private String nullToDash(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }
}
