# Research Summary — VDT-AIOps

**Synthesized from:** STACK.md, FEATURES.md, ARCHITECTURE.md, PITFALLS.md
**Date:** 2026-05-28

---

## Executive Summary

VDT-AIOps is a single Spring Boot 3.2 / Java 21 process operating as a long-lived CLI daemon: it tails Docker logs via the Docker Engine API, derives metrics from that stream, runs statistical anomaly detection, deduplicates alerts, assembles evidence bundles, and calls Claude for natural-language root cause analysis. The architecture is event-driven internally (Spring `ApplicationEventPublisher`) so the expensive AI call path never blocks the hot monitoring loop. Virtual threads (Project Loom) make one-thread-per-container log streaming cheap. Spring Shell provides the interactive REPL.

The core value chain is five steps deep and strictly sequential: **log stream → anomaly detection → alert dedup → context bundle → Claude**. Every phase builds exactly one layer of this chain. Getting the log stream correct in Phase 1 is non-negotiable — the multiplexed Docker frame protocol and graceful shutdown lifecycle are the two most disqualifying errors if missed early.

The biggest implementation risks are operational, not algorithmic: Elasticsearch mapping explosion silently stops log ingest; an unbounded context bundle causes Claude to fail precisely during high-load incidents; no alert deduplication means the tool generates the alert fatigue it was designed to solve.

---

## Recommended Stack

| Layer | Technology | Version | Decision |
|-------|------------|---------|----------|
| Runtime | Java | 21 LTS | Virtual threads for log streaming |
| Framework | Spring Boot | 3.2.x | `web-application-type=none`; `spring.threads.virtual.enabled=true` |
| CLI | Spring Shell | 3.2.x | Interactive REPL — Picocli is wrong (no persistent session) |
| Docker API | docker-java + httpclient5 | 3.3.x | De facto Java Docker client; handles multiplexed frame protocol |
| Log storage | elasticsearch-java (official) | 8.13.x | Must match ES server major.minor exactly. NOT Spring Data Elasticsearch |
| Structured state | Spring Data JPA + Flyway | 3.2.x | Alerts, metrics snapshots, AI results. Flyway from day one |
| DB driver | PostgreSQL JDBC | 42.7.x | Standard |
| AI integration | Spring AI (Anthropic) | 1.0.x | **MUST VALIDATE GA status**. Fallback: Spring Boot 3.2 RestClient (built-in) |
| Anomaly detection | Apache Commons Math | 3.6.1 | Z-score + sliding windows. No ML frameworks needed |

**What NOT to use:** `spring-boot-starter-web`, Spring Data Elasticsearch, High Level REST Client (deprecated), RestTemplate, WebFlux/Reactor, Picocli standalone, Weka/Deeplearning4j, Quartz.

---

## Table Stakes Features

| Feature | Notes |
|---------|-------|
| Real-time log tailing (all 4 containers) | Docker API followLogs=true |
| Four Golden Signals | Error rate + latency highest priority; CPU/mem fast follow |
| Health/liveness per service | UP / DOWN / DEGRADED — question zero |
| Threshold anomaly detection | Static first; z-score rolling baseline is Phase 2 |
| Alert deduplication | (service, rule, severity) fingerprint; 5-min cooldown; state-transition firing only |
| P1-P4 severity classification | Defined explicitly in code AND Claude system prompt |
| Log storage (ES) + state storage (PG) | Both required; each for different data shape |
| Context window ±5 min around event | Isolated point-in-time data is useless for RCA |
| UTC millisecond timestamps | Cross-service correlation impossible without this |

**AI differentiators (after table stakes):** RCA hypotheses with confidence %, severity reasoning, cross-service causal chain, natural language queries, bilingual output (Vietnamese/English).

**Anti-features (explicitly out of scope):** Web UI, Slack/email alerting, Kubernetes, RBAC, custom ML models, auto-remediation, OpenTelemetry/OTLP pipeline.

---

## Architecture in One Page

Single Spring Boot process. All concerns communicate via `ApplicationEventPublisher` — never direct method calls across concern boundaries.

```
DockerLogStreamer (1 virtual thread/container)
MetricsCollector (@Scheduled, docker statsCmd)     ──► RawEvent
HealthPoller (@Scheduled, RestClient GET)

         RawEvent
            │
     AnomalyDetector (per-container sliding window, ConcurrentLinkedDeque)
            │ AnomalyEvent
     AlertManager (Caffeine dedup + PG state-transition persistence)
            │ AlertCreatedEvent (novel alerts only)
     ContextBuilder (ES ±5-min log window + PG metrics snapshot)
            │ ContextBundle
     AIAnalysisAgent (Spring AI or RestClient → Claude)
            │ AnalysisResult
     CLIOutputRenderer (ANSI stdout)
     AlertRepository (PG write)

InteractiveShell (Spring Shell) → ContextBuilder → AIAnalysisAgent
LogIndexer (@Async, bounded executor, best-effort ES write — never blocks detection)
```

**Storage split:**
- Elasticsearch: time-series log documents, daily rolling index `aiops-logs-YYYY.MM.DD`, `"dynamic": false`
- PostgreSQL: `alerts`, `alert_groups`, `metrics_snapshots`, `ai_analyses` (hypotheses + remediation as JSONB)

---

