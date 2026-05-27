# Architecture Research — VDT-AIOps

**Domain:** AIOps CLI Agent — Docker Container Monitoring + AI-Driven Incident Analysis
**Researched:** 2026-05-28

## System Overview

The system is a single Spring Boot process running as a long-lived CLI monitoring daemon. Five loosely-coupled concerns — data collection, anomaly detection, context assembly, AI analysis, and alert lifecycle management — communicate via Spring's `ApplicationEventPublisher` (internal event bus) rather than direct method calls. This keeps each concern independently testable and keeps the expensive AI call path off the hot monitoring loop.

```
┌─────────────────────────────────────────────────────────────────┐
│  Spring Boot Process (CLI — ApplicationRunner entry point)      │
│                                                                 │
│  ┌──────────────┐    ┌──────────────────┐    ┌──────────────┐  │
│  │  Docker      │    │  Metrics         │    │  Health      │  │
│  │  Log         │    │  Collector       │    │  Poller      │  │
│  │  Streamer    │    │  (Stats API)     │    │              │  │
│  └──────┬───────┘    └────────┬─────────┘    └──────┬───────┘  │
│         └──────────────── RawEvent ─────────────────┘          │
│                               │                                 │
│                    ┌──────────▼──────────┐                      │
│                    │  Anomaly Detector   │                      │
│                    │  (Sliding Window)   │                      │
│                    └──────────┬──────────┘                      │
│                               │ AnomalyEvent                   │
│                               │                                 │
│                    ┌──────────▼──────────┐                      │
│                    │  Alert Manager      │◄── Caffeine cache    │
│                    │  (dedup + group)    │    (in-memory TTL)   │
│                    └──────────┬──────────┘                      │
│                               │ AlertCreatedEvent               │
│                               │                                 │
│              ┌────────────────▼────────────────┐                │
│              │     Context Builder              │                │
│              │  log window ±5 min (ES query)   │                │
│              │  metrics snapshot (PG)          │                │
│              │  related service logs (ES)      │                │
│              └────────────────┬────────────────┘                │
│                               │ ContextBundle (JSON)           │
│                               │                                 │
│              ┌────────────────▼────────────────┐                │
│              │     AI Analysis Agent           │                │
│              │  (Spring AI + Anthropic Claude) │                │
│              └────────────────┬────────────────┘                │
│                               │ AnalysisResult                 │
│                               │                                 │
│         ┌─────────────────────▼──────────────────────┐         │
│         │  Output Layer                              │         │
│         │  CLI console (ANSI formatted)              │         │
│         │  PostgreSQL (alert state + AI results)     │         │
│         │  Elasticsearch (logs indexed)              │         │
│         └────────────────────────────────────────────┘         │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  Interactive Shell (Spring Shell)                         │  │
│  │  "Why is service X slow?" → Agent → CLI response         │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
         │                          │
    docker.sock              ┌──────▼──────────────┐
    (Docker API)             │  Docker Compose      │
                             │  nginx, node-api     │
                             │  postgres, redis     │
                             │  elasticsearch       │
                             │  postgresql          │
                             └─────────────────────┘
```

## Component Breakdown

| Component | Responsibility | Communicates With |
|-----------|---------------|-------------------|
| **DockerLogStreamer** | Opens persistent `logContainerCmd` (followLogs=true) per container via docker-java. Emits `RawLogEvent` per line. | AnomalyDetector (via Spring event), LogIndexer |
| **MetricsCollector** | Polls `docker stats` via `statsCmd` on `@Scheduled` interval (default 10s). Emits `MetricsSnapshot`. | AnomalyDetector, PostgreSQL writer |
| **HealthPoller** | HTTP GET to container health endpoints on `@Scheduled` interval. Emits `HealthEvent`. | AnomalyDetector |
| **AnomalyDetector** | Per-container sliding windows. Evaluates rules: error-rate spike, latency breach, service unresponsive. Emits `AnomalyEvent` when rule fires. | AlertManager (via Spring event) |
| **AlertManager** | Deduplicates via SHA-256 fingerprint of `(container, rule, severity)` + Caffeine TTL cache (default 5 min). Groups correlated alerts by time proximity. Emits `AlertCreatedEvent` for novel alerts only. | ContextBuilder (via Spring event), PostgreSQL |
| **ContextBuilder** | On `AlertCreatedEvent`, queries ES for ±5-min log window, queries PG for metrics snapshots, fetches related service logs. Serializes to `ContextBundle` JSON. | AIAnalysisAgent, Elasticsearch, PostgreSQL |
| **AIAnalysisAgent** | Accepts `ContextBundle`, constructs XML-tagged prompt, calls Claude via Spring AI `ChatClient`, parses structured JSON response into `AnalysisResult`. | Output Layer, PostgreSQL |
| **LogIndexer** | Bulk-indexes `LogEntry` documents from `RawLogEvent` stream. Provides time-range queries for ContextBuilder. `@Async` so it never blocks the detection path. | Elasticsearch |
| **AlertRepository** | Spring Data JPA. Persists alert state, incidents, `AnalysisResult` (severity, hypotheses, remediation). | PostgreSQL |
| **InteractiveShell** | Spring Shell commands. Freeform user question → infers scope → builds `ContextBundle` → calls `AIAnalysisAgent` → prints response. | AIAnalysisAgent, CLI output |
| **CLIOutputRenderer** | Renders `AnalysisResult` and anomaly notifications to stdout with ANSI color. Passive consumer — nothing depends on it. | Terminal (sink) |

