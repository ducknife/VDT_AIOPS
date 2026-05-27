<!-- GSD:project-start source:PROJECT.md -->

## Project

**VDT-AIOps**

Hệ thống AIOps (AI for IT Operations) dùng AI Agent để giải quyết "alert fatigue" trong vận hành phần mềm. CLI tool giám sát thời gian thực các Docker container, tự động phát hiện bất thường (error rate tăng đột biến, latency cao, service không phản hồi), xây dựng "context bundle" phong phú xung quanh sự kiện, rồi gọi Claude (Anthropic) để phân tích severity, root cause, và đề xuất remediation. Dự án học tập nhằm hiểu sâu AIOps patterns, context-driven AI analysis, và tích hợp LLM vào observability workflows.

**Core Value:** Khi một sự kiện bất thường xảy ra, hệ thống phải tự động thu thập đủ ngữ cảnh (log ±5 phút, metrics cùng thời điểm, log service liên quan) và đưa ra phân tích AI có ý nghĩa — không chỉ là alert đơn thuần mà là chẩn đoán có thể hành động ngay.

### Constraints

- **Tech Stack**: Java Spring Boot — backend chính; phù hợp với enterprise AIOps tools thực tế
- **AI Provider**: Claude (Anthropic) — cần ANTHROPIC_API_KEY trong environment
- **Infrastructure**: Docker Compose only, no cloud — môi trường phát triển local
- **Storage**: PostgreSQL + Elasticsearch — cả hai chạy trong Docker Compose của project
- **Scope**: Milestone 1 = mini project CLI Agent — không mở rộng sang web UI hay production deployment

<!-- GSD:project-end -->

<!-- GSD:stack-start source:research/STACK.md -->

## Technology Stack

## Recommended Stack

### Core Framework

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Java | 21 LTS | Runtime | Virtual threads (Project Loom) — one virtual thread per Docker log stream costs kilobytes, not megabytes. Spring Boot 3.2+ requires Java 17+; 21 is the correct LTS baseline for new projects. |
| Spring Boot | 3.2.x | Application container | Auto-configuration, DI, `@Scheduled`, `@Async`. Use `spring-boot-starter` (NOT `spring-boot-starter-web`) — no embedded Tomcat. |

### CLI Framework

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Spring Shell | 3.2.x | Interactive REPL + command dispatch | Native Spring integration. Provides persistent REPL session, tab-completion, help generation, ANSI output. Commands are `@ShellComponent` beans — your `@Service` classes inject directly. |

### Docker Integration

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| docker-java | 3.3.x | Docker Engine API — log streaming, container stats, exec | De-facto standard Java Docker client. Full Docker Remote API support: log streaming (`logContainerCmd`), real-time stats (`statsCmd`), container inspection. Testcontainers uses it internally. |
| docker-java-transport-httpclient5 | 3.3.x | HTTP transport | Recommended transport for docker-java 3.3+, replacing deprecated OkHttp transport. |

### Storage Clients

#### Elasticsearch — Official Java Client 8.x (NOT Spring Data Elasticsearch)

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| elasticsearch-java | 8.13.x | Log indexing + time-range querying | Official Elastic-maintained client. Type-safe fluent builder API. Replaces deprecated HLRC. |

- SDE adds ORM abstraction designed for entity-mapped documents. Log events are not entities — they're semi-structured, high-volume, time-series records.
- SDE's version alignment with ES 8.x is historically brittle.
- Direct control over index mappings and query DSL is needed for ±5-minute log window queries.

#### PostgreSQL — Spring Data JPA + Flyway

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| spring-boot-starter-data-jpa | 3.2.x | ORM for structured state | JPA is correct — alert entities, incidents, and AI analysis results are structured domain objects with clear schemas. |
| postgresql JDBC driver | 42.7.x | JDBC driver | Official PostgreSQL JDBC driver. |
| Flyway | 9.x / 10.x | Schema migrations | Reproducible migrations at startup — critical when PG container is recreated. |

### AI Integration

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| spring-ai-anthropic-spring-boot-starter | 1.0.x | Claude API integration | Spring AI provides `ChatClient` abstraction with first-class Anthropic support. Handles API key injection, request marshaling, retries, and Spring Boot auto-configuration. |

# Set ANTHROPIC_API_KEY env var; Spring AI reads it via spring.ai.anthropic.api-key

### Metrics Scraping

| Source | Method |
|--------|--------|
| Container CPU/memory/network | docker-java `statsCmd` |
| Service health endpoints (`/health`) | `RestClient` GET |
| nginx `stub_status` | `RestClient` GET + regex parsing |
| Prometheus `/metrics` (if added) | `RestClient` GET + line parsing |

### Anomaly Detection

| Technology | Version | Purpose |
|------------|---------|---------|
| Apache Commons Math | 3.6.1 | `DescriptiveStatistics` — mean, std dev, percentiles for z-score detection |

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

## Open Questions (Require Validation Before Implementation)

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

## Roadmap Implications

- **Phase 1:** Spring Boot 3.2 skeleton + Spring Shell + docker-java connectivity — validates most novel components early
- **Phase 2:** ES + PG storage with official clients. Flyway migrations from the start
- **Phase 3:** AI integration — validate Spring AI 1.0 availability at this point; fall back to RestClient if needed
- **Phase 4+:** Anomaly detection + context bundling require working log streaming (Phase 1) and storage (Phase 2)

<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->

## Conventions

Conventions not yet established. Will populate as patterns emerge during development.
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->

## Architecture

Architecture not yet mapped. Follow existing patterns found in the codebase.
<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:skills/ -->

## Project Skills

No project skills found. Add skills to any of: `.claude/skills/`, `.agents/skills/`, `.cursor/skills/`, `.github/skills/`, or `.codex/skills/` with a `SKILL.md` index file.
<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->

## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:

- `/gsd-quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd-debug` for investigation and bug fixing
- `/gsd-execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->

<!-- GSD:profile-start -->

## Developer Profile

> Profile not yet configured. Run `/gsd-profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