## Critical Pitfalls — What to Address Per Phase

### Phase 1 (before any parsing logic)

| Pitfall | Prevention |
|---------|------------|
| Docker multiplexed frame protocol | Use docker-java SDK; never read log stream as plain UTF-8 |
| ApplicationContext vs streaming threads | Implement SmartLifecycle; `spring.lifecycle.timeout-per-shutdown-phase=30s` |
| ES dynamic mapping explosion | Template with `"dynamic": false`, `total_fields.limit: 200` before first write |
| ES index growth / flood-stage | ILM: rollover 1GB/7d, delete after 14d |
| Stream reconnection silent death | Exponential backoff; `since=last_timestamp` resume; 60s watchdog |

### Phase 2 (before detection and context assembly)

| Pitfall | Prevention |
|---------|------------|
| Tool creates its own alert storm | State-transition firing only; 5-min cooldown per (service, rule) in PG |
| Global thresholds misfire | Rolling baseline (mean + 2σ) per service; minimum absolute floor |
| Too much noise in bundle | Pre-filter health checks + static assets; cap 200 lines/service (head+tail+sample) |
| Unbounded bundle under load | Hard cap; include context_stats; pre-compute signal summary |

### Phase 3 (before analysis loop runs)

| Pitfall | Prevention |
|---------|------------|
| Prompt injection from log data | Wrap logs in `<log_data>` tags; label as data in system prompt |
| Cost explosion | `max-calls-per-hour=20`; log every call to PG; haiku for P3/P4 |
| Inconsistent severity | Define P1-P4 precisely in system prompt; require JSON response |
| Rate limit drops analyses | Single-threaded Claude queue; RateLimiter 3/min; retry on 429 |
| HikariCP pool exhaustion | Never hold DB connection across Claude API call |

---

## LLM Integration Key Decisions

**Spring AI vs RestClient:** Spring AI 1.0.x preferred — MUST verify GA status before Phase 3. If still RC, use RestClient (stable, built-in, ~50 lines of boilerplate).

**Prompt structure (XML-tagged):**
```
<background> system context, P1-P4 definitions, language preference </background>
<event> anomaly event JSON </event>
<logs> pre-filtered log lines </logs>
<metrics> metrics snapshot </metrics>
<instructions> analysis steps </instructions>
<output_format> JSON schema </output_format>
```

**Severity contract (in system prompt):**
- P1: service completely unreachable, OR error rate >50% for >2 min, OR data loss
- P2: service degraded, error rate >20%, OR latency >5x baseline
- P3: intermittent errors <20%, OR single non-critical service affected
- P4: informational, no measurable user impact

**Token budget:** Cap bundle at ~80,000 chars. Set `max_tokens=1500`. Use `claude-haiku-4-5` for P3/P4; `claude-sonnet-4-6` for P1/P2.

**Self-observability status line:** `Streams: 4/4 | ES queue: 0 | Claude queue: 0 | Calls: 3/20 | Last anomaly: 4m ago`

---

## Phase-Ordered Roadmap Implications

| Phase | Delivers | Key Risk to Prevent |
|-------|----------|---------------------|
| 1 — Infrastructure skeleton | Spring Boot + Spring Shell + docker-java streaming + ES template + PG schema + clean shutdown | CRIT-1 (frame protocol), CRIT-4 (shutdown), ES mapping explosion |
| 2 — Data collection + detection | Health polling + metrics + log indexing + anomaly detection + alert dedup + PG state | Alert storms (MISTAKE-2), baseline design (MISTAKE-1), bundle pre-filtering |
| 3 — AI analysis agent | Context builder + Claude integration + structured response + rate limiting + cost controls | All LLM pitfalls; validate Spring AI GA first |
| 4 — Interactive shell | Spring Shell REPL, natural language queries, question classifier, bilingual output | Spring Shell + background stdout conflict; question scope (DEMO-4) |
| 5 — Simulation + validation | Demo scenarios (container kill, load spike, DB exhaustion, Redis OOM, bad deploy) | Gradual failure detection (DEMO-1), ES query perf at volume (DEMO-3) |

**Anomaly detection build order within Phase 2:** service down → HTTP 5xx rate → DB connection exhaustion. These three give best AI demo value and are easiest to simulate.

---

## Open Questions — Resolve Before or During Phase 1 Planning

1. **Spring AI 1.0 GA** — validate at `https://spring.io/projects/spring-ai`. If still RC, use RestClient from day one.
2. **Official Anthropic Java SDK** — check `https://docs.anthropic.com/en/api/client-sdks`.
3. **docker-java exact patch** — verify `https://github.com/docker-java/docker-java/releases`; confirm httpclient5 is recommended transport for 3.3.x.
4. **ES server version** — must be pinned in `docker-compose.yml` before writing any client code. Client major.minor must match server exactly.
5. **Commons Math 4 GA** — check `https://commons.apache.org/proper/commons-math/`; start on CM4 if stable.
6. **Spring Shell REPL + background stdout** — validate interactive prompt doesn't conflict with monitoring threads printing to same stdout.
7. **Log volume measurement** — measure actual lines/5-min window under simulated load before hardcoding the 200-line cap.
8. **ES vs direct Docker API for context window** — direct Docker API `--since`/`--until` is simpler for v1; decide before Phase 2.