## Data Flow

### Monitoring Loop (automated path)

```
docker-java log stream
  → DockerLogStreamer: RawLogEvent published
  → LogIndexer: writes to ES @Async (best-effort, non-blocking)
  → AnomalyDetector: updates sliding window, evaluates rules
      → if rule fires: AnomalyEvent published
          → AlertManager: Caffeine dedup check
              → if new: persists to PG, AlertCreatedEvent published
                  → ContextBuilder: assembles ContextBundle (ES + PG queries)
                      → AIAnalysisAgent: Claude API call
                          → AnalysisResult persisted to PG
                          → CLIOutputRenderer: prints to console
```

### Interactive Path (user-initiated)

```
Spring Shell command input
  → InteractiveShell: parses intent, identifies scope (service, time range)
  → ContextBuilder: assembles ContextBundle for that scope
  → AIAnalysisAgent: Claude API call with question + context
  → Response rendered to console (Vietnamese or English per user preference)
```

## Storage Schema Patterns

**Elasticsearch — daily rolling index `aiops-logs-YYYY.MM.DD`:**
```json
{
  "timestamp": "ISO-8601 UTC",
  "container": "nginx",
  "service": "nginx",
  "level": "ERROR",
  "message": "raw log line",
  "parsed": { "status": 502, "path": "/api/users", "latency_ms": 3241 }
}
```
Key queries: time-range + container filter for context window, error-level filter for signal extraction.

**PostgreSQL — structured state tables:**
- `alerts` — id, container, rule, severity, fingerprint, created_at, suppressed_until
- `alert_groups` — id, trigger_alert_id, member_alert_ids[], grouped_at
- `metrics_snapshots` — id, container, cpu_pct, mem_mb, req_rate, error_rate, recorded_at
- `ai_analyses` — id, alert_id, severity, hypotheses (JSONB), remediation_steps (JSONB), confidence, model, analyzed_at

**ContextBundle (in-memory serialization):**
```java
public record ContextBundle(
    AnomalyEvent triggerEvent,
    List<LogEntry> logWindowBefore,      // 5 min before event
    List<LogEntry> logWindowAfter,       // 5 min after event (if available)
    List<MetricsSnapshot> metricsNearEvent,
    Map<String, List<LogEntry>> relatedServiceLogs,
    Instant bundleBuiltAt
) {}
```

Token budget: truncate log windows to configurable char limit (default 80,000 chars). Prioritize: ERROR/FATAL lines > WARN lines > stack traces > slow-request markers > normal lines.

## Key Implementation Patterns

### Pattern 1: Virtual Threads for Docker Log Streaming

```java
// One virtual thread per container — cheap, non-blocking
for (String containerId : monitoredContainers) {
    Thread.ofVirtual()
        .name("log-stream-" + containerName)
        .start(() -> streamLogsBlocking(containerId));
}
// awaitCompletion() blocks the virtual thread; platform threads unaffected
```

### Pattern 2: Sliding Window Anomaly Detection

Per-container `ConcurrentLinkedDeque<LogEntry>` bounded by time horizon (e.g., 60s). On each new entry, expire stale entries, evaluate all registered `DetectionRule` implementations. Rules are Spring `@Component` beans — add new rules without touching the detector.

### Pattern 3: Caffeine Dedup Cache

