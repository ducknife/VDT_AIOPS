# Roadmap: VDT-AIOps

## Overview

VDT-AIOps is built as a single Spring Boot process where every layer depends strictly on the layer below it. The roadmap follows the architecture's dependency chain: infrastructure skeleton first, then data collection, then detection and alert management, then AI analysis, then the CLI interface, and finally simulation scripts that validate the complete end-to-end pipeline. Each phase delivers one complete, independently testable layer. No phase begins before its dependency layer is solid.

**Total v1 requirements:** 18
**Granularity:** Standard (6 phases)
**Coverage:** 18/18 requirements mapped

---

## Phases

- [ ] **Phase 1: Infrastructure Skeleton** - Spring Boot process + docker-java connectivity + Elasticsearch template + PostgreSQL schema + clean shutdown lifecycle
- [ ] **Phase 2: Data Collection** - Real-time log streaming from 4 containers, container metrics scraping every 15 seconds, and async log indexing into Elasticsearch with noise pre-filtering
- [ ] **Phase 3: Detection and Alert Management** - Anomaly detection for service-down events, P1-P4 severity classification, SHA-256 deduplication, incident grouping, and PostgreSQL alert state persistence
- [ ] **Phase 4: AI Analysis Agent** - Context bundle assembly (log window ±5 min, metrics snapshot, related service logs), Claude API integration with XML-tagged prompts, structured JSON response parsing
- [ ] **Phase 5: CLI Interface** - Continuous monitoring mode with ANSI color output, interactive free-form question mode, and self-observability status line refreshed every 30 seconds
- [ ] **Phase 6: Simulation and Validation** - Four simulation scripts covering container kill, HTTP load spike, DB connection exhaustion, and Redis OOM; end-to-end demo validation

---

## Phase Details

### Phase 1: Infrastructure Skeleton
**Goal**: A running Spring Boot process that connects to Docker, Elasticsearch, and PostgreSQL, streams logs from all 4 containers, and shuts down cleanly without data loss.
**Depends on**: Nothing (first phase)
**Requirements**: (No v1 requirement IDs — this is the foundation that all v1 requirements depend on; the infrastructure is a prerequisite, not a deliverable requirement)
**Success Criteria** (what must be TRUE):
  1. `./mvnw spring-boot:run` starts the process without errors and connects to all four containers (nginx, node-api, postgres, redis) via docker-java
  2. Log lines from each container appear on stdout within 5 seconds of the process starting, proving the multiplexed Docker frame protocol is handled correctly
  3. Elasticsearch index template is applied at startup with `"dynamic": false` so no mapping explosion can occur on first write
  4. Flyway migrations run at startup and create the PostgreSQL schema (`alerts`, `alert_groups`, `metrics_snapshots`, `ai_analyses` tables) without errors
  5. CTRL-C triggers SmartLifecycle shutdown: all 4 log-streaming virtual threads stop cleanly within the 30-second timeout with no stack traces
**Plans**: TBD

### Phase 2: Data Collection
**Goal**: The system continuously collects, filters, and stores all raw observability data — container logs in Elasticsearch and container metrics in PostgreSQL — at the rates and quality required by the detection layer.
**Depends on**: Phase 1
**Requirements**: DC-01, DC-02, DC-03
**Success Criteria** (what must be TRUE):
  1. Log lines from all 4 containers (nginx, node-api, postgres, redis) are indexed into Elasticsearch continuously; a `GET aiops-logs-*/_count` query shows count increasing while the system runs (DC-01, DC-03)
  2. Health-check pings and static asset requests do NOT appear in Elasticsearch — noise pre-filtering is verified by inspecting indexed documents (DC-03)
  3. PostgreSQL `metrics_snapshots` table receives one row per container every 15 seconds; a SQL count query after 1 minute shows approximately 4 × 4 = 16 rows (DC-02)
  4. Log indexing is async and non-blocking: a simulated ES write delay does not slow down log streaming or anomaly detection (DC-03)
**Plans**: TBD

### Phase 3: Detection and Alert Management
**Goal**: The system detects when a container goes down, classifies the severity correctly, deduplicates repeat alerts, groups related alerts into incidents, and persists all alert state to PostgreSQL — without generating alert storms.
**Depends on**: Phase 2
**Requirements**: AD-01, AM-01, AM-02, AM-03, AM-04
**Success Criteria** (what must be TRUE):
  1. Manually stopping a container triggers an `AnomalyEvent` with severity P1 and the alert appears in PostgreSQL `alerts` table within 30 seconds (AD-01, AM-02, AM-04)
  2. Stopping the same container a second time within 5 minutes does NOT create a second alert row — the SHA-256 deduplication TTL blocks it; a new alert IS created after the 5-minute cooldown expires (AM-01)
  3. Stopping two related containers within a 2-minute window results in a single `alert_groups` record linking both alerts, not two separate unlinked alerts (AM-03)
  4. A CLI query (or direct SQL) returns all open alerts with `opened_at`, `status=ALERTING`, and null `closed_at`, confirming queryable state (AM-04)
  5. P1-P4 classification boundary is enforced: a service-down event is classified P1, a simulated 25% error rate is classified P2, a simulated 10% error rate is classified P3 (AM-02)
