# Duckompose · Engine

The **always-on brain** — a Java 21 / Spring Boot engine that watches the monitored system, detects
anomalies, correlates them into incidents, and drives an AI Agent to diagnose each one.

> Part of [Duckompose](../../README.md). See also the [infra](../infra/README.md) and [TUI](../tui/README.md).

---

## Pipeline

```
Docker logs / Prometheus  ─▶  detect (static thresholds)  ─▶  correlate (dependency graph)
        │                                                              │
        ▼                                                              ▼
   persist logs                                          AI Agent investigates (Claude, 4-phase loop)
                                                                       │
                                                                       ▼
                                            persist IncidentReport  ─▶  stream to TUI (WebSocket)
```

The 5-second detection scan fans out **one virtual thread per service** (Project Loom), and each
investigation runs on a virtual thread too — blocking I/O (log streams, Prometheus, Docker, LLM calls)
made cheap.

---

## Package map (`com.vdt.aiops`)

| Package | Responsibility |
|---------|----------------|
| `monitoring.logcollector` | Stream container logs via Docker (one virtual thread each), normalize to templates, batch-persist |
| `monitoring.metricscraper` | Query Prometheus (PromQL) + read Docker `stats`; hybrid metric collection |
| `monitoring.detection` | `AnomalyDetector` — scan services against static-threshold `AnomalyRule`s (parallelized) |
| `monitoring.alertmanager` | Create/dedup/resolve alerts · `AlertCorrelator` (Union-Find) · `CorrelationTick` (debounce + dispatch) |
| `topology` | `ServiceGraph` built from `depends_on` labels — up/downstream + topological root |
| `agent.context` | `ContextBuilder` — assembles the rich context bundle around an incident |
| `agent.loop` | `Query` — the **4-phase agentic loop** (assemble → call LLM → run tools → feed back) |
| `agent.incident` | `IncidentReport` — the structured, evidence-backed diagnosis |
| `agent.interact` | Interactive **chat** mode (token streaming, memory) + `defense` (auto-compaction) |
| `agent.prompt` | System prompts for investigation & chat |
| `tools.read` | **Read-only** diagnostic tools the agent can call (logs, metrics, alerts, dependencies, inspect…) |
| `config` | Executors (virtual threads), WebSocket, and typed `AiopsProperties` |
| `utils` | Helpers — `ServiceName`, `ServiceType`, `JsonExtractor`, `MonitoredServices`… |

**The agent loop** ([`agent/loop/Query.java`](src/main/java/com/vdt/aiops/agent/loop/Query.java)) drives tool
execution **manually** (`internalToolExecutionEnabled(false)`) with a `MAX_TURNS` guard, a
*force-final-answer* fallback, and prose-tolerant JSON extraction into `List<IncidentReport>`.

---

## Data & schema

State lives in PostgreSQL (`alerts`, `incidents`, `service_logs`). The schema is **owned by Flyway**
([`src/main/resources/db/migration`](src/main/resources/db/migration)) and only *validated* by JPA at
startup (`ddl-auto: validate`).

---

## Configuration

Key knobs in [`application.yaml`](src/main/resources/application.yaml):

| Setting | Meaning |
|---------|---------|
| `aiops.monitoring.poll-interval-ms` | detection/collection cadence (default 5000) |
| `aiops.anomaly.rules` | static-threshold rules per service *type* |
| `aiops.correlation.quiet-window-seconds` / `max-wait-seconds` | debounce before dispatching a group (30 / 120) |
| `aiops.agent.max-turns` / `max-concurrent` | agent tool-call budget & concurrent investigations |
| `spring.ai.anthropic.chat.options.model` | the Claude model (`claude-sonnet-4-6`) |

**Required:** `ANTHROPIC_API_KEY` (via env or `.env`). The embedded server exists only to host the
**WebSocket endpoint** (`:8088`) that streams events to the TUI — there is no public REST API.

---

## Running

The engine needs the platform infra (Prometheus, state DB, Docker access). Two ways:

**Via Docker (recommended)** — see [infra](../infra/README.md); the backend compose builds and runs it.

**Locally (for development):**
```bash
# from repo root, with the backend infra already up (prometheus, postgres-aiops, docker-proxy)
cd aiops/app
mvn spring-boot:run
```
Local defaults: Docker `tcp://localhost:2375`, state DB `localhost:5433`, Prometheus `localhost:9090`
— all overridable by env (the container sets them to the in-network service names).

---

## Tech

Java 21 (virtual threads) · Spring Boot 3.5 · Spring AI (Claude) · `docker-java` · Prometheus/PromQL ·
Spring Data JPA · Flyway · WebSocket.
