# Agnes — AI-Powered Supply Chain Optimization

> **Intelligent sourcing decisions for CPG companies.** Agnes analyzes your ingredient portfolio and surfaces the highest-value optimization opportunities — substitutions, consolidations, reformulations, and complexity reductions — while checking every recommendation against EU food-safety regulations.

---

## Table of Contents

- [Problem Statement](#problem-statement)
- [Overview](#overview)
- [Key Features](#key-features)
- [How Agnes Addresses the Core Challenge](#how-agnes-addresses-the-core-challenge)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Getting Started](#getting-started)
- [Optimization Pipeline](#optimization-pipeline)
- [External Data Enrichment](#external-data-enrichment)
- [Compliance Engine](#compliance-engine)
- [Explainability & Evidence Trails](#explainability--evidence-trails)
- [Handling Uncertainty & Trustworthiness](#handling-uncertainty--trustworthiness)
- [Graph Explorer](#graph-explorer)
- [API Reference](#api-reference)
- [Scalability & Future Vision](#scalability--future-vision)

---

## Problem Statement

CPG companies regularly overpay because sourcing is fragmented. The same ingredient may be purchased by multiple companies, plants, or product lines without anyone having full visibility into the combined demand. That means:

- **Suppliers do not see the true buying volume** — orders are not consolidated across business units.
- **Buyers lose leverage** on price, lead time, and service levels.
- **Consolidation is only valuable if the components are actually substitutable** and still compliant in the context of the end product.

The challenge goes far beyond simple cost optimization. A cheaper or more consolidated alternative must still satisfy the quality and compliance requirements of the finished product. This requires combining structured internal data with incomplete external evidence — supplier websites, product listings, certification databases, public product pages, and regulatory references. **A sourcing recommendation is only valid if the system can justify that compliance and quality constraints are still met.**

The focus lies on making incomplete and messy data actionable: identifying functional substitutes, inferring compliance-relevant requirements, and producing an explainable sourcing proposal that balances supplier consolidation, lead time, and practical feasibility.

At [Spherecast](https://spherecast.com), we think of this capability as **Agnes** — an AI Supply Chain Manager that helps teams make better sourcing decisions by reasoning across fragmented supply chain data.

---

## Overview

Agnes is a full-stack AI decision-support application that acts as a supply-chain analyst for CPG companies. It ingests normalized bill-of-materials (BOM) data and supplier mappings from a read-only SQLite database, enriches this with external market evidence, and uses Claude (Anthropic) as its reasoning engine to produce explainable, compliance-verified sourcing recommendations.

Agnes operates in three modes:

| Mode | Description | Trigger |
|---|---|---|
| **Optimize** | Runs one or more optimizers to find margin-improvement opportunities | Natural-language instructions like *"maximize margins for vitamin D"* |
| **Knowledge** | Answers factual questions about the supply chain data | Questions like *"which supplier provides our magnesium?"* |
| **Graph** | Returns structured graph data for visual exploration | Graph API calls from the frontend |

---

## Key Features

- **Natural-language interface** — Ask questions or give instructions in plain English or German; Agnes routes them to the right subsystem automatically.
- **Functional substitution detection** — Identifies ingredients that are chemically equivalent or functionally interchangeable, even when listed under different names across companies or product lines.
- **Wave-based parallel optimizer execution** — Wave 1 (Substitution + Complexity) and Wave 2 (Consolidation + Reformulation) run concurrently using Java virtual threads.
- **EU Regulatory compliance checking** — Every optimization finding is verified against EU 1169/2011 (food information to consumers) before being shown to the user.
- **External data enrichment** — Combines internal BOM/supplier data with market evidence from external sources (e.g., iHerb product listings, certifications, sourcing info) to validate recommendations.
- **Explainable evidence trails** — Every finding carries structured evidence items (EU regulation references, AI reasoning, required actions) so that procurement teams can audit and trust the recommendations.
- **Smart SQL scoping** — Agnes generates targeted SQL from the user's prompt instead of loading all data, keeping context windows small and responses fast.
- **Pre-filter compliance bypass** — Findings with no allergen, animal-origin, novel-food, chemistry, or label-claim risk are auto-approved without an additional Claude call.
- **Interactive graph visualization** — Three graph views (Company↔Supplier, Company↔Product, Product↔Supplier) rendered with vis-network in the frontend.
- **Read-only database access** — All SQL is routed through `SqlGuard`, which enforces SELECT-only and blocks schema modifications.
- **Uncertainty-aware verdicts** — Compliance results are classified as `compliant`, `uncertain`, or `non-compliant`, explicitly surfacing where human review is needed rather than hiding ambiguity.

---

## How Agnes Addresses the Core Challenge

The hackathon challenge requires teams to solve three interconnected problems. Here is how Agnes addresses each:

### 1. Identify functionally interchangeable components

Agnes's **Substitution Optimizer** and **Complexity Optimizer** analyze the ingredient portfolio to find materials that are chemically equivalent (e.g., "Cholecalciferol" vs. "Vitamin D3") or functionally redundant across product lines. The system reasons at the raw-ingredient level, inferring equivalence even when naming conventions differ across companies.

### 2. Infer quality and compliance requirements

Rather than requiring manually curated compliance rules, Agnes **infers** which regulations apply based on the characteristics of each proposed change. The `ComplianceRelevance` metadata — allergen flags, animal-origin flags, novel food risk, chemistry changes, and label-claim risk — is extracted by the optimizer and used to determine which EU 1169/2011 articles are relevant. External market evidence (certifications, sourcing data) is gathered to fill gaps in the internal data.

### 3. Produce explainable sourcing recommendations

Every finding in the final report includes a structured evidence trail: the regulatory context that applies, the specific risk mechanism, and 2–3 actionable steps for the procurement team. Findings are never presented without justification — the system always shows *why* a recommendation is safe (or not) and *what* needs to happen next.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        React Frontend                           │
│  ChatScreen  │  GraphScreen  │  ModeToggle  │  Markdown         │
└────────────────────────┬────────────────────────────────────────┘
                         │  HTTP / REST
┌────────────────────────▼────────────────────────────────────────┐
│                    Spring Boot Backend                          │
│                                                                 │
│  /api/optimize ──► OptimizeHandler                              │
│                        │                                        │
│              ┌─────────▼──────────┐                             │
│              │   OptimizerRouter  │  (optimizer-router prompt)  │
│              └─────────┬──────────┘                             │
│                        │                                        │
│          ┌─────────────▼─────────────┐                          │
│          │  Parallel Execution       │  (with dependencies)     │
│          │  SUB & CONSOL & REFORM    │                          │
│          │        & COMPLEX          │                          │
│          └─────────────┬─────────────┘                          │
│                        │                                        │
│          ┌─────────────▼─────────────┐                          │
│          │    ComplianceChecker      │  (EU 1169/2011 lookup    │
│          │   pre-filter →            |                          | 
|          |   post-processing         │   + external evidence)   │
│          └─────────────┬─────────────┘                          │
│                        │                                        │
│          ┌─────────────▼─────────────┐                          │
│          │    ResponseComposer       │  (Markdown report with   │
│          │                           │   evidence trails)       │
│          └───────────────────────────┘                          │
│                                                                 │
│  /api/knowledge ──► KnowledgeHandler                            │
│                                                                 │
│  /api/graph/* ──► GraphController ──► GraphService              │
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

### Knowledge & Optimization examples

```
What are the current suppliers for Vitamin-B products? (Knowledge)
```

```
Can you substitute all vitamin D3? (Substitution + Compliance)
```

```
Please maximize the profit margins for all magnesium and vitamin D products across the Vitacost, CVS, and Target brands (Substitution + Reformulation +Compliance)
```

---

## Optimization Pipeline

### Optimizer types

| Optimizer | What it finds | Example finding |
|---|---|---|
| **SUBSTITUTION** | Ingredients used under different names that are chemically equivalent | *"Cholecalciferol" and "Vitamin D3" are the same molecule — consolidate to one SKU* |
| **CONSOLIDATION** | Multiple suppliers for the same ingredient where volume can be pooled | *"Three suppliers for magnesium oxide — pool volume to Supplier A for 12% cost reduction"* |
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

## External Data Enrichment

Agnes goes beyond internal BOM and supplier data by enriching findings with external market evidence. This is critical because internal databases alone rarely contain enough information to validate whether a substitution is compliant and commercially viable.

### Current enrichment sources

- **iHerb market evidence** — The `IHerbClient` retrieves product listings, certifications, and sourcing information for ingredients under consideration. This provides real-world evidence of ingredient availability, common formulations, and supplier certifications.
- **EU 1169/2011 regulatory excerpts** — The `ComplianceLookupService` maintains a structured lookup of EU food-information regulation articles, keyed by compliance-risk category (allergens, novel food, animal origin, etc.).

### How enrichment is used

External evidence is injected directly into the compliance verification prompt alongside internal findings. Claude evaluates whether the evidence supports or contradicts a proposed change, producing verdicts grounded in real data rather than parametric knowledge alone. When external sources are unavailable, the system explicitly notes this limitation in the evidence trail rather than silently proceeding.

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

### Stage 2 — Legal context lookup

For findings that trigger compliance flags, the `ComplianceLookupService` retrieves relevant EU 1169/2011 excerpts keyed to the finding's `ComplianceRelevance` metadata.

### Stage 3 — Post-proessing (with Claude verification)

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
3. **Required Action** — 2–3 actionable steps for the procurement team

---

## Explainability & Evidence Trails

Agnes is designed so that no recommendation is presented without justification. Every optimization finding in the final Markdown report includes:

- **Finding ID** (e.g., `SUB-001`, `CONSOL-002`) for traceability
- **What changed** — the specific substitution, consolidation, or reformulation proposed
- **Why it's beneficial** — cost savings, volume leverage, reduced complexity
- **Compliance verdict** — `compliant` / `uncertain` / `non-compliant` with reasoning
- **Evidence items** — structured references to EU regulations, market data, and AI reasoning
- **Required actions** — concrete next steps for the procurement team

This structured audit trail allows procurement teams to review, challenge, and act on recommendations with confidence rather than relying on opaque AI outputs.

---

## Handling Uncertainty & Trustworthiness

Agnes explicitly models uncertainty rather than hiding it:

- **Three-tier compliance verdicts** — `compliant`, `uncertain`, and `non-compliant` ensure that ambiguous cases are flagged for human review rather than silently approved or rejected.
- **Evidence gaps are surfaced** — When external data sources are unavailable (e.g., iHerb returns no results), the system reports this in the evidence trail with a stub reason, so users know the limitation.
- **Read-only database access** — `SqlGuard` enforces SELECT-only queries and blocks schema modifications, `--` comments, `;` injection, and subquery escape patterns, preventing the AI from modifying or corrupting data.
- **Scoped SQL generation** — Rather than loading the entire database into the context window, Agnes generates targeted queries scoped to the user's request, reducing the surface area for hallucination.
- **Structured output parsing** — Optimizer results are parsed into strongly-typed Java records (`Finding`, `ComplianceRelevance`, `OptimizerResult`), enforcing schema conformance and catching malformed AI outputs before they reach the user.

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

Runs the optimization pipeline (router → optimizers → compliance → report).


### POST `/api/knowledge`

Answers factual questions about the supply chain data using SQL generation.



### GET `/api/graph/company-supplier`

Returns the Company ↔ Supplier graph.

**Query params:** `companyId` (optional), `supplierId` (optional)

### GET `/api/graph/company-product`

Returns the Company ↔ Product graph.

**Query params:** `companyId` (optional)

### GET `/api/graph/product-supplier`

Returns the Product ↔ Supplier bipartite graph.

### GET `/api/graph/companies`

Returns a flat list of companies for the filter dropdown.

### GET `/api/health`

Basic liveness check. Returns `200 OK` with `"Agnes is healthy"`.

### GET `/api/health/config`

Reports runtime configuration state: `apiKeyPresent`, `databaseConnected`, `schemaLoaded`.

### GET `/api/debug/schema`

Returns the full database schema as seen by Agnes (table names + column definitions).

---

## Scalability & Future Vision

Agnes is designed as a foundation that can scale and improve over time:

- **Scalable data retrieval** — At the current data volume, Agnes loads targeted DB context via smart SQL scoping. When scaling to larger datasets, the architecture is designed to introduce RAG (Retrieval-Augmented Generation) for more efficient data retrieval — replacing or augmenting direct SQL context loading without changing the compliance or optimizer architecture.
- **Multi-region regulatory support** — The compliance engine currently targets EU 1169/2011 but the lookup-service pattern generalizes to FDA, Codex Alimentarius, or other regulatory frameworks.
- **Agentic enrichment workflows** — Future iterations could have Agnes autonomously retrieve, verify, and structure missing evidence from the web — scraping supplier spec sheets, parsing CoA PDFs, or querying certification databases — rather than relying on pre-integrated APIs.
