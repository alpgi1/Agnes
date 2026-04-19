package com.spherecast.agnes.service;

import com.spherecast.agnes.dto.GraphEdge;
import com.spherecast.agnes.dto.GraphNode;
import com.spherecast.agnes.dto.GraphResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GraphServiceTest {

    @Autowired
    private GraphService graphService;

    // ── Company ↔ Supplier ────────────────────────────────────────────

    @Test
    void companySupplierGraph_returnsNodesAndEdges() {
        GraphResponse response = graphService.getCompanySupplierGraph(null, null);

        assertThat(response.meta().view()).isEqualTo("company-supplier");
        assertThat(response.nodes()).isNotEmpty();
        assertThat(response.edges()).isNotEmpty();

        // Node types
        Set<String> nodeTypes = response.nodes().stream().map(GraphNode::type).collect(Collectors.toSet());
        assertThat(nodeTypes).containsExactlyInAnyOrder("company", "supplier");

        // All node IDs follow prefix convention
        response.nodes().forEach(n -> {
            if ("company".equals(n.type())) {
                assertThat(n.id()).startsWith("company-");
            } else {
                assertThat(n.id()).startsWith("supplier-");
            }
        });

        // Edges reference valid node IDs
        Set<String> nodeIds = response.nodes().stream().map(GraphNode::id).collect(Collectors.toSet());
        response.edges().forEach(e -> {
            assertThat(nodeIds).contains(e.from());
            assertThat(nodeIds).contains(e.to());
            assertThat(e.type()).isEqualTo("sources_from");
            assertThat(e.properties()).containsKey("product_count");
        });

        assertThat(response.meta().nodeCount()).isEqualTo(response.nodes().size());
        assertThat(response.meta().edgeCount()).isEqualTo(response.edges().size());
    }

    // ── Company ↔ Product ─────────────────────────────────────────────

    @Test
    void companyProductGraph_filteredByCompany_returnsScoped() {
        // First get companies
        List<Map<String, Object>> companies = graphService.getCompanies();
        assertThat(companies).isNotEmpty();

        int firstCompanyId = ((Number) companies.get(0).get("Id")).intValue();
        GraphResponse response = graphService.getCompanyProductGraph(firstCompanyId);

        assertThat(response.meta().view()).isEqualTo("company-product");
        assertThat(response.nodes()).isNotEmpty();

        // Should have exactly one company node
        long companyCount = response.nodes().stream()
                .filter(n -> "company".equals(n.type())).count();
        assertThat(companyCount).isEqualTo(1);

        // Should have finished goods and raw materials
        Set<String> nodeTypes = response.nodes().stream().map(GraphNode::type).collect(Collectors.toSet());
        assertThat(nodeTypes).contains("company", "finished_good");

        // Edge types: owns and uses
        Set<String> edgeTypes = response.edges().stream().map(GraphEdge::type).collect(Collectors.toSet());
        assertThat(edgeTypes).containsAnyOf("owns", "uses");
    }

    @Test
    void companyProductGraph_unfiltered_returnsAll() {
        GraphResponse response = graphService.getCompanyProductGraph(null);

        assertThat(response.meta().view()).isEqualTo("company-product");

        long companyCount = response.nodes().stream()
                .filter(n -> "company".equals(n.type())).count();
        // Should have multiple companies when unfiltered
        assertThat(companyCount).isGreaterThan(1);
    }

    // ── Product ↔ Supplier ────────────────────────────────────────────

    @Test
    void productSupplierGraph_returnsBipartiteGraph() {
        GraphResponse response = graphService.getProductSupplierGraph();

        assertThat(response.meta().view()).isEqualTo("product-supplier");
        assertThat(response.nodes()).isNotEmpty();
        assertThat(response.edges()).isNotEmpty();

        // Node types: only raw_material and supplier
        Set<String> nodeTypes = response.nodes().stream().map(GraphNode::type).collect(Collectors.toSet());
        assertThat(nodeTypes).containsExactlyInAnyOrder("raw_material", "supplier");

        // All edges are supplied_by
        response.edges().forEach(e ->
                assertThat(e.type()).isEqualTo("supplied_by"));

        // Edges reference valid node IDs
        Set<String> nodeIds = response.nodes().stream().map(GraphNode::id).collect(Collectors.toSet());
        response.edges().forEach(e -> {
            assertThat(nodeIds).contains(e.from());
            assertThat(nodeIds).contains(e.to());
        });
    }

    // ── Companies list ────────────────────────────────────────────────

    @Test
    void getCompanies_returnsNonEmptyList() {
        List<Map<String, Object>> companies = graphService.getCompanies();

        assertThat(companies).isNotEmpty();
        assertThat(companies.get(0)).containsKey("Id");
        assertThat(companies.get(0)).containsKey("Name");
    }
}
