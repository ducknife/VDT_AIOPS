# VDT-AIOps

## What This Is

Hệ thống AIOps (AI for IT Operations) dùng AI Agent để giải quyết "alert fatigue" trong vận hành phần mềm. CLI tool giám sát thời gian thực các Docker container, tự động phát hiện bất thường (error rate tăng đột biến, latency cao, service không phản hồi), xây dựng "context bundle" phong phú xung quanh sự kiện, rồi gọi Claude (Anthropic) để phân tích severity, root cause, và đề xuất remediation. Dự án học tập nhằm hiểu sâu AIOps patterns, context-driven AI analysis, và tích hợp LLM vào observability workflows.

## Core Value

Khi một sự kiện bất thường xảy ra, hệ thống phải tự động thu thập đủ ngữ cảnh (log ±5 phút, metrics cùng thời điểm, log service liên quan) và đưa ra phân tích AI có ý nghĩa — không chỉ là alert đơn thuần mà là chẩn đoán có thể hành động ngay.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] Thu thập log real-time từ 4 Docker containers qua Docker API (nginx, node-api, postgres, redis)
- [ ] Phát hiện bất thường: error rate tăng đột biến, service không phản hồi, latency cao
- [ ] Context Builder: khi phát hiện sự kiện → tự động thu thập log ±5 phút + metrics cùng thời điểm + log service liên quan
- [ ] AI Agent gọi Claude API với context bundle → trả về severity (P1-P4), root cause hypotheses, remediation steps
- [ ] Interactive mode: người dùng hỏi "Tại sao service X chậm?" → Agent phân tích và trả lời bằng tiếng Việt/Anh
- [ ] Alert Manager: phân loại, nhóm alert liên quan, tránh duplicate notifications
- [ ] Lưu log vào Elasticsearch, state/alerts vào PostgreSQL
- [ ] Bộ simulation scripts: kill container, tăng load, lỗi database connection để demo
- [ ] Metrics scraping: CPU, memory, request rate, error rate từ containers (health endpoints hoặc Prometheus)

### Out of Scope

- AWS hoặc cloud deployment — chạy local Docker Compose hoàn toàn
- Web dashboard / UI — CLI only, không cần frontend
- Anthropic MCP spec chính thức — dùng custom context bundling pattern thay thế
- Multi-tenant / production hardening — đây là learning project, không cần auth/RBAC
- Alerting qua email/SMS/Slack — CLI output và log storage đủ cho scope này

## Context

**Môi trường mô phỏng:** 4-service Docker Compose stack — nginx (reverse proxy), node-api (Node.js REST API), postgres, redis. Đây là stack web phổ biến, dễ tạo các loại lỗi đặc trưng: HTTP 5xx từ nginx, connection pool exhaustion từ postgres, cache miss từ redis.

**AI Integration:** Claude Anthropic API — context window lớn phù hợp cho log analysis, khả năng reasoning về distributed system failures tốt.

**Log Storage strategy:** Elasticsearch để search/filter log nhanh, PostgreSQL để lưu alert state, incidents, và AI analysis results. Cả hai chạy trong Docker Compose.

**Pattern chính (Custom Context Bundling):** Không dùng Anthropic MCP spec. Thay vào đó, khi phát hiện "Event of Interest", Context Builder tự thu thập và serialize thành một JSON bundle: `{event, logs_window, metrics_snapshot, related_service_logs}` rồi gửi cho AI Agent.

**Học tập focus:** Hiểu cách xây dựng AI Agent có ngữ cảnh phong phú, anomaly detection patterns, và integration giữa observability data + LLM reasoning.

## Constraints

- **Tech Stack**: Java Spring Boot — backend chính; phù hợp với enterprise AIOps tools thực tế
- **AI Provider**: Claude (Anthropic) — cần ANTHROPIC_API_KEY trong environment
- **Infrastructure**: Docker Compose only, no cloud — môi trường phát triển local
- **Storage**: PostgreSQL + Elasticsearch — cả hai chạy trong Docker Compose của project
- **Scope**: Milestone 1 = mini project CLI Agent — không mở rộng sang web UI hay production deployment

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Java Spring Boot thay vì Node.js/TypeScript | Phù hợp hơn với enterprise AIOps context, phong phú ecosystem cho Docker API, Elasticsearch | — Pending |
| Custom context bundling thay vì Anthropic MCP spec | Đơn giản hơn, dễ học, không phụ thuộc vào MCP tooling bên ngoài | — Pending |
| PostgreSQL + Elasticsearch (2 storage) | ES cho full-text log search/analytics, PG cho structured state — phản ánh real-world AIOps stack | — Pending |
| Claude Anthropic API | Context window lớn tốt hơn cho log analysis, phù hợp với platform này | — Pending |
| CLI-only (không có web UI) | Giữ scope gọn, focus vào AI/detection logic hơn là UI | — Pending |

---

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-05-28 after initialization*
