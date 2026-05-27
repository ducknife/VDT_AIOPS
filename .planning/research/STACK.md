# Stack Research — VDT-AIOps

**Domain:** AIOps CLI monitoring tool (Java Spring Boot + Claude + ES + PG)
**Researched:** 2026-05-28
**Overall Confidence:** MEDIUM-HIGH (training knowledge; open questions noted)

---

## Recommended Stack

### Core Framework

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Java | 21 LTS | Runtime | Virtual threads (Project Loom) — one virtual thread per Docker log stream costs kilobytes, not megabytes. Spring Boot 3.2+ requires Java 17+; 21 is the correct LTS baseline for new projects. |
| Spring Boot | 3.2.x | Application container | Auto-configuration, DI, `@Scheduled`, `@Async`. Use `spring-boot-starter` (NOT `spring-boot-starter-web`) — no embedded Tomcat. |

**CRITICAL config (`application.properties`):**
```properties
spring.main.web-application-type=none
spring.threads.virtual.enabled=true
```

**Confidence:** HIGH

---

### CLI Framework

**Recommendation: Spring Shell 3.2.x**

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Spring Shell | 3.2.x | Interactive REPL + command dispatch | Native Spring integration. Provides persistent REPL session, tab-completion, help generation, ANSI output. Commands are `@ShellComponent` beans — your `@Service` classes inject directly. |

**Why NOT Picocli:** Picocli is a one-shot command framework (no REPL). The interactive mode ("why is service X slow?") requires a persistent session — Spring Shell provides this natively.

```xml
<dependency>
    <groupId>org.springframework.shell</groupId>
    <artifactId>spring-shell-starter</artifactId>
    <version>3.2.x</version> <!-- verify latest patch on Maven Central -->
</dependency>
```

**Confidence:** HIGH for Spring Shell being correct. MEDIUM on exact patch version.

---

### Docker Integration

**Recommendation: docker-java 3.3.x + httpclient5 transport**

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| docker-java | 3.3.x | Docker Engine API — log streaming, container stats, exec | De-facto standard Java Docker client. Full Docker Remote API support: log streaming (`logContainerCmd`), real-time stats (`statsCmd`), container inspection. Testcontainers uses it internally. |
| docker-java-transport-httpclient5 | 3.3.x | HTTP transport | Recommended transport for docker-java 3.3+, replacing deprecated OkHttp transport. |

**Key API patterns:**
```java
// Log streaming (blocking — run on virtual thread)
dockerClient.logContainerCmd(containerId)
    .withStdOut(true).withStdErr(true)
    .withFollowStream(true)
    .withSince((int)(System.currentTimeMillis() / 1000))
    .exec(new ResultCallback.Adapter<Frame>() {
        @Override
        public void onNext(Frame frame) {
            // non-blocking: publish Spring event and return immediately
            eventPublisher.publishEvent(new RawLogEvent(containerId, frame));
        }
    })
    .awaitCompletion(); // blocks virtual thread, cheap

// Container stats (CPU, memory, network)
dockerClient.statsCmd(containerId).exec(statsCallback);

// Container listing
dockerClient.listContainersCmd()
    .withNameFilter(List.of("nginx", "node-api", "postgres", "redis"))
    .exec();
```

**Virtual threads per container:**
```java
for (String containerId : monitoredContainers) {
    Thread.ofVirtual()
        .name("log-stream-" + containerName)
        .start(() -> streamLogsBlocking(containerId));
}
```

```xml
<dependency>
    <groupId>com.github.docker-java</groupId>
    <artifactId>docker-java</artifactId>
    <version>3.3.3</version>
</dependency>
<dependency>
    <groupId>com.github.docker-java</groupId>
    <artifactId>docker-java-transport-httpclient5</artifactId>
    <version>3.3.3</version>
</dependency>
```

**Confidence:** HIGH for docker-java being correct. MEDIUM on exact patch (verify at github.com/docker-java/docker-java/releases).

---

### Storage Clients

#### Elasticsearch — Official Java Client 8.x (NOT Spring Data Elasticsearch)

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| elasticsearch-java | 8.13.x | Log indexing + time-range querying | Official Elastic-maintained client. Type-safe fluent builder API. Replaces deprecated HLRC. |

**Why NOT Spring Data Elasticsearch (SDE):**
- SDE adds ORM abstraction designed for entity-mapped documents. Log events are not entities — they're semi-structured, high-volume, time-series records.
- SDE's version alignment with ES 8.x is historically brittle.
- Direct control over index mappings and query DSL is needed for ±5-minute log window queries.

