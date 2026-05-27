# Features Research — VDT-AIOps

**Domain:** AIOps / Observability CLI tool for alert fatigue reduction
**Researched:** 2026-05-28
**Confidence:** MEDIUM-HIGH (AIOps/SRE domain is mature and well-documented)

---

## Table Stakes

Features every serious observability tool must have. Missing any of these makes the tool untrustworthy to an SRE audience.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Real-time log tailing | Foundational — SREs expect `tail -f` semantics over containers | Low | Docker API `logs --follow`. Users abort immediately if logs are stale |
| The Four Golden Signals | Latency, traffic, errors, saturation — Google SRE canon | Medium | Error rate + latency highest priority; saturation (CPU/mem) fast follow |
| Health/liveness check per service | "Is it up?" is question zero — must answer before AI analysis makes sense | Low | Poll `/health` endpoints or Docker container state. Binary: UP/DOWN/DEGRADED |
| Threshold-based anomaly triggers | Static baselines: error rate > N%, latency > Xms, service unreachable | Medium | Static thresholds first; dynamic baselines are differentiator territory |
| Alert deduplication | Same root cause firing 50 alerts in 60s = alert storm | Medium | Keyed by (service, metric, threshold). Time-window suppression: 1 alert per 5 min per key |
| Severity classification (P1-P4) | Without severity, everything looks equally urgent — the core alert fatigue problem | Low-Med | P1=service down, P2=degraded, P3=anomalous but functional, P4=informational |
| Structured output + log storage | Alerts must be persisted and queryable, not just printed to stdout | Medium | PG for structured state + alert history; ES for log search |
| Timestamp precision (UTC, ms) | Correlation across services is impossible without this | Low | Non-negotiable for distributed system debugging |
| Context window around event | When anomaly fires, must collect logs ±N minutes — isolated point-in-time data is useless for RCA | Medium | ±5 min is the right default |
| Graceful error handling | Connection failures, Docker daemon unavailable, API timeouts must not crash | Low-Med | Print clear error + retry. A tool that crashes during an incident is worse than no tool |

---

## Differentiators (AI-Powered)

Features where LLM integration creates unique value unavailable in traditional rule-based tools.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| AI root cause hypotheses | Translates raw log noise into "3 probable causes, ranked by likelihood" — replaces 20 min of manual grep | High | Quality proportional to context bundle quality |
| Severity scoring with reasoning | P1-P4 with justification ("P2 because error rate 12%, SLO threshold 5%, trend rising") | High | LLM reasoning outperforms pure threshold rules |
| Remediation step generation | "Run these 3 commands to likely fix this" — actionable, not just diagnostic | High | Must be scoped to specific stack (Docker Compose, nginx, postgres) |
| Cross-service causal chain | "nginx 502s ← node-api crash ← postgres connection pool exhaustion" — the full chain | High | Requires related service logs in context bundle |
| Natural language query interface | "Why is redis slow?" → Agent searches logs, analyzes, responds | High | Key differentiator vs traditional grep/dashboard tools |
| AI alert noise reduction | AI classifies alerts as signal vs noise — suppresses flapping/transient alerts | High | Beyond dedup: AI understands "postgres retry spike during deploy" ≠ incident |
| Confidence scoring on hypotheses | "Root cause A: 85%, B: 40%" — tells user where to look first | Medium | Requires explicit prompt engineering for Claude to express uncertainty |
| Bilingual output (Vietnamese/English) | Lowers barrier for Vietnamese-speaking ops teams | Low | Prompt engineering task, not architecture. High value for target audience |

---

## Nice to Have (v2+)

Valuable but deferrable. Do not block v1.

| Feature | Why Defer |
|---------|-----------|
| Dynamic baselines / statistical anomaly detection | Requires hours/days of accumulated time-series data |
| Distributed trace correlation | Requires OpenTelemetry instrumentation in simulated services |
| SLO/SLA tracking + error budget burn rate | Production-grade tooling (Datadog/New Relic level). Overkill for learning project |
| Historical incident search UI | Good v2 once data accumulates in ES |
| Runbook integration | AI fetches relevant runbook before suggesting remediation |
| Trend forecasting ("OOM in ~45 minutes") | Requires time-series model |
| Configurable alert rules via YAML | Hardcoded thresholds fine for learning project |
| Streaming AI responses | Request/response model sufficient |
| Rich TUI dashboard (k9s-style) | Project specifies CLI-only |
| Incident narrative / post-mortem generation | High value but not on critical path |

