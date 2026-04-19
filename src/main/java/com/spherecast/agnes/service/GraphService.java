package com.spherecast.agnes.service;

import com.spherecast.agnes.dto.GraphEdge;
import com.spherecast.agnes.dto.GraphNode;
import com.spherecast.agnes.dto.GraphResponse;
import com.spherecast.agnes.dto.GraphResponse.GraphMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GraphService {

    private static final Logger log = LoggerFactory.getLogger(GraphService.class);

    private final JdbcTemplate jdbcTemplate;

    public GraphService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ── Company ↔ Supplier ─────────────────────────────────────────────

    public GraphResponse getCompanySupplierGraph(Integer companyId, Integer supplierId) {
        long start = System.nanoTime();
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();

        StringBuilder whereClause = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (companyId != null) {
            whereClause.append(" AND c.Id = ? ");
            args.add(companyId);
        }
        if (supplierId != null) {
            whereClause.append(" AND s.Id = ? ");
            args.add(supplierId);
        }

        String baseQuery = """
                FROM Company c
                  JOIN Product p_fg ON p_fg.CompanyId = c.Id AND p_fg.Type = 'finished-good'
                  JOIN BOM b ON b.ProducedProductId = p_fg.Id
                  JOIN BOM_Component bc ON bc.BOMId = b.Id
                  JOIN Product p_rm ON p_rm.Id = bc.ConsumedProductId
                  JOIN Supplier_Product sp ON sp.ProductId = p_rm.Id
                  JOIN Supplier s ON s.Id = sp.SupplierId
                """ + whereClause.toString();

        // Nodes: Companies
        jdbcTemplate.query(
                "SELECT DISTINCT c.Id, c.Name " + baseQuery,
                args.toArray(),
                (rs) -> {
                    nodes.add(new GraphNode(
                            "company-" + rs.getInt("Id"),
                            rs.getString("Name"),
                            "company",
                            Map.of()
                    ));
                }
        );

        // Nodes: Suppliers
        jdbcTemplate.query(
                "SELECT DISTINCT s.Id, s.Name " + baseQuery,
                args.toArray(),
                (rs) -> {
                    nodes.add(new GraphNode(
                            "supplier-" + rs.getInt("Id"),
                            rs.getString("Name"),
                            "supplier",
                            Map.of()
                    ));
                }
        );

        // Edges: Company sources_from Supplier (transitive via Products)
        jdbcTemplate.query(
                "SELECT c.Id AS company_id, s.Id AS supplier_id, COUNT(DISTINCT p_rm.Id) AS product_count "
                        + baseQuery + " GROUP BY c.Id, s.Id",
                args.toArray(),
                (rs) -> {
                    int productCount = rs.getInt("product_count");
                    edges.add(new GraphEdge(
                            "company-" + rs.getInt("company_id"),
                            "supplier-" + rs.getInt("supplier_id"),
                            "sources_from",
                            Map.of("product_count", productCount)
                    ));
                }
        );

        long durationMs = (System.nanoTime() - start) / 1_000_000L;
        log.info("Company-Supplier graph (companyId={}, supplierId={}): {} nodes, {} edges ({}ms)",
                companyId, supplierId, nodes.size(), edges.size(), durationMs);
        return new GraphResponse(nodes, edges,
                new GraphMeta("company-supplier", nodes.size(), edges.size(), durationMs));
    }

    // ── Company ↔ Product ──────────────────────────────────────────────

    public GraphResponse getCompanyProductGraph(Integer companyId) {
        long start = System.nanoTime();
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();

        String companyFilter = companyId != null ? " WHERE c.Id = " + companyId : "";
        String productFilter = companyId != null ? " AND p.CompanyId = " + companyId : "";

        // Nodes: Companies
        jdbcTemplate.query(
                "SELECT DISTINCT c.Id, c.Name FROM Company c" + companyFilter,
                (rs) -> {
                    nodes.add(new GraphNode(
                            "company-" + rs.getInt("Id"),
                            rs.getString("Name"),
                            "company",
                            Map.of()
                    ));
                }
        );

        // Nodes: Finished Goods
        jdbcTemplate.query(
                "SELECT p.Id, p.SKU FROM Product p WHERE p.Type = 'finished-good'" + productFilter,
                (rs) -> {
                    String sku = rs.getString("SKU");
                    nodes.add(new GraphNode(
                            "fg-" + rs.getInt("Id"),
                            sku != null && !sku.isBlank() ? sku : "Unknown FG",
                            "finished_good",
                            Map.of("sku", sku != null ? sku : "")
                    ));
                }
        );

        // Edges: Company owns Finished Good
        jdbcTemplate.query(
                "SELECT c.Id AS company_id, p.Id AS product_id FROM Product p"
                        + " JOIN Company c ON c.Id = p.CompanyId WHERE p.Type = 'finished-good'" + productFilter,
                (rs) -> {
                    edges.add(new GraphEdge(
                            "company-" + rs.getInt("company_id"),
                            "fg-" + rs.getInt("product_id"),
                            "owns",
                            Map.of()
                    ));
                }
        );

        // Nodes: Raw Materials (via BOM, scoped to the company's finished goods)
        String rmQuery = companyId != null
                ? """
                  SELECT DISTINCT p_rm.Id, p_rm.SKU
                  FROM Product p_rm
                    JOIN BOM_Component bc ON bc.ConsumedProductId = p_rm.Id
                    JOIN BOM b ON b.Id = bc.BOMId
                    JOIN Product p_fg ON p_fg.Id = b.ProducedProductId AND p_fg.CompanyId = ?
                  """
                : """
                  SELECT DISTINCT p_rm.Id, p_rm.SKU
                  FROM Product p_rm
                    JOIN BOM_Component bc ON bc.ConsumedProductId = p_rm.Id
                  """;

        Object[] rmArgs = companyId != null ? new Object[]{companyId} : new Object[]{};
        jdbcTemplate.query(rmQuery, rmArgs,
                (rs) -> {
                    String sku = rs.getString("SKU");
                    nodes.add(new GraphNode(
                            "rm-" + rs.getInt("Id"),
                            sku != null && !sku.isBlank() ? sku : "Unknown RM",
                            "raw_material",
                            Map.of("sku", sku != null ? sku : "")
                    ));
                }
        );

        // Edges: Finished Good uses Raw Material (scoped)
        String usesQuery = companyId != null
                ? """
                  SELECT b.ProducedProductId AS fg_id, bc.ConsumedProductId AS rm_id
                  FROM BOM b
                    JOIN BOM_Component bc ON bc.BOMId = b.Id
                    JOIN Product p_fg ON p_fg.Id = b.ProducedProductId AND p_fg.CompanyId = ?
                  """
                : """
                  SELECT b.ProducedProductId AS fg_id, bc.ConsumedProductId AS rm_id
                  FROM BOM b
                    JOIN BOM_Component bc ON bc.BOMId = b.Id
                  """;

        Object[] usesArgs = companyId != null ? new Object[]{companyId} : new Object[]{};
        jdbcTemplate.query(usesQuery, usesArgs,
                (rs) -> {
                    edges.add(new GraphEdge(
                            "fg-" + rs.getInt("fg_id"),
                            "rm-" + rs.getInt("rm_id"),
                            "uses",
                            Map.of()
                    ));
                }
        );

        long durationMs = (System.nanoTime() - start) / 1_000_000L;
        log.info("Company-Product graph (companyId={}): {} nodes, {} edges ({}ms)",
                companyId, nodes.size(), edges.size(), durationMs);
        return new GraphResponse(nodes, edges,
                new GraphMeta("company-product", nodes.size(), edges.size(), durationMs));
    }

    // ── Product ↔ Supplier ─────────────────────────────────────────────

    public GraphResponse getProductSupplierGraph() {
        long start = System.nanoTime();
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();

        String baseQuery = """
                FROM Product p
                  JOIN Supplier_Product sp ON sp.ProductId = p.Id
                  JOIN Supplier s ON s.Id = sp.SupplierId
                """;

        // Nodes: Raw Materials that have a supplier
        jdbcTemplate.query(
                "SELECT DISTINCT p.Id, p.SKU " + baseQuery,
                (rs) -> {
                    String sku = rs.getString("SKU");
                    nodes.add(new GraphNode(
                            "rm-" + rs.getInt("Id"),
                            sku != null && !sku.isBlank() ? sku : "Unknown RM",
                            "raw_material",
                            Map.of("sku", sku != null ? sku : "")
                    ));
                }
        );

        // Nodes: Suppliers
        jdbcTemplate.query(
                "SELECT DISTINCT s.Id, s.Name " + baseQuery,
                (rs) -> {
                    nodes.add(new GraphNode(
                            "supplier-" + rs.getInt("Id"),
                            rs.getString("Name"),
                            "supplier",
                            Map.of()
                    ));
                }
        );

        // Edges: Raw Material supplied_by Supplier
        jdbcTemplate.query(
                "SELECT sp.ProductId AS rm_id, sp.SupplierId AS supplier_id " + baseQuery,
                (rs) -> {
                    edges.add(new GraphEdge(
                            "rm-" + rs.getInt("rm_id"),
                            "supplier-" + rs.getInt("supplier_id"),
                            "supplied_by",
                            Map.of()
                    ));
                }
        );

        long durationMs = (System.nanoTime() - start) / 1_000_000L;
        log.info("Product-Supplier graph: {} nodes, {} edges ({}ms)", nodes.size(), edges.size(), durationMs);
        return new GraphResponse(nodes, edges,
                new GraphMeta("product-supplier", nodes.size(), edges.size(), durationMs));
    }

    // ── Companies and Suppliers list (for dropdown) ────────────────────

    public List<Map<String, Object>> getCompanies() {
        return jdbcTemplate.queryForList("SELECT Id, Name FROM Company ORDER BY Name");
    }

    public List<Map<String, Object>> getSuppliers() {
        return jdbcTemplate.queryForList("SELECT Id, Name FROM Supplier ORDER BY Name");
    }
}