**Plans**: TBD

### Phase 4: AI Analysis Agent (MCP)
**Goal**: Spring Boot app exposes an MCP Server with observability tools; when a novel alert fires, an AI Agent session starts where Claude autonomously calls MCP tools to gather context, then returns a structured analysis — without blocking the monitoring loop.
**Depends on**: Phase 3
**Requirements**: AI-01, AI-02, AI-03
**Success Criteria** (what must be TRUE):
  1. MCP Server starts and exposes at minimum 4 tools: `get_logs`, `get_metrics`, `get_related_service_logs`, `list_active_alerts` — verified by an MCP client handshake listing available tools (AI-01)
  2. After a P1 alert fires, an `ai_analyses` row appears in PostgreSQL within 60 seconds with non-null `severity`, at least 2 `root_cause_hypotheses` with `confidence_pct`, and at least 1 `remediation_step` (AI-02, AI-03)
  3. MCP tool calls are logged — Claude can be seen calling `get_logs` and `get_metrics` during analysis; context is gathered dynamically, not pre-assembled (AI-01, AI-02)
  4. The analysis is async: monitoring loop continues emitting log lines to stdout while Claude processes the MCP session — no blocking observable (AI-02)
**Plans**: TBD

### Phase 5: CLI Interface
**Goal**: Operators can run the tool in continuous monitoring mode to see color-coded alerts and AI analyses as they arrive, ask free-form questions about service health in interactive mode, and read a live status line that confirms the system is healthy.
**Depends on**: Phase 4
**Requirements**: CLI-01, CLI-02, CLI-03
**Success Criteria** (what must be TRUE):
  1. In monitoring mode, a P1 alert prints to console in red ANSI, a P2 alert in yellow, a P3 alert in green, and a P4 alert in gray — verified by triggering each scenario with simulation scripts (CLI-01)
  2. In interactive mode, typing "Why is node-api slow?" returns an AI-generated analysis in plain text within 30 seconds, drawing from current logs and metrics for that service (CLI-02)
  3. The status line refreshes every 30 seconds and displays: active stream count (e.g. "4/4"), ES write queue depth, number of Claude calls in the current hour, and time since last anomaly — all fields non-null (CLI-03)
  4. Background monitoring output and the interactive prompt do not visually corrupt each other (Spring Shell stdout conflict is resolved) (CLI-02, CLI-03)
**UI hint**: yes
**Plans**: TBD

### Phase 6: Simulation and Validation
**Goal**: A complete demo can be run using four standalone simulation scripts that reliably trigger every major failure class, proving the full pipeline from Docker event to AI analysis works end-to-end.
**Depends on**: Phase 5
**Requirements**: SIM-01, SIM-02, SIM-03, SIM-04
**Success Criteria** (what must be TRUE):
  1. `simulate-down.sh` stops a target container and within 30 seconds a P1 alert with AI analysis appears on the monitoring console — the complete pipeline fires (SIM-01)
  2. `simulate-load.sh` drives enough HTTP traffic through wrk or ab to produce a measurable error rate increase; an alert fires and AI analysis references the elevated error rate in its evidence (SIM-02)
  3. `simulate-db-exhaustion.sh` creates concurrent connections exceeding postgres `max_connections`; a P1 or P2 alert fires and AI analysis identifies DB connection exhaustion as a root cause hypothesis (SIM-03)
  4. `simulate-redis-oom.sh` fills Redis to its `maxmemory` limit; an alert fires and AI analysis references cache eviction or downstream effects in its evidence (SIM-04)
**Plans**: TBD

---

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Infrastructure Skeleton | 0/0 | Not started | - |
| 2. Data Collection | 0/0 | Not started | - |
| 3. Detection and Alert Management | 0/0 | Not started | - |
| 4. AI Analysis Agent | 0/0 | Not started | - |
| 5. CLI Interface | 0/0 | Not started | - |
| 6. Simulation and Validation | 0/0 | Not started | - |

---

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| DC-01 | Phase 2 | Pending |
| DC-02 | Phase 2 | Pending |
| DC-03 | Phase 2 | Pending |
| AD-01 | Phase 3 | Pending |
| AM-01 | Phase 3 | Pending |
| AM-02 | Phase 3 | Pending |
| AM-03 | Phase 3 | Pending |
| AM-04 | Phase 3 | Pending |
| AI-01 | Phase 4 | Pending |
| AI-02 | Phase 4 | Pending |
| AI-03 | Phase 4 | Pending |
| CLI-01 | Phase 5 | Pending |
| CLI-02 | Phase 5 | Pending |
| CLI-03 | Phase 5 | Pending |
| SIM-01 | Phase 6 | Pending |
| SIM-02 | Phase 6 | Pending |
| SIM-03 | Phase 6 | Pending |
| SIM-04 | Phase 6 | Pending |

**Coverage:** 18/18 v1 requirements mapped. No orphans.

---
*Roadmap created: 2026-05-28*
*Last updated: 2026-05-28 after initial roadmap creation*
