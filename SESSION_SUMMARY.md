# Agnes — Session Summary (Phases 1–6)

**Date:** 2026-04-18  
**Stack:** Spring Boot 4.0.0 · Java 21 · SQLite (read-only) · Anthropic claude-sonnet-4-6  
**Repo:** https://github.com/alpgi1/Agnes.git, branch `master`  
**Last commit:** 2c210ca  
**Test count:** 75 green, 0 failures (unit + integration)  

---

## What is Agnes?

An AI-powered Supply Chain decision-support backend. It answers natural-language
questions about a CPG sourcing database and classifies optimization requests into
four optimizer types (Substitution, Consolidation, Reformulation, Complexity). As
of Phase 6, the Substitution optimizer is real; the other three are wired-in
stubs that return `⏳ pending` in the report.

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
- `ComplianceRelevance` — `allergen_changes`, `animal_origin_changes`, `novel_food_risk`, `label_claim_risk`, `affected_claims`, `regulatory_axes`, `notes`. Every `Finding` carries one — Phase 9's compliance checker will consume this contract.
- `Finding` — `id`, `title`, `summary`, `rationale`, `affectedSkus[]`, `estimatedImpact`, `confidence`, `complianceRelevance`.
- `OptimizerResult` — `type`, `findings`, `narrative`, `stub`, `stubReason`. Static `stub(type, reason)` factory.
- `OptimizerContext` — `userPrompt`, `scope`, `data`, `history`, `sessionId`.
- `ScopedData` — `rows[]`, `totalRows`, `truncated`, `sqlUsed`, `asPromptString` (grouped by company, capped at 60 000 chars).

**Optimizer plugin model:**
- `Optimizer` interface — `type()` + `run(ctx)`.
- `OptimizerRegistry` — Spring autowires all `Optimizer` beans into an `EnumMap`.
- Four implementations: `SubstitutionOptimizer` (real, temp 0.3) + `ConsolidationOptimizer` / `ReformulationOptimizer` / `ComplexityOptimizer` (stubs that return `OptimizerResult.stub(type, "not yet implemented…")`).

**Data loading (`ScopedDataLoader`):**
- **Scope.ALL** — hardcoded denormalized query joining `Company × finished-good Product × BOM × BOM_Component × raw-material Product × Supplier_Product × Supplier` (~5000 rows on current DB).
- **Scope.COMPANY / PRODUCT / INGREDIENT** — Claude generates the scoped SELECT via `scoped-data-sql.txt` (temp 0.2). Falls back to ALL on SQL failure, empty result, or Claude error.
- Executor cap raised to **10 000 rows** via `SqlExecutor.executeReadOnly(sql, maxRows)` + `AgnesRepository.executeScopedQuery(sql, maxRows)` — the 200-row default is preserved for the debug path.

**Prompts** (new):
- `compliance-awareness.txt` — 14 EU mandatory allergens, animal-origin, novel food, label claims; defines the `compliance_relevance` JSON schema that every finding must carry.
- `scoped-data-sql.txt` — generates a denormalized SELECT for the requested scope. Columns labeled `company`, `product`, `ingredient_sku`, `supplier` (matching actual schema — no `Name` on Product, no `Country` on Supplier).
- `optimizer-substitution.txt` — clustering prompt; JSON output schema for ≤10 findings; includes `{{COMPLIANCE_AWARENESS}}`, `{{PORTFOLIO_DATA}}`, `{{USER_PROMPT}}`.

**Response shape (`ResponseComposer`):**
- `# Optimization Report` with scope, optimizers run, data-row count, router reasoning.
- One section per optimizer: stubs render as `⏳ _pending_ — <reason>`; real optimizers list findings with **Affected SKUs**, **Impact**, **Confidence**, and **Pre-filter flags** summarizing `compliance_relevance`.

**`OptimizeResponse` extended** — added `List<Finding> findings` and `String complianceStatus` (currently `"pending — ComplianceChecker lands in Phase 9"`). The existing fields (`sessionId`, `markdown`, `optimizersRun`, `scope`, `routerReasoning`, `durationMs`) are preserved.

**Schema gotchas discovered during Phase 6:**
- `BOM_Component` uses `BOMId` (uppercase), not `BomId`.
- `Product` has no `Name` column — only `SKU`.
- `Supplier` has no `Country` column.
- The initial hardcoded SQL was corrected pre-commit once `SchemaProvider` output was inspected.

