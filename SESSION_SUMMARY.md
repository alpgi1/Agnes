# Agnes — Session Summary (Phases 1–10)

**Date:** 2026-04-19  
**Stack:** Spring Boot 4.0.0 · Java 21 · SQLite (read-only) · Anthropic claude-sonnet-4-6  
**Repo:** https://github.com/alpgi1/Agnes.git, branch `master`  
**Last commit:** 5a8a1fd  
**Test count:** 111 total, 0 failures (unit + integration)  

---

## What is Agnes?

An AI-powered Supply Chain decision-support backend. It answers natural-language
questions about a CPG sourcing database and runs optimization analysis via four
optimizer types (Substitution, Consolidation, Reformulation, Complexity). All four
optimizers are real (no stubs). Every finding is verified by a ComplianceChecker
that produces verdicts (`compliant`, `uncertain`, `non-compliant`) backed by EU
regulation context and iHerb market evidence.

The database (`db.sqlite`, 180 KB, committed at repo root) contains ~61 companies,
finished-good products, BOMs, raw-material SKUs, and supplier links. **No prices,
quantities, or lead times** — Agnes works with structure and naming only.

**Actual DB schema** (verified via `SchemaProvider`):

| Table | Columns |
|-------|---------|
| Company | Id, Name |
| Product | Id, SKU, CompanyId, Type (`finished-good` / `raw-material`) |
| BOM | Id, ProducedProductId |
| BOM_Component | BOMId, ConsumedProductId (note: uppercase `BOMId`) |
| Supplier | Id, Name |
| Supplier_Product | SupplierId, ProductId |

Raw-material SKUs encode the ingredient slug, e.g. `RM-C1-vitamin-d3-cholecalciferol-67efce0f`.

---

## Environment notes

- **Maven path (macOS):** `~/.m2/wrapper/dists/apache-maven-3.9.14/db91789b/bin/mvn`
- **Maven path (Windows, IntelliJ-bundled):** `/c/Program Files/JetBrains/IntelliJ IDEA 2025.2.3/plugins/maven/lib/maven3/bin/mvn.cmd`
- **Java:** OpenJDK 25 (Windows: `~/.jdks/openjdk-25`)
- **Jackson version:** Spring Boot 4.0.0 ships Jackson **3.x** → package is
  `tools.jackson.databind.*` NOT `com.fasterxml.jackson.databind.*`.
  Annotations (`@JsonProperty`, `@JsonIgnoreProperties`, `@JsonCreator`) still in
  `com.fasterxml.jackson.annotation.*`.
- **API key:** stored in `.env` (gitignored). Load with `set -a && . .env && set +a`.
- Integration tests are guarded by `@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = "sk-ant-.+")` — they skip without the key.

---

## Phase 1 — Spring Boot Skeleton (commit 891ab1b)

**Files created:**
- `pom.xml` — Spring Boot 4.0.0, Java 21, sqlite-jdbc, Lombok, validation, cache, JDBC
- `AgnesApplication.java` — `@EnableCaching`, `@ConfigurationPropertiesScan`
- `config/ClaudeConfig.java` — record with `@ConfigurationProperties(prefix="claude")`, fields: `apiKey`, `model`, `maxTokens`, `baseUrl`, `anthropicVersion`
- `config/DatabaseConfig.java` — HikariCP pool pointing at `db.sqlite` with `setReadOnly(true)`
- `config/CorsConfig.java` — permissive CORS for dev
- `controller/AgnesController.java` — `GET /api/health`, `GET /api/health/config`
- `src/main/resources/application.yml` — claude config bound from env var `ANTHROPIC_API_KEY`

---

## Phase 2 — Read-Only DB Access (commit aef7a28)

**Key design decisions:**
- `SqlGuard` rejects anything that isn't `SELECT` or `WITH…SELECT` (whitelist approach, case-insensitive, strips comments)
- `SqlExecutor` caps results at **201 rows** (returns first 200 + `truncated=true`), 5 s timeout
- `SchemaProvider` generates a schema prompt string with table names, column types, row counts, and hard-coded semantic notes; cached via `@Cacheable("schema")`