```java
Cache<String, Instant> dedupCache = Caffeine.newBuilder()
    .expireAfterWrite(5, TimeUnit.MINUTES)
    .maximumSize(1000)
    .build();

String fingerprint = sha256(event.containerName() + event.ruleName() + event.severity());
if (dedupCache.getIfPresent(fingerprint) == null) {
    dedupCache.put(fingerprint, Instant.now());
    // proceed to context build + AI call
}
```

### Pattern 4: XML-Tagged Claude Prompt

```java
String prompt = """
    <background>
    AIOps assistant analyzing a 4-service stack: nginx, node-api, postgres, redis.
    Respond in Vietnamese unless the user asks in English.
    </background>
    <event>%s</event>
    <logs>%s</logs>
    <metrics>%s</metrics>
    <instructions>
    1. Assign severity: P1 (service down), P2 (degraded), P3 (warning), P4 (informational)
    2. Provide 2-3 root cause hypotheses ranked by likelihood with confidence %
    3. Provide concrete remediation steps with exact commands where applicable
    4. Identify which service logs corroborate/contradict each hypothesis
    </instructions>
    <output_format>
    Valid JSON: { severity, hypotheses[], remediationSteps[], confidence }
    </output_format>
    """.formatted(eventJson, logsText, metricsJson);
```

### Pattern 5: @Async Log Indexing

```java
@Async  // bounded ThreadPoolTaskExecutor: core=2, max=4, queue=1000
public void index(LogEntry entry) {
    // best-effort — drop when queue full, detection path is critical
}
```

## Anti-Patterns to Avoid

| Anti-Pattern | Why Bad | Correct Approach |
|---|---|---|
| Call Claude inside monitoring loop | 2-10s latency backs up loop; unbounded token costs | Emit AnomalyEvent → dedup → call Claude only for novel high-signal events |
| ContextBuilder inside AIAnalysisAgent | Violates SRP; hard to unit test; couples storage to AI | ContextBuilder assembles bundle first; agent receives fully-formed ContextBundle |
| Raw log storage in PostgreSQL | `LIKE '%ERROR%'` table scans on log volume → slow | ES for logs (time-range queries), PG for structured state only |
| Unstructured Claude prompts | Degrades reasoning quality (Anthropic guidance) | XML-tagged sections: background, data, instructions, output_format |
| Blocking Docker streams on platform thread | Starves other application work | Virtual threads (Java 21) or dedicated thread per container |
| Magic number thresholds | Untestable, unmaintainable | All thresholds in `application.yml` under `aiops:` prefix, bound to `@ConfigurationProperties` |

## Docker Compose Service Topology

```
Monitored Stack:
  nginx:8080      → reverse proxy, HTTP access logs, 5xx detection
  node-api:3000   → Node.js REST API, response time, error logs
  postgres:5432   → connection pool status via pg_stat_activity
  redis:6379      → cache hit/miss rates (from node-api logs)

Infrastructure Stack (same Compose file):
  elasticsearch:9200   → log index (daily rolling: aiops-logs-YYYY.MM.DD)
  postgres:5432        → alert state, metrics snapshots, AI analysis results
                          (same postgres, separate database: aiops_state)
```

The Spring Boot CLI process connects to `docker.sock` via volume mount and to ES/PG via exposed ports.

## Recommended Build Order

| Phase | Focus | Why First |
|-------|-------|-----------|
| 1 | Docker Compose + Spring Boot skeleton + Spring Shell | Validates connectivity before building analysis on top |
| 2 | Log streaming (DockerLogStreamer) + ES indexing | Data must flow before detection can work |
| 3 | Health polling + Metrics scraping + PG storage | Completes the data collection layer |
| 4 | Anomaly detection + Alert Manager + dedup | Detection engine — all previous phases feed this |
| 5 | Context Builder + AI Analysis Agent | Core value — requires working detection + storage |
| 6 | Interactive Shell + bilingual output | Wraps AI pipeline in user-friendly CLI |
| 7 | Simulation scripts + demo scenarios | Validates end-to-end against known failures |

## Architectural Decisions to Make

- **Spring AI vs direct RestClient for Claude:** Spring AI 1.0 GA status must be verified before implementation
- **ES index per service vs single index:** Per-container index (`aiops-logs-nginx-*`) vs unified (`aiops-logs-*`) — unified is simpler; per-container gives isolation
- **Context bundle token budget:** 80K chars default — measure actual log volume under load before hardcoding
- **Alert group persistence:** Group by time proximity only, or also by causal service graph? Start simple (time proximity)