```java
// Index a log event
client.index(i -> i
    .index("aiops-logs-" + containerName)
    .document(logEvent));

// Query ±5 min around anomaly timestamp
client.search(s -> s
    .index("aiops-logs-*")
    .query(q -> q.bool(b -> b
        .must(m -> m.range(r -> r
            .field("timestamp")
            .gte(JsonData.of(windowStart.toEpochMilli()))
            .lte(JsonData.of(windowEnd.toEpochMilli()))))
        .filter(f -> f.term(t -> t
            .field("container").value(containerName))))),
    LogEvent.class);
```

```xml
<dependency>
    <groupId>co.elastic.clients</groupId>
    <artifactId>elasticsearch-java</artifactId>
    <version>8.13.0</version> <!-- must match ES server major.minor -->
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.17.0</version>
</dependency>
```

**Version rule:** ES Java Client major.minor MUST match ES server major.minor. Pin both in `docker-compose.yml` and `pom.xml`.

**Confidence:** HIGH for official client over SDE. HIGH for version matching rule.

---

#### PostgreSQL — Spring Data JPA + Flyway

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| spring-boot-starter-data-jpa | 3.2.x | ORM for structured state | JPA is correct — alert entities, incidents, and AI analysis results are structured domain objects with clear schemas. |
| postgresql JDBC driver | 42.7.x | JDBC driver | Official PostgreSQL JDBC driver. |
| Flyway | 9.x / 10.x | Schema migrations | Reproducible migrations at startup — critical when PG container is recreated. |

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.7.2</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
```

**Confidence:** HIGH — standard Spring Boot + PostgreSQL stack.

---

### AI Integration

**Primary: Spring AI 1.0.x (Anthropic provider)**

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| spring-ai-anthropic-spring-boot-starter | 1.0.x | Claude API integration | Spring AI provides `ChatClient` abstraction with first-class Anthropic support. Handles API key injection, request marshaling, retries, and Spring Boot auto-configuration. |

```properties
# Set ANTHROPIC_API_KEY env var; Spring AI reads it via spring.ai.anthropic.api-key
spring.ai.anthropic.chat.options.model=claude-sonnet-4-6
spring.ai.anthropic.chat.options.max-tokens=4096
```

```java
@Service
public class AiAnalysisAgent {
    @Autowired ChatClient chatClient;

    public AnalysisResult analyze(ContextBundle bundle) {
        String response = chatClient.prompt()
            .system("AIOps expert analyzing distributed system incidents.")
            .user(bundle.toPromptString())
            .call()
            .content();
        return parseAnalysis(response);
    }
}
```

**MUST VALIDATE** Spring AI 1.0 GA status at `https://spring.io/projects/spring-ai` before implementation.

**Fallback: Direct RestClient (if Spring AI not GA)**

```java
@Service
public class AnthropicRestClient {
    private final RestClient restClient;

    public AnthropicRestClient(@Value("${anthropic.api-key}") String apiKey) {
        this.restClient = RestClient.builder()
            .baseUrl("https://api.anthropic.com")
            .defaultHeader("x-api-key", apiKey)
            .defaultHeader("anthropic-version", "2023-06-01")
            .defaultHeader("content-type", "application/json")
            .build();
    }

    public String callClaude(String prompt) {
        // POST /v1/messages — stable, well-documented
    }
}
```

**Note:** As of August 2025 training data, Anthropic had no official first-party Java SDK. Verify at `https://docs.anthropic.com/en/api/client-sdks` — if one exists now, evaluate it.

**Confidence:** MEDIUM-HIGH for Spring AI. HIGH for RestClient fallback.

---

### Metrics Scraping

No additional library needed. Use what's already in the stack:

| Source | Method |
|--------|--------|
| Container CPU/memory/network | docker-java `statsCmd` |
| Service health endpoints (`/health`) | `RestClient` GET |
| nginx `stub_status` | `RestClient` GET + regex parsing |
| Prometheus `/metrics` (if added) | `RestClient` GET + line parsing |

```java
@Scheduled(fixedRate = 15_000)
public void pollHealthEndpoints() {
    for (String serviceUrl : serviceHealthUrls) {
        try {
            String response = restClient.get().uri(serviceUrl).retrieve().body(String.class);
            metricsStore.record(serviceUrl, response);
        } catch (Exception e) {
            anomalyDetector.reportUnreachable(serviceUrl);
        }
    }
}
```

**Confidence:** HIGH.

---

### Anomaly Detection

**Recommendation: Apache Commons Math 3.6.1 + custom rolling window**