**New tests:**
- `service/ScopedDataLoaderIT.java` — real DB; asserts ALL scope returns rows, SQL mentions `FROM Company`, prompt string is capped.
- `handler/optimizers/SubstitutionOptimizerIT.java` — real Claude + DB; asserts substitution run is non-stub and findings carry `complianceRelevance`. Also covers empty-data stub fallback.
- `handler/OptimizeHandlerIT.java` — real Claude + DB; asserts the generic German prompt runs all four optimizers and renders ⏳ for the three stubs.

---

## All Endpoints (current)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/health` | Uptime check |
| GET | `/api/health/config` | Model name + apiKeyPresent flag |
| GET | `/api/debug/schema` | Full schema prompt string (plain text) |
| POST | `/api/debug/query` | Run raw SQL (guarded, read-only) |
| POST | `/api/debug/claude` | Raw Claude round-trip |
| POST | `/api/knowledge` | NL question → SQL → Markdown answer |
| POST | `/api/optimize` | Prompt → router → scoped data → optimizer pipeline → Markdown + findings |

---

## Test Coverage (75 tests)

| Class | Type | Count |
|-------|------|-------|
| SqlGuardTest | Unit | 15 |
| JsonExtractorTest | Unit | 13 |
| PromptLoaderTest | Unit | 4 |
| ClaudeClientTest | Unit (MockRestServiceServer) | 7 |
| KnowledgeHandlerTest | Unit (Mockito) | 4 |
| OptimizerRouterTest | Unit (Mockito) | 9 |
| AgnesApplicationTests | Spring context load | 1 |
| SchemaProviderTest | IT (real DB) | 1 |
| AgnesRepositoryIT | IT (real DB) | 3 |
| ScopedDataLoaderIT | IT (real DB) | 2 |
| ClaudeClientIT | IT (real Claude) | 2 |
| KnowledgeHandlerIT | IT (real Claude + DB) | 3 |
| OptimizerRouterIT | IT (real Claude) | 7 |
| SubstitutionOptimizerIT | IT (real Claude + DB) | 2 |
| OptimizeHandlerIT | IT (real Claude + DB) | 2 |

16 IT tests skipped without `ANTHROPIC_API_KEY`. `@Order` + 1 s sleep between ITs avoids rate limits.

---

## What Comes Next (Phase 7+)

**Phase 7 — ConsolidationOptimizer.** Replace the stub with a Claude call that, given the substitution clusters plus supplier links, identifies merge-purchasing opportunities across companies.

**Phase 8 — Reformulation & Complexity Optimizers.** Reformulation needs chemistry/function knowledge via Claude prompting; Complexity inspects a single BOM for functionally-overlapping ingredients.

**Phase 9 — ComplianceChecker.** New handler consumes every `Finding.complianceRelevance` block and produces a compliance verdict (clean / review / reject) per finding. `OptimizeResponse.complianceStatus` is wired in Phase 6 specifically to receive this.

Each remaining optimizer follows the same pattern already proven in Phase 6:
1. `ScopedDataLoader` already provides the data.
2. A new prompt template + `askJson` call (temp 0.3).
3. Parse into `Finding[]` with `complianceRelevance` populated.
4. `ResponseComposer` already renders them; no handler changes needed.

---

## Feedback / Constraints Remembered

- **No `Co-Authored-By` trailer** in commit messages.
- Maven must be invoked via full path — macOS: `~/.m2/wrapper/dists/apache-maven-3.9.14/db91789b/bin/mvn`; Windows/IntelliJ: `/c/Program Files/JetBrains/IntelliJ IDEA 2025.2.3/plugins/maven/lib/maven3/bin/mvn.cmd`.
- Jackson 3 is in use — always import from `tools.jackson.databind.*` for runtime types; `com.fasterxml.jackson.annotation.*` for annotations.
- `.env` file holds the API key (gitignored) — never commit it.
- Every finding emitted by any optimizer **must** carry a `compliance_relevance` object (even empty) — Phase 9 assumes the field is always present.
- `IMPLEMENTATION_PLAN.md` was referenced in early phase prompts but was never in the repo — each phase prompt was self-contained.
