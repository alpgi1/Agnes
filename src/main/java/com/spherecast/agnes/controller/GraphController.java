package com.spherecast.agnes.controller;

import com.spherecast.agnes.dto.GraphResponse;
import com.spherecast.agnes.service.GraphService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/graph")
public class GraphController {

    private final GraphService graphService;

    public GraphController(GraphService graphService) {
        this.graphService = graphService;
    }

    @GetMapping("/company-supplier")
    public GraphResponse companySupplier(
            @RequestParam(required = false) Integer companyId,
            @RequestParam(required = false) Integer supplierId) {
        return graphService.getCompanySupplierGraph(companyId, supplierId);
    }

    @GetMapping("/company-product")
    public GraphResponse companyProduct(
            @RequestParam(required = false) Integer companyId) {
        return graphService.getCompanyProductGraph(companyId);
    }

    @GetMapping("/product-supplier")
    public GraphResponse productSupplier() {
        return graphService.getProductSupplierGraph();
    }

    @GetMapping("/companies")
    public List<Map<String, Object>> companies() {
        return graphService.getCompanies();
    }

    @GetMapping("/suppliers")
    public List<Map<String, Object>> suppliers() {
        return graphService.getSuppliers();
    }
}
