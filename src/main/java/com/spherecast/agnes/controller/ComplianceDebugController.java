package com.spherecast.agnes.controller;

import com.spherecast.agnes.config.ExternalApisConfig;
import com.spherecast.agnes.service.compliance.ComplianceLookupService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/debug")
public class ComplianceDebugController {

    private final ComplianceLookupService lookupService;
    private final ExternalApisConfig externalApisConfig;

    public ComplianceDebugController(ComplianceLookupService lookupService,
                                     ExternalApisConfig externalApisConfig) {
        this.lookupService = lookupService;
        this.externalApisConfig = externalApisConfig;
    }

    @GetMapping("/compliance-config")
    public Map<String, Object> complianceConfig() {
        return Map.of(
                "iherbStubbed", !externalApisConfig.isConfigured(),
                "regulationLoaded", lookupService.isLoaded(),
                "articleCount", lookupService.articleCount(),
                "annexCount", lookupService.annexCount()
        );
    }
}
