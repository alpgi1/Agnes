package com.spherecast.agnes.service.compliance;

import com.spherecast.agnes.config.ComplianceConfig;
import com.spherecast.agnes.handler.optimizers.ComplianceRelevance;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ComplianceLookupService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceLookupService.class);

    private final ComplianceConfig config;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    private RegulationJson regulation;
    private RegulationJson regulation2;

    public ComplianceLookupService(ComplianceConfig config,
                                   ResourceLoader resourceLoader,
                                   ObjectMapper objectMapper) {
        this.config = config;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void load() {
        this.regulation = loadFile(config.regulationPath());
        // Load the second regulation file (annexes)
        String path2 = config.regulationPath().replace(".json", "_2.json");
        this.regulation2 = loadFile(path2);

        int articles = regulation.articles().size() + regulation2.articles().size();
        int annexes = regulation.annexes().size() + regulation2.annexes().size();
        log.info("Loaded EU regulation: {} articles, {} annexes", articles, annexes);
    }

    private RegulationJson loadFile(String path) {
        try (InputStream is = resourceLoader.getResource(path).getInputStream()) {
            return objectMapper.readValue(is, RegulationJson.class);
        } catch (Exception e) {
            log.warn("Failed to load EU regulation JSON from {}: {}", path, e.getMessage());
            return new RegulationJson(
                    new RegulationJson.Regulation("EU 1169/2011", "unavailable", "unavailable", "unavailable"),
                    List.of(), List.of()
            );
        }
    }

    /** Returns regulation articles whose relevance_tags overlap with the requested tags. */
    public List<RegulationJson.Article> articlesByTags(Set<String> tags) {
        List<RegulationJson.Article> result = new ArrayList<>();
        for (RegulationJson.Article art : allArticles()) {
            if (art.relevanceTags() == null) continue;
            for (String tag : art.relevanceTags()) {
                if (tags.contains(tag)) {
                    result.add(art);
                    break;
                }
            }
        }
        return result;
    }

    /** Returns a specific article by number, or null. */
    public RegulationJson.Article articleByNumber(int number) {
        return allArticles().stream()
                .filter(a -> a.number() == number)
                .findFirst()
                .orElse(null);
    }

    /** Returns Annex II (allergens), fully populated. */
    public RegulationJson.Annex allergenAnnex() {
        return allAnnexes().stream()
                .filter(a -> "allergen_list".equals(a.type()) || "Annex II".equals(a.id()))
                .findFirst()
                .orElse(null);
    }

    /** Returns true if a given ingredient name matches any EU-14 allergen entry. */
    public Optional<RegulationJson.Annex.AllergenItem> findAllergenMatch(String ingredientName) {
        RegulationJson.Annex annex = allergenAnnex();
        if (annex == null || annex.items() == null) return Optional.empty();
        String lc = ingredientName.toLowerCase();
        for (RegulationJson.Annex.AllergenItem item : annex.items()) {
            if (item.name() != null && lc.contains(item.name().toLowerCase())) return Optional.of(item);
            if (item.examples() != null) {
                for (String ex : item.examples()) {
                    if (lc.contains(ex.toLowerCase())) return Optional.of(item);
                }
            }
            if (item.details() != null && item.details().toLowerCase().contains(lc)) return Optional.of(item);
        }
        return Optional.empty();
    }

    /**
     * Main entry point: given a finding's compliance_relevance flags, return
     * relevant articles + allergen hits as LookupResults.
     */
    public List<LookupResult> lookupForFinding(ComplianceRelevance cr) {
        if (cr == null) return List.of();

        Set<String> tags = buildTagSet(cr);
        List<LookupResult> results = new ArrayList<>();

        // Fetch articles by tags
        List<RegulationJson.Article> matchedArticles = articlesByTags(tags);
        // Sort: articles matching more tags first
        matchedArticles.sort((a, b) -> {
            long aHits = a.relevanceTags() == null ? 0 :
                    a.relevanceTags().stream().filter(tags::contains).count();
            long bHits = b.relevanceTags() == null ? 0 :
                    b.relevanceTags().stream().filter(tags::contains).count();
            return Long.compare(bHits, aHits);
        });

        for (RegulationJson.Article art : matchedArticles) {
            String url = "https://eur-lex.europa.eu/eli/reg/2011/1169/oj#art_" + art.number();
            String body = art.summary();
            if (body != null && body.length() > 500) body = body.substring(0, 500) + "...";
            results.add(new LookupResult(
                    "EU 1169/2011 Article " + art.number(),
                    art.title(),
                    body,
                    url
            ));
        }

        // Allergen-specific lookups
        if ((cr.allergenChanges() != null && !cr.allergenChanges().isEmpty())
                || Boolean.TRUE.equals(cr.labelClaimRisk())) {
            // Also check ingredient keywords
            if (cr.ingredientKeywordsForLookup() != null) {
                for (String kw : cr.ingredientKeywordsForLookup()) {
                    findAllergenMatch(kw).ifPresent(item ->
                            results.add(new LookupResult(
                                    "EU 1169/2011 Annex II item " + item.number(),
                                    item.name(),
                                    item.details(),
                                    "https://eur-lex.europa.eu/eli/reg/2011/1169/oj#anx_II"
                            ))
                    );
                }
            }
        }

        // Cap at 5 results
        if (results.size() > 5) {
            return results.subList(0, 5);
        }
        return results;
    }

    private Set<String> buildTagSet(ComplianceRelevance cr) {
        Set<String> tags = new HashSet<>();
        tags.add("mandatory_info");
        if (cr.allergenChanges() != null && !cr.allergenChanges().isEmpty()) tags.add("allergens");
        if (cr.affectedClaims() != null && !cr.affectedClaims().isEmpty()) {
            tags.add("claims");
            tags.add("labelling");
            tags.add("ingredients");
        }
        if (Boolean.TRUE.equals(cr.labelClaimRisk())) {
            tags.add("claims");
            tags.add("labelling");
        }
        if (cr.animalOriginChanges() != null && !cr.animalOriginChanges().isEmpty()) {
            tags.add("animal_origin");
        }
        if (Boolean.TRUE.equals(cr.novelFoodRisk())) tags.add("novel_food");
        if (Boolean.TRUE.equals(cr.changesIngredientChemistry())) {
            tags.add("ingredients");
            tags.add("name_of_food");
        }
        return tags;
    }

    private List<RegulationJson.Article> allArticles() {
        List<RegulationJson.Article> all = new ArrayList<>();
        if (regulation != null && regulation.articles() != null) all.addAll(regulation.articles());
        if (regulation2 != null && regulation2.articles() != null) all.addAll(regulation2.articles());
        return all;
    }

    private List<RegulationJson.Annex> allAnnexes() {
        List<RegulationJson.Annex> all = new ArrayList<>();
        if (regulation != null && regulation.annexes() != null) all.addAll(regulation.annexes());
        if (regulation2 != null && regulation2.annexes() != null) all.addAll(regulation2.annexes());
        return all;
    }

    public record LookupResult(
            String sourceRef,
            String summary,
            String body,
            String url
    ) {}

    // --- Accessors for testing / debug endpoint ---
    public int articleCount() { return allArticles().size(); }
    public int annexCount() { return allAnnexes().size(); }
    public boolean isLoaded() { return regulation != null; }
}