**Files created:**
- `util/SqlGuard.java` + `util/InvalidSqlException.java`
- `service/SqlExecutor.java` + `service/QueryExecutionException.java` + `service/QueryResult.java`
- `service/SchemaProvider.java`
- `repository/AgnesRepository.java` — `executeQuery(sql)` = guard → execute
- `dto/DebugQueryRequest.java`
- `controller/GlobalExceptionHandler.java` — maps `InvalidSqlException` → 400, `QueryExecutionException` → 500
- Debug endpoints: `GET /api/debug/schema`, `POST /api/debug/query`

---

## Phase 3 — ClaudeClient (commit 4808cd1)

**Key design decisions:**
- Hand-rolled `RestClient` wrapper (no Anthropic SDK) — version-lock-free
- Retry: max 3 attempts, 500 ms → 1500 ms backoff on 429 / 5xx / IO
- Temperature defaults: `ask()` = 0.5, `askJson()` = 0.2
- API key guard: throws `ClaudeApiException(-1)` if key is blank or `"PLACEHOLDER"` — before any HTTP call
- `JsonExtractor`: handles raw JSON, fenced JSON (` ```json ... ``` `), and JSON embedded in prose
- Read timeout: **180 s** (bumped from 60 s in Phase 8 to support large portfolio analysis)
- Max tokens: **8192** (bumped from 4096 in Phase 8 to avoid truncated responses)

**Files created:**
- `dto/ChatMessage.java` — record with `user(…)` / `assistant(…)` factory methods
- `service/claude/ClaudeMessagesRequest.java` — Jackson-serializable request body (`@JsonInclude(NON_NULL)`)
- `service/claude/ClaudeMessagesResponse.java` — response with nested `ContentBlock` + `Usage` (`@JsonIgnoreProperties(ignoreUnknown=true)`)
- `service/claude/ClaudeApiException.java` — holds `statusCode` (-1 = no HTTP response) + `responseBody`
- `service/claude/JsonExtractionException.java`
- `service/claude/JsonExtractor.java` — `extractJson(String)` + `extractJson(String, Class<T>)`
- `service/claude/ClaudeClient.java` — `ask(…)`, `askJson(…)` variants; package-private constructor accepting `RestClient.Builder` for unit test injection
- `dto/DebugClaudeRequest.java`
- `GlobalExceptionHandler` extended: `ClaudeApiException` → 502, `JsonExtractionException` → 500
- Debug endpoint: `POST /api/debug/claude`

**ClaudeClient constructor design for tests:**
```java
@Autowired  // primary — used by Spring
public ClaudeClient(ClaudeConfig config, JsonExtractor jsonExtractor) { … }

// package-private — used by ClaudeClientTest via MockRestServiceServer
ClaudeClient(ClaudeConfig config, JsonExtractor jsonExtractor, RestClient.Builder builder) { … }
```
`MockRestServiceServer.bindTo(builder)` replaces the request factory before `build()`.

---

## Phase 4 — KnowledgeHandler (commit 0f82f4f)