---

## Anti-Features for v1

Explicitly NOT building. Deliberate scope control.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Web dashboard / UI | Doubles scope, shifts focus from AI/detection | CLI output + `--format json` |
| Email / SMS / Slack alerting | Webhook config, secrets, delivery guarantees — a project in itself | Store in PostgreSQL. CLI output is the notification |
| Kubernetes / cloud support | Different APIs, auth, mental model | Docker Compose only |
| Multi-tenant / RBAC | Auth adds friction before AI value is demonstrated | Single-user local tool |
| Custom ML models / local inference | Training pipelines = separate research project | Claude API — LLM reasoning on logs is already differentiated |
| Auto-remediation (auto-restart, auto-scale) | Dangerous without extensive safeguards | Suggestions only. Human executes |
| OpenTelemetry / OTLP pipeline | Separate observability discipline | Direct Docker API + health endpoint polling |
| Prometheus/Alertmanager integration | Production-grade complexity | Direct metric scraping from health endpoints |

---

## Feature Dependencies

```
Docker API log streaming
  └── Real-time log tailing (all 4 services)
       └── Log window extraction (±5 min around trigger)
            └── Context Bundle construction
                 └── AI analysis via Claude API (severity, RCA, remediation)
                      ├── Monitoring mode (automated alerts)
                      └── Interactive mode ("why is X slow?")

Health endpoint polling + Docker container state
  └── Service liveness (UP/DOWN/DEGRADED)
       └── "Service down" trigger (P1)
            └── Context Bundle construction

Metrics scraping (CPU, mem, request rate, error rate)
  └── Four Golden Signals per service
       └── Threshold-based anomaly detection
            └── Alert generation
                 └── Alert deduplication (Caffeine + PG persistence)
                      └── Alert severity classification (P1-P4 rule engine)
                           └── AI severity scoring with reasoning

PostgreSQL
  ├── Alert dedup state (persists across restarts)
  ├── Alert history + incident log
  └── AI analysis results (severity, hypotheses, remediation)

Elasticsearch
  ├── Log window extraction (time-range + service filter)
  └── Historical log search (v2)

Simulation scripts
  └── All anomaly types demonstrable reproducibly
       └── AI output quality assessable against known ground truth
```

### Critical Path (Irreducible Core)

```
1. Docker API log stream
2. Error rate / service-down detection
3. Context bundle (±5 min logs + related service logs)
4. Claude API call with structured bundle
5. P1-P4 + RCA output to stdout
```

Everything else enhances or wraps this chain.

---

## Anomaly Type Priority Ordering

| Rank | Anomaly | Signal Source | AI Analysis Quality | Demo Value |
|------|---------|--------------|---------------------|-----------|
| 1 | Service completely down (container stopped) | Docker container state | HIGH — clear causal chain | HIGH — immediate, visible |
| 2 | HTTP 5xx error rate spike | nginx access logs, app logs | HIGH — log patterns very informative | HIGH — realistic failure mode |
| 3 | DB connection pool exhaustion | postgres logs, app error logs | HIGH — multi-service causal chain | HIGH — classic distributed failure |
| 4 | High latency (P95 > threshold) | app logs with request timing | MEDIUM — needs time-series context | MEDIUM — harder to trigger reliably |
| 5 | Memory/CPU saturation | Docker stats API | MEDIUM — metrics-only, less log context | MEDIUM — requires sustained load |
| 6 | Redis cache miss / connection failure | redis logs, app logs | MEDIUM — often secondary symptom | MEDIUM — good supporting evidence |

**Recommendation:** Build anomaly detection in this priority order. Types 1-3 provide best AI demonstration value and are easiest to simulate with Docker Compose.

---

## AI Output Format — What Makes RCA Actionable

Based on Google SRE Book + PagerDuty/Incident.io post-mortem standards:

