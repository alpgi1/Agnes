package com.spherecast.agnes.service.compliance;

import com.spherecast.agnes.config.ExternalApisConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class IHerbClient {

    private static final Logger log = LoggerFactory.getLogger(IHerbClient.class);

    private final ExternalApisConfig config;

    public IHerbClient(ExternalApisConfig config) {
        this.config = config;
    }

    public record IHerbProduct(
            String productId, String title, String brand,
            List<String> certifications, String ingredientSource,
            Double rating, Integer reviewCount, String url
    ) {}

    public record IHerbSearchResult(
            String keyword, List<IHerbProduct> products,
            boolean fromStub, String stubReason
    ) {}

    public IHerbSearchResult search(String keyword) {
        String normalized = keyword.trim().toLowerCase();
        if (!config.isConfigured()) {
            return fromStub(normalized, "IHERB_RAPIDAPI_KEY not configured");
        }
        // Real API path would go here; for now always stub
        log.debug("iHerb real API not yet wired — using stub for '{}'", normalized);
        return fromStub(normalized, "real API path not yet implemented");
    }

    /**
     * Batch lookup: dedupes keywords, calls search() once per unique keyword.
     */
    public Map<String, IHerbSearchResult> searchAll(Collection<String> keywords) {
        return keywords.stream()
                .map(String::trim)
                .map(String::toLowerCase)
                .distinct()
                .collect(Collectors.toMap(k -> k, this::search));
    }

    private IHerbSearchResult fromStub(String keyword, String reason) {
        List<IHerbProduct> products = STUB_CATALOG.getOrDefault(keyword, List.of());
        if (products.isEmpty()) {
            // Try partial match
            products = STUB_CATALOG.entrySet().stream()
                    .filter(e -> keyword.contains(e.getKey()) || e.getKey().contains(keyword))
                    .findFirst()
                    .map(Map.Entry::getValue)
                    .orElse(List.of());
        }
        return new IHerbSearchResult(keyword, products, true, reason);
    }

    // ---- Stub catalog ----

    private static final Map<String, List<IHerbProduct>> STUB_CATALOG = Map.ofEntries(
            Map.entry("vitamin d3", List.of(
                    new IHerbProduct("stub-1", "Now Foods D3 5000 IU", "Now Foods",
                            List.of("Non-GMO", "Kosher", "Halal"), "lanolin", 4.8, 15234,
                            "https://www.iherb.com/pr/now-foods-d3"),
                    new IHerbProduct("stub-2", "Garden of Life Vegan D3", "Garden of Life",
                            List.of("Non-GMO", "Vegan", "Organic"), "lichen", 4.7, 8921,
                            "https://www.iherb.com/pr/gol-vegan-d3"),
                    new IHerbProduct("stub-3", "Sports Research D3", "Sports Research",
                            List.of("Non-GMO"), "lanolin", 4.8, 22011,
                            "https://www.iherb.com/pr/sports-research-d3")
            )),
            Map.entry("cholecalciferol", List.of(
                    new IHerbProduct("stub-4", "Doctor's Best Cholecalciferol", "Doctor's Best",
                            List.of("Non-GMO", "Gluten-Free"), "lanolin", 4.7, 5812,
                            "https://www.iherb.com/pr/dbest-d3"),
                    new IHerbProduct("stub-5", "Nordic Naturals D3", "Nordic Naturals",
                            List.of("Non-GMO", "Kosher"), "lanolin", 4.8, 3421,
                            "https://www.iherb.com/pr/nordic-d3")
            )),
            Map.entry("magnesium oxide", List.of(
                    new IHerbProduct("stub-6", "Nature's Way Magnesium Oxide", "Nature's Way",
                            List.of("Vegetarian"), null, 4.5, 2134,
                            "https://www.iherb.com/pr/nw-mg-oxide"),
                    new IHerbProduct("stub-7", "Solgar Magnesium Oxide", "Solgar",
                            List.of("Kosher", "Vegan", "Gluten-Free"), null, 4.6, 1823,
                            "https://www.iherb.com/pr/solgar-mg-oxide")
            )),
            Map.entry("magnesium glycinate", List.of(
                    new IHerbProduct("stub-8", "Doctor's Best Magnesium Glycinate", "Doctor's Best",
                            List.of("Non-GMO", "Gluten-Free", "Vegan"), null, 4.8, 18923,
                            "https://www.iherb.com/pr/dbest-mg-glycinate"),
                    new IHerbProduct("stub-9", "Thorne Magnesium Bisglycinate", "Thorne",
                            List.of("Gluten-Free"), null, 4.7, 4521,
                            "https://www.iherb.com/pr/thorne-mg-bisglycinate")
            )),
            Map.entry("vitamin c", List.of(
                    new IHerbProduct("stub-10", "Now Foods Vitamin C-1000", "Now Foods",
                            List.of("Non-GMO", "Kosher", "Vegan"), null, 4.8, 26412,
                            "https://www.iherb.com/pr/now-vit-c")
            )),
            Map.entry("ascorbic acid", List.of(
                    new IHerbProduct("stub-11", "NutriBiotic Ascorbic Acid", "NutriBiotic",
                            List.of("Non-GMO", "Vegan"), null, 4.7, 3821,
                            "https://www.iherb.com/pr/nutribiotic-ascorbic")
            )),
            Map.entry("magnesium stearate", List.of(
                    new IHerbProduct("stub-12", "NOW Vegetable Magnesium Stearate", "Now Foods",
                            List.of("Vegan", "Non-GMO"), "vegetable", null, null, null)
            )),
            Map.entry("silicon dioxide", List.of(
                    new IHerbProduct("stub-13", "Generic Silicon Dioxide excipient",
                            "(multiple manufacturers)", List.of("GRAS"), null, null, null, null)
            )),
            Map.entry("zinc", List.of(
                    new IHerbProduct("stub-14", "Now Foods Zinc Picolinate", "Now Foods",
                            List.of("Non-GMO", "Vegan"), null, 4.7, 11234,
                            "https://www.iherb.com/pr/now-zinc")
            )),
            Map.entry("calcium", List.of(
                    new IHerbProduct("stub-15", "Nature Made Calcium 600 mg", "Nature Made",
                            List.of("USP Verified"), null, 4.6, 7823,
                            "https://www.iherb.com/pr/nm-calcium")
            )),
            Map.entry("tocopherol", List.of(
                    new IHerbProduct("stub-16", "Now Foods Natural E-400", "Now Foods",
                            List.of("Non-GMO"), "soy-free sunflower", 4.7, 5621,
                            "https://www.iherb.com/pr/now-vit-e")
            ))
    );
}