**Pipeline:** user question → SQL (Claude #1, temp 0.2) → execute (SqlGuard + SqlExecutor) → one-retry repair if SQL fails → Markdown answer (Claude #2, temp 0.5)

**Prompt templates** (in `src/main/resources/prompts/`, loaded by `PromptLoader`):
- `knowledge-schema-to-sql.txt` — placeholders: `{{SCHEMA}}`, `{{HISTORY}}`, `{{QUESTION}}`
- `knowledge-data-to-answer.txt` — placeholders: `{{HISTORY}}`, `{{QUESTION}}`, `{{SQL}}`, `{{ROW_COUNT}}`, `{{TRUNCATED_NOTE}}`, `{{ROWS_JSON}}`
- `knowledge-sql-repair.txt` — placeholders: `{{SCHEMA}}`, `{{QUESTION}}`, `{{PREVIOUS_SQL}}`, `{{ERROR}}`

**Files created:**
- `service/PromptLoader.java` — `load(name)` (Cacheable) + `render(name, Map<String,String>)` with `{{KEY}}` regex substitution; WARN on missing key
- `dto/KnowledgeRequest.java` — `prompt`, `history` (nullable), `sessionId` (nullable, server generates UUID if absent)
- `dto/KnowledgeResponse.java` — `sessionId`, `markdown`, `sqlUsed`, `rowCount`, `truncated`, `durationMs`
- `handler/KnowledgeHandler.java`
- Endpoint: `POST /api/knowledge`

**Row cap for prompt:** repository caps at 200 rows; KnowledgeHandler further caps at 50 rows / 50 KB before passing to Claude.

**SQL repair bug fixed during testing:** original impl returned the bad SQL in `sqlUsed`; fixed by wrapping `executeWithRepair` return in a private `record ExecResult(QueryResult result, String finalSql)`.

---

## Phase 5 — OptimizerRouter + /optimize Stub (commit a254d3b)

**Refactor:** `formatHistory` extracted from KnowledgeHandler into shared `service/HistoryFormatter.java` (@Component).

**Router design:** single Claude call (temp 0.1) returning strict JSON; Java normalizes/validates defensively:
- Unknown optimizer names → dropped silently
- Empty optimizer list → fallback to all four in canonical order
- All four present → forced to canonical order (`SUBSTITUTION → CONSOLIDATION → REFORMULATION → COMPLEXITY`)
- Bad scope type → fallback to ALL
- COMPANY/PRODUCT/INGREDIENT scope with null value → fallback to ALL
- Any exception → fallback to canonical/ALL + reasoning field mentions failure

**Files created:**
- `handler/optimizers/OptimizerType.java` — enum with `CANONICAL_ORDER` + `@JsonCreator fromJson()`
- `handler/RouterDecision.java` — `optimizers`, `scope (Scope.ScopeType + value)`, `reasoning`
- `handler/RouterDto.java` — package-private DTO mirroring Claude JSON response
- `handler/OptimizerRouter.java` — `route(prompt, history)` with full defensive normalization
- `handler/OptimizeHandler.java` — Phase 5 stub: runs router, returns routing decision as markdown
- `dto/OptimizeRequest.java` + `dto/OptimizeResponse.java`
- `src/main/resources/prompts/optimizer-router.txt` — bilingual (EN+DE) few-shot classifier with 6 examples
- Endpoint: `POST /api/optimize`

---

## Phase 6 — Optimizer Infrastructure + SubstitutionOptimizer (commit 2c210ca)

**Pipeline (real, no longer a stub):**  
`prompt` → `OptimizerRouter.route` → `ScopedDataLoader.load(scope)` → `OptimizerRegistry.get(type).run(ctx)` for each type in the router's canonical list → `ResponseComposer.compose(...)` → Markdown + `findings[]` + `complianceStatus`.

**Core DTOs** (all under `handler/optimizers/`):
- `ComplianceRelevance` — `allergen_changes`, `animal_origin_changes`, `novel_food_risk`, `label_claim_risk`, `affected_claims`, `regulatory_axes`, `notes`. Every `Finding` carries one — the compliance checker consumes this contract.
- `Finding` — `id`, `title`, `summary`, `rationale`, `affectedSkus[]`, `estimatedImpact`, `confidence`, `complianceRelevance`.
- `OptimizerResult` — `type`, `findings`, `narrative`, `stub`, `stubReason`. Static `stub(type, reason)` factory.
- `OptimizerContext` — `userPrompt`, `scope`, `data`, `history`, `sessionId`.
- `ScopedData` — `rows[]`, `totalRows`, `truncated`, `sqlUsed`, `asPromptString` (grouped by company, capped at 60 000 chars).

**Optimizer plugin model:**
- `Optimizer` interface — `type()` + `run(ctx)`.
- `OptimizerRegistry` — Spring autowires all `Optimizer` beans into an `EnumMap`.

**Data loading (`ScopedDataLoader`):**
- **Scope.ALL** — hardcoded denormalized query joining `Company × finished-good Product × BOM × BOM_Component × raw-material Product × Supplier_Product × Supplier` (~5000 rows on current DB).
- **Scope.COMPANY / PRODUCT / INGREDIENT** — Claude generates the scoped SELECT via `scoped-data-sql.txt` (temp 0.2). Falls back to ALL on SQL failure, empty result, or Claude error.
- Executor cap raised to **10 000 rows** via `SqlExecutor.executeReadOnly(sql, maxRows)` + `AgnesRepository.executeScopedQuery(sql, maxRows)` — the 200-row default is preserved for the debug path.

**Prompts** (new):
- `compliance-awareness.txt` — 14 EU mandatory allergens, animal-origin, novel food, label claims; defines the `compliance_relevance` JSON schema that every finding must carry.
- `scoped-data-sql.txt` — generates a denormalized SELECT for the requested scope.
- `optimizer-substitution.txt` — clustering prompt; JSON output schema for ≤5 findings; includes `{{COMPLIANCE_AWARENESS}}`, `{{PORTFOLIO_DATA}}`, `{{USER_PROMPT}}`.

---

## Phase 7a+7b — Consolidation & Reformulation Optimizers (commit c47e177)

**Dependency chain architecture:**
- Optimizers can depend on prior results: `OptimizerDependencies` defines the dependency graph.
- `CONSOLIDATION` depends on `SUBSTITUTION` (needs substitution clusters to identify cross-company merge-purchasing).
- `REFORMULATION` depends on `SUBSTITUTION` (same ingredient data).
- `COMPLEXITY` has no dependencies.
- If a dependency isn't in the router's plan, `OptimizeHandler` injects it as a **hidden** optimizer run (its findings are passed to the dependent but not shown in the report).

**OptimizerContext extended:**
- Added `priorResults: Map<OptimizerType, OptimizerResult>` — each optimizer receives prior results via `withPriorResult()`.
- Fixed `EnumMap` crash: `Map.of()` has no type info for `EnumMap(Map)` constructor → uses `new EnumMap<>(OptimizerType.class)` + `putAll()`.

**ConsolidationOptimizer (real):**
- Receives substitution clusters, formats them via `ClusterFormatter`, and prompts Claude to find cross-company volume consolidation opportunities.
- Every consolidation finding carries `derivedFrom` linking back to substitution finding IDs.
- Prompt: `optimizer-consolidation.txt` with `{{SUBSTITUTION_CLUSTERS}}` injection block.

**ReformulationOptimizer (real):**
- Receives substitution clusters and prompts Claude to identify ingredient form changes that reduce cost, improve stability, or simplify supply chain.
- Prompt: `optimizer-reformulation.txt` with chemistry-focused analysis instructions.

**New files:**
- `handler/optimizers/ClusterFormatter.java` — formats substitution findings into a prompt-friendly block.
- `handler/optimizers/OptimizerDependencies.java` — static dependency graph for optimizers.
- `src/main/resources/prompts/optimizer-consolidation.txt`
- `src/main/resources/prompts/optimizer-reformulation.txt`
- Tests: `ConsolidationOptimizerIT`, `ReformulationOptimizerIT`, `OptimizerDependenciesTest`

---

## Phase 7c — ComplexityOptimizer (commit 42da577)

**ComplexityOptimizer (real):**
- Analyzes BOM-level ingredient redundancy: finds functionally overlapping ingredients within the same product (e.g. two different vitamin E forms used as antioxidants).
- Each finding includes `redundancyPair` (`ingredientA`, `ingredientB`, `overlapReason`, `product`) for precise identification.
- No dependencies — works directly on portfolio data.
- Prompt: `optimizer-complexity.txt` with detailed excipient/functional analysis rules.

**Finding extended:**
- Added `RedundancyPair` record for complexity findings.
- `ResponseComposer` updated to render redundancy pairs in the report.

At this point all four optimizers are real — no stubs remain.

---

## Phase 8 — ComplianceChecker + iHerb Client + EU Regulation Lookup (commit 95c2c3a + 0e18d55)

**The compliance loop is now closed.** Every finding produced by any optimizer starts with `complianceStatus = "pending"` and is verified by the `ComplianceChecker` into a real verdict.

### Compliance Verification Pipeline

1. **`ComplianceLookupService`** — loads EU 1169/2011 regulation JSONs at startup (55 articles, 15 annexes). Provides:
   - Tag-based article search: overlaps `relevance_tags` from findings with article tags.
   - Allergen matching: searches Annex II (14 mandatory EU allergens) for ingredient keywords.
   - Per-finding lookup capped at 5 results for prompt conciseness.
   - Data: `eu_1169_2011.json` (443 lines) + `eu_1169_2011_2.json` (479 lines).

2. **`IHerbClient`** — provides market evidence for supplement ingredients.
   - 11-entry `STUB_CATALOG` covering common ingredients (Vitamin D3, Magnesium Oxide, Fish Oil, etc.).
   - Partial keyword matching against catalog entries.
   - Real API path stubbed out — falls back to catalog when `IHERB_RAPIDAPI_KEY` is `PLACEHOLDER`.

3. **`ComplianceChecker`** — orchestrator:
   - For each finding: looks up EU regulation context + iHerb market evidence.
   - Packs ALL findings + evidence into a **single Claude call** (prompt: `compliance-checker.txt`).
   - Claude returns JSON array of verdicts: `compliant`, `uncertain`, or `non-compliant`.
   - **Dual-layer safety:** individual missing verdicts default to "uncertain"; total Claude failure wraps all findings with fallback evidence.
   - Each verdict includes structured `EvidenceItem` list (type, source, URL, summary).

4. **Integration into OptimizeHandler:**
   - After all optimizers run, `ComplianceChecker.verify()` processes all visible findings.
   - Results are rebuilt with `Finding.withComplianceVerdict()`.
   - Overall compliance status aggregated: `non-compliant` > `uncertain` > `compliant`.

5. **ResponseComposer updated:**
   - Verdict icons: ✅ compliant, ⚠️ uncertain, ❌ non-compliant.
   - Evidence hyperlinks rendered per finding.
   - Overall compliance top-line in the report header.
   - Backward-compatible `compose()` overload for existing tests.

### Finding DTO extended

```java
public record Finding(
    String id, String title, String summary, String rationale,
    List<AffectedSku> affectedSkus, String estimatedImpact,
    String confidence, ComplianceRelevance complianceRelevance,
    List<String> derivedFrom, RedundancyPair redundancyPair,
    String complianceStatus,          // "pending" | "compliant" | "uncertain" | "non-compliant"
    List<EvidenceItem> complianceEvidence
) {
    public record EvidenceItem(String type, String source, String url, String summary) {}
    public Finding withComplianceVerdict(String status, List<EvidenceItem> evidence) { ... }
}
```

### Config additions (`application.yml`)

```yaml
claude:
  max-tokens: 8192          # bumped from 4096 to avoid truncated optimizer responses

compliance:
  regulation-path: classpath:compliance/eu_1169_2011.json

iherb:
  rapidapi-key: ${IHERB_RAPIDAPI_KEY:PLACEHOLDER}
  rapidapi-host: iherb1.p.rapidapi.com
  base-url: https://iherb1.p.rapidapi.com
```

### New files (Phase 8)

| File | Purpose |
|------|---------|
| `config/ComplianceConfig.java` | EU regulation JSON path config |
| `config/ExternalApisConfig.java` | iHerb RapidAPI key/host/URL config |
| `service/compliance/RegulationJson.java` | Jackson DTOs for regulation JSON |
| `service/compliance/ComplianceLookupService.java` | Tag-based article search + allergen matching |
| `service/compliance/IHerbClient.java` | Market evidence with 11-entry stub catalog |
| `service/compliance/ComplianceChecker.java` | Orchestrator: lookup → iHerb → Claude → verdicts |
| `controller/ComplianceDebugController.java` | `/api/debug/compliance-config` |
| `resources/prompts/compliance-checker.txt` | Prompt with 3 injection blocks |
| `resources/compliance/eu_1169_2011.json` | EU regulation articles (55 articles, 15 annexes) |
| `resources/compliance/eu_1169_2011_2.json` | Additional regulation data |

### Prompt tuning (Phase 8 hotfix, commit 0e18d55)

- All four optimizer prompts capped to **5 findings** (was 10) + **6 affected_skus per finding** to prevent token overflow on large portfolios (2860 data rows).
- Read timeout bumped to **180 s** (complexity optimizer needs >120 s for large portfolios).
- Fixed `EnumMap` crash in `OptimizerContext.withPriorResult()` when `priorResults` is `Map.of()`.

---

## Phase 9 — React + TypeScript + Vite Frontend

**The UI is now fully built as a single-page React application.**

### Architecture & Tech Stack
- **Stack:** React 18, Vite, TypeScript, Tailwind CSS, shadcn/ui.
- **State Management:** Custom hooks (`useChatSession`, `useMode`) with `localStorage` persistence.
- **No Router:** Simple `AnimatePresence` transition between Landing and Chat screens.

### Screens & Components
1. **LandingScreen:** Features a custom Three.js + GSAP spiral particle animation with a delayed "AGNES" logo reveal.
2. **ChatScreen:** Features a modernized Three.js dotted surface background (`DottedSurface`) — world-space grid (`SEPARATION=150`, 40×60), sine-wave animation, grey dots on black.
3. **ChatPanel:**
   - **ModeToggle:** Moved from header to bottom-left of input area (compact `small` variant). Header now shows only "AGNES" text.
   - **ChatInput:** Auto-resizing textarea with prefix detection (`/optimize` or `/knowledge`).
   - **MessageList:** Auto-scrolling list of `MessageBubble` components. Welcome screen has no avatar logo.
   - **ThinkingIndicator:** Honest elapsed timer with staged progression text based on the active mode.
4. **AssistantMarkdown:** Robust Markdown rendering via `react-markdown` and `remark-gfm`. Custom components format links (open in new tab), tables, code blocks, and compliance badges seamlessly into the dark UI.
5. **GlowCard (`spotlight-card.tsx`):** Mouse-tracking spotlight border effect applied to all message bubbles. Accepts `glowColor` (`blue`/`purple`/`red`), `customSize`, `className`, `children`. Opacity `0.97` backdrop prevents dot bleed-through.

### API Integration
- Dedicated `client.ts` wraps `fetch` with a 180s timeout (accommodating long optimizer runs).
- Strongly typed request/response DTOs mirroring the backend Java models (`OptimizeResponse`, `KnowledgeResponse`, `Finding`, etc.).

---

## Phase 10 — Performance: Parallel Optimizer Waves + Smart SQL Scoping (commit 5a8a1fd)

### Problem
Broad queries (e.g. "maximize margins for magnesium and vitamin D across Vitacost, CVS, Target") were timing out at 180s because:
1. `scope=ALL` fetched all 2860 rows → 60K char prompt → ~15K input tokens per Claude call
2. `maxTokens=8192` globally → Claude reserved unnecessary output space → slower responses
3. All 4 optimizers ran sequentially → wall time = sum of all calls

### Parallel Wave Execution (`OptimizeHandler` + `OptimizerDependencies`)

`OptimizerDependencies.waves()` performs a topological sort into parallel levels:
- **Wave 1:** `SUBSTITUTION + COMPLEXITY` (both independent, run concurrently)
- **Wave 2:** `CONSOLIDATION + REFORMULATION` (both depend only on SUBSTITUTION, run concurrently after Wave 1)

Uses `Executors.newVirtualThreadPerTaskExecutor()` (Java 21 virtual threads). Each wave fires all steps as `CompletableFuture.supplyAsync(...)` and joins them before proceeding to the next wave. Safe because `OptimizerContext` is fully immutable (Java record + `Map.copyOf()`).

### Smart SQL Scoping (`ScopedDataLoader`)

`load()` now calls `loadSmartAll(userPrompt)` instead of `loadAll()` when `scope=ALL`:
1. Calls Claude with the `scoped-data-sql.txt` prompt + the user's natural language prompt
2. Claude extracts mentioned companies/ingredients and generates a filtered WHERE clause
3. If the SQL returns rows → use them (typically 50–200 rows instead of 2860)
4. If SQL generation fails, SQL returns empty, or any exception → falls back to `loadAll()` gracefully

`scoped-data-sql.txt` updated with multi-entity OR rules:
```sql
-- Multiple companies:
(c.Name LIKE '%Vitacost%' OR c.Name LIKE '%CVS%' OR c.Name LIKE '%Target%')
-- Combined with ingredients:
AND (rm.SKU LIKE '%magnesium%' OR rm.SKU LIKE '%vitamin-d%')
```

### Per-Call maxTokens Override (`ClaudeClient`)

Added `Integer maxTokens` parameter to the full-param `ask()` and new `askJson(…, int maxTokens)` overloads. All existing callers unchanged (pass `null` → config default).

| Caller | maxTokens |
|--------|-----------|
| All 4 optimizers | 2500 |
| ComplianceChecker | 1200 |
| Smart SQL generation | 300 |
| Everything else | 8192 (config default) |

### Expected impact
- Broad query timeout → **~35–45s** (no timeout)
- Single-optimizer query (e.g. vitamin D3 substitution) → **~15–20s** (unchanged, maxTokens helps compliance)

---

## All Endpoints (current)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/health` | Uptime check |
| GET | `/api/health/config` | Model name + apiKeyPresent flag |
| GET | `/api/debug/schema` | Full schema prompt string (plain text) |
| GET | `/api/debug/compliance-config` | Compliance + iHerb config state |
| POST | `/api/debug/query` | Run raw SQL (guarded, read-only) |
| POST | `/api/debug/claude` | Raw Claude round-trip |
| POST | `/api/knowledge` | NL question → SQL → Markdown answer |
| POST | `/api/optimize` | Prompt → router → scoped data → optimizer pipeline → compliance → Markdown + findings |

---

## Test Coverage (111 tests)

| Class | Type | Count |
|-------|------|-------|
| SqlGuardTest | Unit | 15 |
| JsonExtractorTest | Unit | 13 |
| PromptLoaderTest | Unit | 4 |
| ClaudeClientTest | Unit (MockRestServiceServer) | 7 |
| KnowledgeHandlerTest | Unit (Mockito) | 4 |
| OptimizerRouterTest | Unit (Mockito) | 9 |
| OptimizerDependenciesTest | Unit | 6 |
| ComplianceLookupServiceTest | Unit (real JSON) | 8 |
| IHerbClientTest | Unit | 5 |
| AgnesApplicationTests | Spring context load | 1 |
| SchemaProviderTest | IT (real DB) | 1 |
| AgnesRepositoryIT | IT (real DB) | 3 |
| ScopedDataLoaderIT | IT (real DB) | 2 |
| ClaudeClientIT | IT (real Claude) | 2 |
| KnowledgeHandlerIT | IT (real Claude + DB) | 3 |
| OptimizerRouterIT | IT (real Claude) | 7 |
| SubstitutionOptimizerIT | IT (real Claude + DB) | 2 |
| ConsolidationOptimizerIT | IT (real Claude + DB) | 2 |
| ReformulationOptimizerIT | IT (real Claude + DB) | 2 |
| ComplexityOptimizerIT | IT (real Claude + DB) | 2 |
| OptimizeHandlerIT | IT (real Claude + DB) | 9 |
| ComplianceCheckerIT | IT (real Claude) | 4 |

33 IT tests require `ANTHROPIC_API_KEY`. All 78 unit tests always run.

---

## Architecture Overview

```
User Prompt
    │
    ├─── /api/knowledge ──→ KnowledgeHandler
    │       Schema → SQL (Claude) → Execute → Repair? → Answer (Claude)
    │
    └─── /api/optimize ──→ OptimizeHandler
            │
            ├── OptimizerRouter (Claude, temp 0.1)
            │     → optimizers[], scope, reasoning
            │
            ├── ScopedDataLoader
            │     → denormalized portfolio data
            │
            ├── OptimizerDependencies
            │     → inject hidden deps (e.g. SUBSTITUTION for CONSOLIDATION)
            │
            ├── Optimizer Pipeline (parallel waves)
            │     Wave 1 (parallel): SubstitutionOptimizer + ComplexityOptimizer
            │     Wave 2 (parallel): ConsolidationOptimizer + ReformulationOptimizer
            │
            ├── ComplianceChecker (single Claude call)
            │     ├── ComplianceLookupService → EU 1169/2011 articles
            │     ├── IHerbClient → market evidence (stub catalog)
            │     └── Claude → verdicts + evidence per finding
            │
            └── ResponseComposer → Markdown report with
                  ✅/⚠️/❌ verdicts, evidence links, overall status
```

---

## Feedback / Constraints Remembered

- **No `Co-Authored-By` trailer** in commit messages.
- Maven must be invoked via full path — macOS: `~/.m2/wrapper/dists/apache-maven-3.9.14/db91789b/bin/mvn`; Windows/IntelliJ: `/c/Program Files/JetBrains/IntelliJ IDEA 2025.2.3/plugins/maven/lib/maven3/bin/mvn.cmd`.
- Jackson 3 is in use — always import from `tools.jackson.databind.*` for runtime types; `com.fasterxml.jackson.annotation.*` for annotations.
- `.env` file holds the API key (gitignored) — never commit it.
- Every finding emitted by any optimizer **must** carry a `compliance_relevance` object (even empty) — the compliance checker assumes the field is always present.
- `IMPLEMENTATION_PLAN.md` was referenced in early phase prompts but was never in the repo — each phase prompt was self-contained.
- All optimizer prompts are capped at **5 findings** + **6 affected_skus per finding** to stay within the 8192 token budget.
- `EnumMap<>(Map)` constructor fails on empty `Map.of()` — always use `new EnumMap<>(OptimizerType.class)` + `putAll()`.