**Good output structure per incident:**
```
SEVERITY: P2
AFFECTED: node-api, postgres

SUMMARY: Postgres connection pool exhaustion caused node-api to return HTTP 503s.

ROOT CAUSE HYPOTHESES:
1. [85%] Postgres max_connections reached — 47 "connection refused" errors
   in postgres logs between 14:31-14:36, pool at 100/100
2. [40%] Slow query holding connections — 3 queries > 5000ms in same window

IMPACT:
- node-api: 503 error rate 94% (baseline 0.1%) since 14:31
- nginx: downstream 502s to all clients

REMEDIATION:
1. Immediate: terminate idle connections via pg_terminate_backend()
2. Short-term: increase max_connections, restart postgres
3. Root fix: implement PgBouncer connection pooling in node-api

ANALYZED WINDOW: 14:26 - 14:41 (±5 min around trigger at 14:31)
```

**Anti-patterns in AI output to avoid:**
- Generic advice ("check your logs") without citing specific log lines
- All hypotheses presented equally — no confidence scores
- Conclusions without supporting evidence
- Missing time window — user can't know what was analyzed
- Remediation without safety caveats for destructive commands

---

## Log Pattern Taxonomy — Signal vs Noise

**High-signal (always include in context bundle):**
- `ERROR` / `FATAL` / `PANIC` + stack traces
- HTTP 5xx in access logs
- `connection refused` / `connection reset` / `timeout`
- `OOM killed` / `out of memory`
- `max connections reached` / `too many open files`
- Container exit codes != 0

**Medium-signal (include with context):**
- `WARN` messages — leading indicators, often precede failures
- Slow query logs (> threshold ms)
- Retry/backoff messages — indicates upstream pressure
- HTTP 4xx spikes — client errors usually, but spikes indicate change

**Noise (filter before sending to Claude):**
- Health check pings (`GET /health 200`)
- Routine cron output
- `DEBUG` logs during normal operation
- Connection pool keep-alive messages
- Expected startup/shutdown sequences

**Key insight:** Pre-filter noise before context bundle construction. Every noise token is a token not spent on signal. Pre-filtering improves AI analysis quality materially.

---

## Simulation Scenarios — Best for Demo

| Scenario | How to Trigger | Anomaly Type | Expected AI Analysis |
|----------|---------------|--------------|---------------------|
| Container kill | `docker stop node-api` | Service down (P1) | "node-api not running. nginx 502s are downstream effect." |
| Load spike | `wrk` against heavy endpoint | Latency + error rate (P2) | "P95 latency exceeds threshold. CPU saturation or slow DB queries." |
| DB connection exhaustion | Many concurrent connections, low `max_connections` | Connection pool (P2) | Multi-service causal chain — most impressive demo |
| Redis OOM | Fill to `maxmemory` limit | Cache eviction (P3) | "Redis hit memory limit. Eviction active. Cache hit rate dropping." |
| Bad deploy | Container with startup error | Crash loop (P1) | "node-api exits immediately. Likely config or dependency error." |

---

## Interactive Mode UX Patterns

| Pattern | Good Practice | Anti-Pattern |
|---------|---------------|--------------|
| Query understanding | Accept fuzzy language: "why slow", "what broke", "postgres issues" | Require exact syntax |
| Response time | Show progress indicator; < 5s simple, up to 30s for full RCA | Silent wait |
| Evidence display | Show which log lines / metrics informed the answer | Abstract "based on analysis" with no backing data |
| Scope clarity | State "Analyzing last 5 minutes of logs for node-api and postgres" | Ambiguous about what was analyzed |
| Error recovery | "Couldn't find logs for that window. Try: [suggestion]" | Silent failure or crash |
| Language | `--lang vi` flag for Vietnamese output | English-only |

---

## Open Questions

- Does Claude API's context window fit ±5 min log volume for all 4 services simultaneously? (Measure actual log volume per service per 5-min window under load)
- What log format does each container emit? nginx, node-api, postgres, redis each have different schemas — noise-filter and context builder need format-specific parsers
- How does alert dedup state survive JVM restarts during development? PostgreSQL persistence recommended over Caffeine-only
- ES vs direct Docker API log query for context window: ES adds complexity but enables richer queries; direct Docker API may be simpler for v1
