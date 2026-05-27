# Project State — VDT-AIOps

## Current Status

Phase: Not started
Active: —
Completed: —

Progress: [..........] 0% — 0/6 phases complete

---

## Project Reference

See: `.planning/PROJECT.md`
Core value: When an anomaly occurs, the system automatically collects full context (logs ±5 min, metrics, related service logs) and delivers an actionable AI diagnosis — not just an alert.
Next action: `/gsd-plan-phase 1`

---

## Phase Summary

| # | Phase | Status | Requirements |
|---|-------|--------|--------------|
| 1 | Infrastructure Skeleton | Pending | (foundation — no v1 req IDs) |
| 2 | Data Collection | Pending | DC-01, DC-02, DC-03 |
| 3 | Detection and Alert Management | Pending | AD-01, AM-01, AM-02, AM-03, AM-04 |
| 4 | AI Analysis Agent | Pending | AI-01, AI-02, AI-03 |
| 5 | CLI Interface | Pending | CLI-01, CLI-02, CLI-03 |
| 6 | Simulation and Validation | Pending | SIM-01, SIM-02, SIM-03, SIM-04 |

---

## Performance Metrics

| Metric | Value |
|--------|-------|
| Phases total | 6 |
| Phases complete | 0 |
| Requirements mapped | 18/18 |
| Plans written | 0 |
| Plans complete | 0 |

---

## Accumulated Context

### Key Decisions Locked In
- Spring Boot 3.2 / Java 21 with virtual threads for log streaming (one thread per container)
- Spring Shell 3.2 for interactive REPL (NOT Picocli — no persistent session)
- docker-java 3.3.x + httpclient5 transport for Docker Engine API
- elasticsearch-java 8.13.x (official client — must match ES server major.minor exactly)
- Spring Data JPA + Flyway for PostgreSQL (Flyway from day one)
- Spring AI (Anthropic) preferred; fall back to RestClient if Spring AI is still RC at Phase 4
- Apache Commons Math 3.6.1 for z-score / sliding window anomaly detection
- Custom context bundling pattern (NOT Anthropic MCP spec)
- ES index template with `"dynamic": false` required before first write (Phase 1)
- Claude queue: single-threaded, RateLimiter 3/min, retry on 429 (Phase 4)

### Open Questions (resolve before or during planning)
- Spring AI 1.0 GA status — verify at https://spring.io/projects/spring-ai
- Official Anthropic Java SDK availability — check https://docs.anthropic.com/en/api/client-sdks
- docker-java exact patch version — verify 3.3.x release at https://github.com/docker-java/docker-java/releases
- ES server version to pin in docker-compose.yml (client must match exactly)
- Commons Math 4 GA status — check https://commons.apache.org/proper/commons-math/
- Spring Shell REPL + background stdout conflict resolution strategy
- Actual log volume per 5-min window under simulated load (validate 200-line cap)

### Architecture Notes
- All cross-concern communication via `ApplicationEventPublisher` — never direct method calls across boundaries
- Log indexing is `@Async` with bounded executor — ES writes must never block the detection loop
- Claude call must never hold a DB connection (HikariCP pool exhaustion risk)
- Context bundle hard cap: ~80,000 chars; include `context_stats` and pre-compute signal summary
- Alert dedup: SHA-256 fingerprint of (container, rule, severity); 5-min TTL; state-transition firing only (OK → ALERTING)

### Blockers
None currently.

### Todos
- Verify Spring AI GA before starting Phase 4 planning
- Pin ES server version in docker-compose.yml at Phase 1
- Measure actual log volume per window under load before hardcoding line caps

---

## Session Continuity

Last session: 2026-05-28 — roadmap created, files initialized
Current task: Roadmap complete, ready for Phase 1 planning
Resume with: `/gsd-plan-phase 1`

---
*State initialized: 2026-05-28*
*Last updated: 2026-05-28 after roadmap creation*
