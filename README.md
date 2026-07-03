<!-- ═══════════════ IMAGE 1 — LOGO ═══════════════
     Paste the Duckompose duck logo. Save as: docs/images/logo.png -->
<p align="center">
  <img src="docs/images/logo.png" alt="Duckompose" width="120">
</p>

<h1 align="center">Duckompose</h1>

<p align="center">
  <b>An AIOps platform where an AI Agent auto-investigates infrastructure incidents —<br>from a raw alert all the way to an evidence-backed root cause.</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange" alt="Java 21">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Spring%20AI-Claude%20Sonnet-8A2BE2" alt="Spring AI">
  <img src="https://img.shields.io/badge/React%20Ink-TUI-61DAFB" alt="React Ink">
  <img src="https://img.shields.io/badge/Docker-Compose-2496ED" alt="Docker">
  <img src="https://img.shields.io/badge/Prometheus-PromQL-E6522C" alt="Prometheus">
</p>

<p align="center"><i>Viettel Digital Talent 2026 · Software Engineer track</i></p>

---

## The problem — the gap between *"alert"* and *"action"*

Modern systems are meshes of interdependent microservices that emit a sea of logs, metrics
and alerts every second. When one service fails, a **single fault cascades into a storm of
alerts** across the whole system. The on-call engineer then faces:

- **Alert fatigue** — too many signals, hard to tell what actually matters.
- Alerts that only say *"something is wrong"*, not **why**.
- **Manual investigation** that leans heavily on individual experience.

**Duckompose closes that gap.** When an anomaly fires, it doesn't just raise an alert — it
builds a rich **context bundle** around the event and lets an **AI Agent investigate it like a
human SRE would**, returning a diagnosis you can act on immediately.

<!-- ═══════════════ IMAGE 2 — TUI SCREENSHOT ═══════════════
     Paste the terminal UI screenshot (the DUCKOMPOSE banner + feed).
     Save as: docs/images/tui.png -->
<p align="center">
  <img src="docs/images/tui.png" alt="Duckompose TUI" width="820">
</p>

---

## ✨ Key features

- 🛰️ **Real-time collection** — streams container logs via the Docker Engine (one virtual
  thread per container) and scrapes metrics from Prometheus with PromQL.
- 🚨 **Anomaly detection** — static-threshold rules per service *type* (service down, high
  P99 latency, error-rate spike, Redis OOM, DB connection exhaustion). The 5-second scan
  **fans out across virtual threads**, one per service.
- 🕸️ **Alert correlation** — a **Union-Find over the service dependency graph** groups related
  alerts into a single incident candidate and picks the topological root, so multiple
  symptoms of one fault become **one incident, not a storm**.
- 🧠 **Agentic investigation** — a self-driven **4-phase loop** calls Claude with read-only
  diagnostic tools, gathers evidence, and returns structured `IncidentReport`s.
- 🔎 **Evidence discipline** — every diagnosis separates **validated findings** from
  **hypotheses** (each with *what still needs checking*) and **cites the exact log/metric**
  it relied on — no hand-waving.
- 💬 **Interactive chat** — ask *"why is node-api slow?"* and the same agent brain answers in
  natural language, with token streaming and conversational memory.
- 🖥️ **Terminal UI** — a React Ink TUI (`duckompose`) shows investigations live over WebSocket.
- 🧪 **Failure simulator** — scripts to reproduce realistic incidents on demand.

---

## 🏗️ Architecture — four layers

<!-- ═══════════════ IMAGE 3 — 4-LAYER ARCHITECTURE (drawio) ═══════════════
     Paste the "Kiến trúc tổng quan 4 lớp" drawio (slide 7 / report Hình 3.1).
     Save as: docs/images/architecture.png -->
<p align="center">
  <img src="docs/images/architecture.png" alt="Four-layer architecture" width="720">
</p>

| Layer | Responsibility | Core components |
|-------|----------------|-----------------|
| **Collection** | Ingest raw telemetry | Log Collector · Metric Collector |
| **Detection & Correlation** | Turn telemetry into incidents | Anomaly Detector · Alert Manager · Alert Correlator + Service Graph |
| **Investigation** | Diagnose the incident | Context Builder · AI Agent + diagnostic tools |
| **Storage & Presentation** | Persist & surface results | JPA repositories · WebSocket server → TUI |

**End-to-end flow:** `detect → correlate → investigate → persist → stream to TUI`.

---

## 🔬 How the Agent investigates — the 4-phase loop

<!-- ═══════════════ IMAGE 4 — AGENT 4-PHASE LOOP (drawio) ═══════════════
     Paste the "Lớp Investigation" flowchart (slide 9 / report Hình 3.3).
     Save as: docs/images/agent-loop.png -->
<p align="center">
  <img src="docs/images/agent-loop.png" alt="Agent 4-phase reasoning loop" width="720">
</p>

1. **Assemble Context** — system prompt + context bundle + the expected JSON output schema.
2. **Call LLM** — Claude decides whether it needs more evidence.
3. **Execute Tools** — read-only diagnostic tools (logs, metrics, alerts, dependencies,
   container inspect) run and their results are fed back.
