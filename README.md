# Agnes — AI-Powered Supply Chain Optimization

> **Intelligent sourcing decisions for CPG companies.** Agnes analyzes your ingredient portfolio and surfaces the highest-value optimization opportunities — substitutions, consolidations, reformulations, and complexity reductions — while checking every recommendation against EU food-safety regulations.

---

<!-- 📸 Screenshots / demo GIFs go here -->

---

## Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Getting Started](#getting-started)
- [Usage](#usage)
- [Optimization Pipeline](#optimization-pipeline)
- [Compliance Engine](#compliance-engine)
- [Graph Explorer](#graph-explorer)
- [API Reference](#api-reference)
- [Project Structure](#project-structure)
- [Configuration](#configuration)

---

## Overview

Agnes is a full-stack AI application that acts as a supply-chain analyst for Consumer Packaged Goods (CPG) companies. It connects to a read-only SQLite database containing ingredient, supplier, product, and company data, then uses Claude (Anthropic) as its reasoning engine.

Agnes operates in three modes:

| Mode | Description | Trigger |
|---|---|---|
| **Optimize** | Runs one or more optimizers to find margin-improvement opportunities | Natural-language instructions like *"maximize margins for vitamin D"* |
| **Knowledge** | Answers factual questions about the supply chain data | Questions like *"which supplier provides our magnesium?"* |
| **Graph** | Returns structured graph data for visual exploration | Graph API calls from the frontend |

---

## Key Features

- **Natural-language interface** — Ask questions or give instructions in plain English or German; Agnes routes them to the right subsystem automatically.
- **Wave-based parallel optimizer execution** — Wave 1 (Substitution + Complexity) and Wave 2 (Consolidation + Reformulation) run concurrently using Java virtual threads.
- **EU Regulatory compliance checking** — Every optimization finding is verified against EU 1169/2011 (food information to consumers) before being shown to the user.
- **Smart SQL scoping** — Agnes generates targeted SQL from the user's prompt instead of loading all data, keeping context windows small and responses fast.
- **Pre-filter compliance bypass** — Findings with no allergen, animal-origin, novel-food, chemistry, or label-claim risk are auto-approved without an additional Claude call.
- **Interactive graph visualization** — Three graph views (Company↔Supplier, Company↔Product, Product↔Supplier) rendered with vis-network in the frontend.
- **Read-only database access** — All SQL is routed through `SqlGuard`, which enforces SELECT-only and blocks schema modifications.
- **Structured audit trail** — Every finding carries evidence items (EU regulation, Claude reasoning, required actions) that are surfaced in the report.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        React Frontend                           │
│   ChatView  │  GraphExplorer  │  CompanyFilter  │  Markdown     │
└────────────────────────┬────────────────────────────────────────┘
                         │  HTTP / REST
┌────────────────────────▼────────────────────────────────────────┐
│                    Spring Boot Backend                          │
│                                                                 │
│  /api/optimize ──► AgnesHandler                                 │
│                        │                                        │
│              ┌─────────▼──────────┐                             │
│              │    RouterDecision  │  (optimizer-router prompt)  │
│              └─────────┬──────────┘                             │
│                        │                                        │
│          ┌─────────────▼─────────────┐                          │
│          │     Wave Execution        │  (virtual threads)       │
│          │  Wave 1: SUB + COMPLEX    │                          │
│          │  Wave 2: CONSOL + REFORM  │                          │
│          └─────────────┬─────────────┘                          │
│                        │                                        │
│          ┌─────────────▼─────────────┐                          │
│          │    ComplianceChecker      │  (EU 1169/2011 lookup    │
│          │  pre-filter → Claude      │   + iHerb evidence)      │
│          └─────────────┬─────────────┘                          │
│                        │                                        │
│          ┌─────────────▼─────────────┐                          │
│          │    ResponseComposer       │  (Markdown report)       │
│          └───────────────────────────┘                          │
│                                                                 │
│  /api/graph/* ──► GraphController ──► GraphService              │
│                                                                 │
│  /api/optimize (knowledge) ──► KnowledgeHandler                 │
│                                                                 │
│  SQLite (read-only) via SqlGuard + SchemaProvider               │
└─────────────────────────────────────────────────────────────────┘
```

---

## Tech Stack

### Backend

| Component | Technology |
|---|---|
| Runtime | Java 21 + Spring Boot 4.0 |
| Concurrency | Virtual Threads (Project Loom) |
| AI Model | Claude (Anthropic API) — claude-opus-4-x / sonnet-4-x |
| Database | SQLite (read-only) via `spring-boot-starter-data-jdbc` |
| HTTP Client | Spring `RestClient` (synchronous) |
| JSON | Jackson 3 (tools.jackson) |
| Prompt loading | Custom `PromptLoader` with Spring `@Cacheable` |
| Build | Maven 3.9+ |

### Frontend

| Component | Technology |
|---|---|
| Framework | React 18 + Vite |
| Graph visualization | vis-network + vis-data |
| Markdown rendering | react-markdown |
| Styling | Plain CSS modules |
| HTTP | Native `fetch` |
| Dev server | Vite (port 5173) |

---

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.9+ (or use the Maven wrapper)
- Node.js 18+ and npm
- An `ANTHROPIC_API_KEY`
- A `db.sqlite` file at the repository root

### 1. Clone and place the database

```bash
git clone <repo-url>
cd Agnes
# Place your db.sqlite at the repo root
cp /path/to/your/db.sqlite ./db.sqlite
```

### 2. Set environment variables

```bash
export ANTHROPIC_API_KEY=sk-ant-...
```

Or create a `.env` file — see `src/main/resources/application.properties` for all supported keys.

### 3. Start the backend

```bash
# From the repo root
mvn spring-boot:run
```

The server starts on **port 8080**. Verify:

```bash
curl http://localhost:8080/api/health
curl http://localhost:8080/api/health/config
```

The app starts even without `ANTHROPIC_API_KEY` — `/api/health/config` will report `"apiKeyPresent": false`.

### 4. Start the frontend

```bash
cd agnes-frontend
npm install
npm run dev
```

The dev server starts on **port 5173**. Open [http://localhost:5173](http://localhost:5173).

---

## Usage

Agnes understands natural language. Type your instruction in the chat input.

### Optimization examples

```
Agnes, please maximize the profit margins for all magnesium and vitamin D products
across the Vitacost, CVS, and Target brands.
```

```
Can you substitute all vitamin D3 across our portfolio?
```

```
Run a full analysis on everything — all four optimizers.
```

```
Find consolidation opportunities for our omega-3 suppliers.
```

### Knowledge / Q&A examples

```
Which supplier provides our magnesium glycinate?
```

```
How many SKUs use lanolin-derived vitamin D3?
```

```
What is our current supplier count for B-vitamin products?
```

### Scoped optimization

Agnes automatically detects scope from your prompt. Mentioning a company name, ingredient, or supplier narrows the SQL query and speeds up the response.

```
Optimize the omega-3 portfolio for Vitacost only.
```

---

## Optimization Pipeline

### Optimizer types

| Optimizer | What it finds | Example finding |
|---|---|---|
| **SUBSTITUTION** | Ingredients used under different names that are chemically equivalent | *"Cholecalciferol" and "Vitamin D3" are the same molecule — consolidate to one SKU* |
| **CONSOLIDATION** | Multiple suppliers for the same ingredient where one preferred supplier could serve all | *"Three suppliers for magnesium oxide — pool volume to Supplier A for 12% cost reduction"* |
| **REFORMULATION** | Ingredient form changes that improve bioavailability or reduce cost | *"Magnesium oxide → magnesium glycinate — better absorption, stable pricing"* |
| **COMPLEXITY** | Redundant SKUs in the portfolio that serve the same function | *"Remove SKU-447 — identical formulation to SKU-312, covered by the same supplier"* |

### Wave execution

```
User prompt
    │
    ▼
Router (Claude) ──► decide which optimizers to run
    │
    ├── Wave 1 (parallel):  SUBSTITUTION ─┐
    │                       COMPLEXITY   ─┤──► findings[]
    │                                     │
    └── Wave 2 (parallel):  CONSOLIDATION─┤  (uses Wave 1 results)
                            REFORMULATION─┘

findings[] ──► ComplianceChecker ──► ResponseComposer ──► Markdown report
```

**Router logic:**
- Generic improvement requests (*"maximize margins"*, *"find savings"*) → **SUBSTITUTION + REFORMULATION**
- Explicit full analysis (*"run full analysis"*, *"optimize everything"*) → **all four**
- Single-optimizer keywords (*"substitute"*, *"consolidate"*, *"reformulate"*, *"simplify"*) → that optimizer only

---

## Compliance Engine

Every finding produced by an optimizer passes through the compliance pipeline before appearing in the report.

### Stage 1 — Pre-filter (no Claude call)

Findings with **none** of the following flags are auto-approved as `compliant` with no additional AI call:

- Allergen changes
- Animal-origin changes
- Novel food risk
- Ingredient chemistry changes
- Label claim risk
- Pre-filter flags

### Stage 2 — Legal context lookup

For findings that pass the pre-filter, the `ComplianceLookupService` retrieves relevant EU 1169/2011 excerpts keyed to the finding's `ComplianceRelevance` metadata.

### Stage 3 — Claude verification + iHerb market evidence

The remaining findings are batched into a single Claude call alongside:
- The legal excerpts from Stage 2
- Market evidence from iHerb (ingredient availability, certifications, sourcing info)

Claude returns a verdict for each finding:

| Verdict | Meaning |
|---|---|
| `compliant` | Safe to implement, no regulatory risks |
| `uncertain` | Plausible but requires supplier certification or label re-review |
| `non-compliant` | Clear breach of EU 1169/2011 or introduces unmitigable risk |

Each verdict includes three structured evidence items:
1. **Regulatory Context** — relevant EU regulation article and how it applies
2. **Specific Risk** — the precise compliance risk mechanism
3. **Required Action** — 2-3 actionable steps for the procurement team

---

## Graph Explorer

The frontend includes an interactive graph explorer powered by vis-network. Three views are available:

### Company ↔ Supplier

Shows which companies source from which suppliers, with `sources_from` edges carrying `product_count` metadata.

- **Nodes:** `company` (blue), `supplier` (orange)
- **Edges:** `sources_from` — weighted by product count

### Company ↔ Product

Shows the product portfolio of one (or all) companies, including finished goods and raw materials.

- **Nodes:** `company`, `finished_good`, `raw_material`
- **Edges:** `owns` (company → finished good), `uses` (finished good → raw material)

### Product ↔ Supplier

Bipartite graph showing which raw materials are sourced from which suppliers.

- **Nodes:** `raw_material`, `supplier`
- **Edges:** `supplied_by`

---

## API Reference

### POST `/api/optimize`

Main chat endpoint. Handles both optimization and knowledge queries.

**Request:**
```json
{
  "message": "Agnes, please maximize the profit margins for all magnesium products.",
  "history": [
    { "role": "user", "content": "..." },
    { "role": "assistant", "content": "..." }
  ]
}
```

**Response:**
```json
{
  "message": "**Optimization Report**\n* **Scope:** ...\n* **Overall Compliance:** ...\n\n**SUB-001: ...**\n..."
}
```

---

### GET `/api/graph/company-supplier`

Returns the Company ↔ Supplier graph.

**Query params:** `companyId` (optional), `supplierId` (optional)

**Response:**
```json
{
  "nodes": [
    { "id": "company-1", "label": "Vitacost", "type": "company", "properties": {} },
    { "id": "supplier-42", "label": "NutriSource", "type": "supplier", "properties": {} }
  ],
  "edges": [
    { "id": "e-1-42", "from": "company-1", "to": "supplier-42", "type": "sources_from",
      "properties": { "product_count": 7 } }
  ],
  "meta": { "view": "company-supplier", "nodeCount": 2, "edgeCount": 1 }
}
```

---

### GET `/api/graph/company-product`

Returns the Company ↔ Product graph.

**Query params:** `companyId` (optional)

---

### GET `/api/graph/product-supplier`

Returns the Product ↔ Supplier bipartite graph.

---

### GET `/api/graph/companies`

Returns a flat list of companies for the filter dropdown.

**Response:**
```json
[
  { "Id": 1, "Name": "Vitacost" },
  { "Id": 2, "Name": "CVS" }
]
```

---

### GET `/api/health`

Basic liveness check. Returns `200 OK` with `"Agnes is healthy"`.

---

### GET `/api/health/config`

Reports runtime configuration state.

```json
{
  "apiKeyPresent": true,
  "databaseConnected": true,
  "schemaLoaded": true
}
```

---

### GET `/api/debug/schema`

Returns the full database schema as seen by Agnes (table names + column definitions).

---

## Project Structure

```
Agnes/
├── src/
│   ├── main/
│   │   ├── java/com/spherecast/agnes/
│   │   │   ├── AgnesApplication.java          # Entry point
│   │   │   ├── controller/
│   │   │   │   ├── AgnesController.java        # POST /api/optimize
│   │   │   │   ├── GraphController.java        # GET /api/graph/*
│   │   │   │   └── HealthController.java       # GET /api/health
│   │   │   ├── handler/
│   │   │   │   ├── AgnesHandler.java           # Main request orchestrator
│   │   │   │   ├── KnowledgeHandler.java       # Q&A mode
│   │   │   │   ├── ResponseComposer.java       # Markdown report builder
│   │   │   │   ├── RouterDecision.java         # Router output record
│   │   │   │   └── optimizers/
│   │   │   │       ├── Optimizer.java          # Interface
│   │   │   │       ├── OptimizerContext.java   # Per-run context
│   │   │   │       ├── OptimizerResult.java    # Result + findings
│   │   │   │       ├── Finding.java            # Core finding record
│   │   │   │       ├── ComplianceRelevance.java
│   │   │   │       ├── ScopedData.java
│   │   │   │       ├── SubstitutionOptimizer.java
│   │   │   │       ├── ConsolidationOptimizer.java
│   │   │   │       ├── ReformulationOptimizer.java
│   │   │   │       └── ComplexityOptimizer.java
│   │   │   ├── service/
│   │   │   │   ├── GraphService.java           # Graph query logic
│   │   │   │   ├── PromptLoader.java           # Loads + caches prompts
│   │   │   │   ├── SmartDataLoader.java        # Scoped SQL generation
│   │   │   │   ├── SchemaProvider.java         # DB schema introspection
│   │   │   │   ├── SqlGuard.java               # SELECT-only enforcement
│   │   │   │   └── compliance/
│   │   │   │       ├── ComplianceChecker.java  # Main compliance orchestrator
│   │   │   │       ├── ComplianceLookupService.java  # EU reg lookup
│   │   │   │       └── IHerbClient.java        # iHerb market evidence
│   │   │   └── service/claude/
│   │   │       └── ClaudeClient.java           # Anthropic API client
│   │   └── resources/
│   │       ├── application.properties
│   │       └── prompts/
│   │           ├── optimizer-router.txt
│   │           ├── optimizer-substitution.txt
│   │           ├── optimizer-consolidation.txt
│   │           ├── optimizer-reformulation.txt
│   │           ├── optimizer-complexity.txt
│   │           ├── compliance-checker.txt
│   │           ├── compliance-awareness.txt
│   │           └── knowledge-handler.txt
│   └── test/
│       └── java/com/spherecast/agnes/
│           └── service/
│               └── GraphServiceTest.java
├── agnes-frontend/
│   ├── src/
│   │   ├── App.jsx                            # Root component + routing
│   │   ├── components/
│   │   │   ├── ChatView.jsx                   # Chat interface
│   │   │   ├── GraphExplorer.jsx              # Graph visualization
│   │   │   ├── GraphSidebar.jsx               # View selector + company filter
│   │   │   └── MessageBubble.jsx              # Markdown message renderer
│   │   └── main.jsx
│   ├── package.json
│   └── vite.config.js
├── db.sqlite                                  # Supply chain database (not in repo)
├── pom.xml
└── README.md
```

---

## Configuration

| Property | Env var | Default | Description |
|---|---|---|---|
| `anthropic.api-key` | `ANTHROPIC_API_KEY` | — | Anthropic API key (required for AI features) |
| `anthropic.model` | `ANTHROPIC_MODEL` | `claude-opus-4-5` | Claude model ID |
| `spring.datasource.url` | — | `jdbc:sqlite:db.sqlite` | Path to the SQLite database |
| `server.port` | — | `8080` | Backend HTTP port |

---

## Development Notes

- **Prompts are cached** via Spring `@Cacheable`. After editing any `.txt` file in `src/main/resources/prompts/`, restart the backend for changes to take effect.
- **SqlGuard** blocks any SQL statement that is not a `SELECT`. It also rejects statements containing `--`, `;`, or subquery patterns that could escape the read-only boundary.
- **Wave execution** uses `CompletableFuture` with a virtual-thread executor. Wave 2 optimizers receive Wave 1 results via `OptimizerContext.priorResults()`.
- **MAX_FINDINGS = 1** per optimizer caps the findings list in Java after Claude's response is parsed. Changing this also changes the compliance prompt size proportionally.
- **iHerb lookups** return stub data by default when the live endpoint is unavailable. The stub reason is surfaced in the compliance report.
