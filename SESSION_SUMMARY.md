# Agnes — Session Summary (Phases 1–5)

**Date:** 2026-04-18  
**Stack:** Spring Boot 4.0.0 · Java 21 · SQLite (read-only) · Anthropic claude-sonnet-4-6  
**Repo:** https://github.com/alpgi1/Agnes.git, branch `master`  
**Last commit:** a254d3b  
**Test count:** 69 green, 0 failures (unit + integration)  

---

## What is Agnes?

An AI-powered Supply Chain decision-support backend. It answers natural-language
questions about a CPG sourcing database and classifies optimization requests into
four optimizer types (Substitution, Consolidation, Reformulation, Complexity).

The database (`db.sqlite`, 180 KB, committed at repo root) contains ~61 companies,
finished-good products, BOMs, raw-material SKUs, and supplier links. **No prices,
quantities, or lead times** — Agnes works with structure and naming only.

---

## Environment notes

- **Maven path (macOS):** `~/.m2/wrapper/dists/apache-maven-3.9.14/db91789b/bin/mvn`
- **Java:** OpenJDK 25.0.2 on PATH (system java)
- **Jackson version:** Spring Boot 4.0.0 ships Jackson **3.x** → package is
  `tools.jackson.databind.*` NOT `com.fasterxml.jackson.databind.*`.
  Annotations (`@JsonProperty`, `@JsonIgnoreProperties`, `@JsonCreator`) still in
  `com.fasterxml.jackson.annotation.*`.
- **API key:** stored in `.env` (gitignored). Load with `set -a && . .env && set +a`.
- **No system `mvn`** — always use the full path above.
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

## All Endpoints (current)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/health` | Uptime check |
| GET | `/api/health/config` | Model name + apiKeyPresent flag |
| GET | `/api/debug/schema` | Full schema prompt string (plain text) |
| POST | `/api/debug/query` | Run raw SQL (guarded, read-only) |
| POST | `/api/debug/claude` | Raw Claude round-trip |
| POST | `/api/knowledge` | NL question → SQL → Markdown answer |
| POST | `/api/optimize` | Prompt → router classification (Phase 5 stub) |

---

## Test Coverage (69 tests)

| Class | Type | Count |
|-------|------|-------|
| SqlGuardTest | Unit | 8 |
| JsonExtractorTest | Unit | 13 |
| PromptLoaderTest | Unit | 4 |
| ClaudeClientTest | Unit (MockRestServiceServer) | 7 |
| KnowledgeHandlerTest | Unit (Mockito) | 4 |
| OptimizerRouterTest | Unit (Mockito) | 8 |
| AgnesApplicationTests | Spring context load | 1 |
| SchemaProviderTest | IT (real DB) | 1 |
| AgnesRepositoryIT | IT (real DB) | 3 |
| ClaudeClientIT | IT (real Claude) | 2 |
| KnowledgeHandlerIT | IT (real Claude + DB) | 3 |
| OptimizerRouterIT | IT (real Claude) | 7 |

IT tests skipped without `ANTHROPIC_API_KEY`. `@Order` + 1 s sleep between ITs avoids rate limits.

---

## What Comes Next (Phase 6+)

Phase 6 — **Substitution Optimizer:** query BOMs for raw-material SKUs, cluster semantically identical ingredients (Claude call), return merge candidates.

Phase 7 — **Consolidation, Reformulation, Complexity Optimizers** (same pattern).

Each optimizer follows:
1. Scope-aware data query via `AgnesRepository`
2. Claude call for analysis
3. Structured result → Markdown

`OptimizeHandler.handle()` will be upgraded from stub to real pipeline, calling each optimizer in canonical order and aggregating results.

---

## Feedback / Constraints Remembered

- **No `Co-Authored-By` trailer** in commit messages.
- Maven must be invoked via full path `~/.m2/wrapper/dists/apache-maven-3.9.14/db91789b/bin/mvn`.
- Jackson 3 is in use — always import from `tools.jackson.databind.*` for runtime types; `com.fasterxml.jackson.annotation.*` for annotations.
- `.env` file holds the API key (gitignored) — never commit it.
- `IMPLEMENTATION_PLAN.md` was referenced in early phase prompts but was never in the repo — each phase prompt was self-contained.
