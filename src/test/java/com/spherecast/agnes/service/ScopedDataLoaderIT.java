package com.spherecast.agnes.service;

import com.spherecast.agnes.handler.RouterDecision.Scope;
import com.spherecast.agnes.handler.RouterDecision.Scope.ScopeType;
import com.spherecast.agnes.handler.optimizers.ScopedData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ScopedDataLoaderIT {

    @Autowired
    ScopedDataLoader loader;

    @Test
    void allScopeLoadsDenormalizedPortfolio() {
        ScopedData data = loader.load(Scope.all(), "optimize everything");

        assertThat(data).isNotNull();
        assertThat(data.totalRows()).isGreaterThan(0);
        assertThat(data.sqlUsed()).containsIgnoringCase("FROM Company");
        assertThat(data.asPromptString()).containsIgnoringCase("Company:");
        assertThat(data.rows().get(0).company()).isNotBlank();
    }

    @Test
    void promptStringIsCapped() {
        ScopedData data = loader.load(Scope.all(), "optimize everything");

        assertThat(data.asPromptString().length()).isLessThanOrEqualTo(61_000);
    }
}