| Technology | Version | Purpose |
|------------|---------|---------|
| Apache Commons Math | 3.6.1 | `DescriptiveStatistics` — mean, std dev, percentiles for z-score detection |

```java
// Z-score: flag if > 3 std deviations from rolling mean
DescriptiveStatistics window = new DescriptiveStatistics(windowSize);
window.addValue(currentErrorRate);
double zScore = (currentErrorRate - window.getMean()) / window.getStandardDeviation();
if (zScore > 3.0) {
    anomalyDetector.emit(AnomalyEvent.ERROR_RATE_SPIKE);
}

// Absolute threshold: latency or service down
if (latencyMs > 5000 || httpStatus >= 500) {
    anomalyDetector.emit(AnomalyEvent.SERVICE_UNHEALTHY);
}
```

**Why NOT ML libraries (Weka, Deeplearning4j):** Require training data. Heavy dependencies. Statistical methods (z-score, thresholds) are interpretable and fully correct for the demo scenarios (kill container, load spike, DB connection errors).

```xml
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-math3</artifactId>
    <version>3.6.1</version>
</dependency>
```

**Confidence:** HIGH.

---

## What NOT to Use

| Avoid | Reason |
|-------|--------|
| `spring-boot-starter-web` | Starts embedded Tomcat. Use `spring.main.web-application-type=none` instead |
| Spring Data Elasticsearch | ORM abstraction wrong for log storage; version alignment with ES 8.x fragile |
| High Level REST Client (HLRC) | Deprecated in ES 8.x, removed in ES 9.x |
| Picocli (standalone) | No REPL loop — wrong for interactive monitoring agent |
| Raw `Scanner(System.in)` loop | No history, tab completion, or help system |
| `RestTemplate` | Maintenance mode in Spring 6+. Use `RestClient` |
| WebFlux / Project Reactor | Overkill — one AI call per anomaly event. Virtual threads handle blocking cheaply |
| Weka / Deeplearning4j / ONNX | ML frameworks requiring training data. Wrong scope |
| Testcontainers | Testing abstraction wrapping docker-java — not for production monitoring |
| Quartz Scheduler | Heavyweight. Spring `@Scheduled` is sufficient |

---

## Open Questions (Require Validation Before Implementation)

1. **Spring AI 1.0 GA status** — validate at `https://spring.io/projects/spring-ai`
2. **Official Anthropic Java SDK** — check `https://docs.anthropic.com/en/api/client-sdks`
3. **docker-java exact version** — verify at `https://github.com/docker-java/docker-java/releases`; confirm httpclient5 is still recommended transport
4. **Spring Shell REPL + background stdout** — validate that Spring Shell interactive REPL doesn't conflict with background threads printing to stdout. May need console multiplexer or suppress background output during input.
5. **ES version pinning** — fix ES server version in `docker-compose.yml` BEFORE writing client code; match major.minor exactly
6. **Commons Math 4 GA** — check `https://commons.apache.org/proper/commons-math/`; if stable, evaluate migration from 3.6.1
7. **Virtual threads + `@Scheduled`** — confirm with Spring Boot 3.2 + `spring.threads.virtual.enabled=true` whether `@Scheduled` runs on virtual threads automatically

---

## Version Summary

| Library | Recommended Version | Confidence |
|---------|--------------------|-----------| 
| Java | 21 LTS | HIGH |
| Spring Boot | 3.2.x | HIGH |
| Spring Shell | 3.2.x | HIGH (patch: MEDIUM) |
| docker-java | 3.3.x | HIGH (patch: MEDIUM) |
| docker-java-transport-httpclient5 | 3.3.x | MEDIUM — verify transport recommendation |
| elasticsearch-java (official) | 8.13.x (match ES server) | HIGH |
| spring-boot-starter-data-jpa | 3.2.x | HIGH |
| postgresql JDBC | 42.7.x | HIGH |
| Flyway | 9.x / 10.x | HIGH |
| Spring AI (Anthropic) | 1.0.x | MEDIUM — validate GA status first |
| RestClient (Claude fallback) | built-in Spring Boot 3.2 | HIGH |
| Apache Commons Math | 3.6.1 | HIGH |

---

## Roadmap Implications

- **Phase 1:** Spring Boot 3.2 skeleton + Spring Shell + docker-java connectivity — validates most novel components early
- **Phase 2:** ES + PG storage with official clients. Flyway migrations from the start
- **Phase 3:** AI integration — validate Spring AI 1.0 availability at this point; fall back to RestClient if needed
- **Phase 4+:** Anomaly detection + context bundling require working log streaming (Phase 1) and storage (Phase 2)