4. **Feed Results Back** — loop until Claude is confident (bounded by a turn budget), then it
   emits a `List<IncidentReport>`.

Tool execution is driven **manually** (not auto) so the loop stays under our control — with a
`MAX_TURNS` guard, a *force-final-answer* fallback, and prose-tolerant JSON extraction.

---

## 🗃️ Data model

<!-- ═══════════════ IMAGE 5 — ERD ═══════════════
     Paste the DB schema / ERD (slide 10 / report Hình 3.4): incidents · alerts · service_logs.
     Save as: docs/images/erd.png -->
<p align="center">
  <img src="docs/images/erd.png" alt="Database ERD" width="720">
</p>

- **`service_logs`** — logs streamed from containers in real time.
- **`alerts`** — fired anomalies (service, type, status, `is_active`), linked to their incident.
- **`incidents`** — the agent's diagnosis: root cause, severity, validated findings,
  hypotheses, recommended actions and cited evidence.

Schema is owned by **Flyway migrations** and only *validated* by JPA at startup.

---

## 🛠️ Tech stack

| Area | Technology |
|------|-----------|
| **Engine** | Java 21 (virtual threads / Project Loom), Spring Boot 3.5, WebSocket |
| **AI** | Spring AI → Claude (Sonnet 4.6), tool-calling agent loop |
| **Collection & Metrics** | `docker-java`, Prometheus + PromQL |
| **Storage** | PostgreSQL, Spring Data JPA, Flyway |
| **Infrastructure** | Docker Compose, Socat proxy (Docker socket → TCP) |
| **TUI** | React + Ink (TypeScript), WebSocket |

---

## 🚀 Getting started

### Prerequisites
- Docker Desktop (Docker Compose v2)
- Node.js 18+ (for the TUI)
- An **`ANTHROPIC_API_KEY`**

### 1 · Configure secrets
Create `aiops/infra/.env`:
```env
ANTHROPIC_API_KEY=sk-ant-...
AIOPS_DB_PASSWORD=change_me
APP_DB_PASSWORD=change_me
```

### 2 · Start the stack
> ⚠️ Start the **simulation first** — it creates the shared Docker network the engine joins.
```bash
# 1) Simulated microservice topology (project: aiops-sim)
docker compose -f aiops/infra/docker-compose.yml up -d

# 2) The AIOps engine + Prometheus + state DB (project: aiops-platform)
docker compose -p aiops-platform -f aiops/infra/docker-compose.backend.yml up -d --build
```

### 3 · Launch the TUI
```bash
duckompose            # if the global command is installed
# or, straight from source:
node aiops/tui/dist/index.js
```
The TUI connects to the engine at `ws://localhost:8088/ws/incidents`.

---

## 🧪 Simulating incidents

From `aiops/infra/scripts/` (PowerShell):

| Scenario | Command |
|----------|---------|
| **Service down** | `.\simulate-container-down.ps1 -Service <name>` |
| **High latency** | `.\simulate-high-latency.ps1 -Service <name>` |
| **Redis OOM** | `.\simulate-redis-oom.ps1` |
| **DB exhaustion** | `.\simulate-db-exhaustion.ps1 -Service <name>` |
| **Load / error spike** | `.\simulate-load-spike.ps1 -Service <name>` |
| **Recover everything** | `.\recover.ps1 <scenario>` |

Trigger a scenario, then watch the TUI: an alert fires → related alerts group → the agent
investigates → an evidence-backed incident appears.

<!-- ═══════════════ IMAGE 6 — RESULT / INCIDENT REPORT (optional) ═══════════════
     Optional: paste the "Kết quả đạt được" incident screenshot (slide 13).
     Save as: docs/images/result.png -->

---

## 📁 Project structure

```
VDT_AIOPS/
├── aiops/
│   ├── app/     # Java Spring Boot engine (detection, correlation, agent, storage)
│   ├── tui/     # React Ink terminal UI
│   └── infra/   # docker-compose files, prometheus.yml, simulation scripts, mock services
├── docs/        # diagrams & images
└── README.md
```

---

## ⚠️ Limitations & roadmap

| Limitation | Direction |
|------------|-----------|
| Detection uses **static thresholds** (no adaptive baseline) | Statistical / adaptive detection (z-score, seasonal baselines) |
| Correlation is **time-window based** — a lagging symptom can be split from its root cause | **Post-hoc causal linking**, not just a fixed quiet-window |
| The service **dependency graph is built once at startup** | Dynamic topology updates (Docker events / periodic refresh) |
| **Single-process engine**, bounded concurrency; chat history in memory | Split into queue-fed workers with shared state (PG/Redis) |
| **No quantitative eval** of diagnosis quality yet | Labelled-incident eval harness with accuracy metrics |

---

## 👤 Author

**Nguyễn Văn Hùng** · Viettel Digital Talent 2026 
📧 darkisknight126@gmail.com

<p align="center"><i>Built for learning — understanding AIOps patterns, context-driven AI analysis, and integrating LLMs into observability workflows.</i></p>
