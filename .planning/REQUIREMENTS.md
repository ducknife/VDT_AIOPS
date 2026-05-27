# Requirements: VDT-AIOps

**Defined:** 2026-05-28
**Core Value:** Khi phát hiện sự kiện bất thường, hệ thống tự động thu thập đủ ngữ cảnh (log ±5 phút, metrics, log service liên quan) và đưa ra phân tích AI có ý nghĩa — không chỉ là alert đơn thuần mà là chẩn đoán có thể hành động ngay.

## v1 Requirements

### Data Collection

- [ ] **DC-01**: System thu thập log real-time từ 4 Docker containers (nginx, node-api, postgres, redis) qua docker-java API với followLogs=true
- [ ] **DC-02**: System thu thập container CPU, memory, và network stats từ Docker statsCmd mỗi 15 giây và lưu vào PostgreSQL
- [ ] **DC-03**: Log entries được index vào Elasticsearch async với noise pre-filtering (loại bỏ health check pings, static asset requests) trước khi lưu

### Anomaly Detection

- [ ] **AD-01**: System phát hiện khi một container dừng hoặc crash (trạng thái exited/stopped) và emit AnomalyEvent P1 trong vòng 30 giây

### Alert Management

- [ ] **AM-01**: System dedup alerts bằng SHA-256 fingerprint của (container, rule, severity) với TTL 5 phút — state-transition firing only (OK → ALERTING)
- [ ] **AM-02**: Mỗi alert được classify P1-P4 theo rules engine trước khi gọi AI: P1=service down, P2=degraded >20% errors, P3=intermittent <20%, P4=informational
- [ ] **AM-03**: Các alerts phát sinh trong cùng một time window (≤2 phút) từ liên quan services được nhóm thành một incident record
- [ ] **AM-04**: Tất cả alerts được lưu vào PostgreSQL với `opened_at`, `closed_at`, `status` và có thể query được qua CLI

### AI Agent

- [ ] **AI-01**: Khi một alert được tạo, system tự động build context bundle gồm: log window ±5 phút quanh sự kiện, metrics snapshot từ cùng thời điểm, và logs từ các services liên quan (giới hạn 200 lines/service)
- [ ] **AI-02**: System gọi Claude API với context bundle đã format bằng XML-tagged prompt, nhận và parse structured JSON response thành AnalysisResult
- [ ] **AI-03**: AI output bao gồm: severity (P1-P4), 2-3 root cause hypotheses có confidence % và evidence từ logs, concrete remediation steps với commands cụ thể khi áp dụng

### CLI Interface

- [ ] **CLI-01**: Monitoring mode chạy liên tục, hiển thị anomaly alerts và AI analysis lên console với ANSI color formatting (P1=đỏ, P2=vàng, P3=xanh lá, P4=xám)
- [ ] **CLI-02**: Interactive mode cho phép user nhập câu hỏi free-form ("Tại sao service X chậm?") và nhận AI analysis dựa trên logs + metrics hiện tại của service đó
- [ ] **CLI-03**: Status line được refresh mỗi 30 giây hiển thị: số stream đang active (4/4), ES write queue depth, số Claude calls trong giờ hiện tại, thời gian từ lần anomaly cuối

### Simulation Scripts

- [ ] **SIM-01**: Script `simulate-down.sh` dừng một hoặc nhiều containers để demo phát hiện P1 service down và AI analysis
- [ ] **SIM-02**: Script `simulate-load.sh` tạo HTTP load spike bằng wrk hoặc ab để demo latency + error rate tăng
- [ ] **SIM-03**: Script `simulate-db-exhaustion.sh` tạo nhiều concurrent connections vượt postgres max_connections để demo DB connection exhaustion
- [ ] **SIM-04**: Script `simulate-redis-oom.sh` fill Redis đến maxmemory limit để demo cache eviction và downstream effects

## v2 Requirements

### Data Collection (Deferred)

- **HLTH-01**: Health endpoint polling qua HTTP GET /health mỗi 15 giây cho từng service (UP/DOWN/DEGRADED detection)

### Anomaly Detection (Deferred)

- **AD-02**: Phát hiện HTTP 5xx error rate spike — tỷ lệ lỗi vượt mean + 2σ trong sliding window 60 giây
- **AD-03**: Phát hiện DB connection pool exhaustion từ postgres logs + node-api error patterns
- **AD-04**: Phát hiện high latency — response time P95 vượt ngưỡng cấu hình từ log parsing

### AI Enhancements (Deferred)

- **AI-04**: Token budget + cost controls: giới hạn Claude calls/giờ, log mỗi call vào PG, dùng haiku cho P3/P4
- **AI-05**: Bilingual output — hỗ trợ Vietnamese lẫn English qua `--lang` flag

### CLI Enhancements (Deferred)

- **CLI-04**: Alert recovery notifications — khi service khôi phục, hiển thị "RECOVERED" và close incident
- **CLI-05**: Query lịch sử incidents từ PostgreSQL qua CLI command

## Out of Scope

| Feature | Reason |
|---------|--------|
| Web dashboard / UI | Doubles scope, shifts focus khỏi AI/detection logic — CLI output đủ cho học tập |
| Email / SMS / Slack alerting | Webhook config, secrets management là một project riêng |
| Kubernetes / cloud deployment | Different APIs, auth, mental model — hoàn toàn khác domain |
| Multi-tenant / RBAC / auth | Friction trước khi AI value được demonstrate |
| Custom ML models (Weka, ONNX) | Training data pipelines = separate research project — Claude API đủ |
| Auto-remediation (auto-restart, auto-scale) | Nguy hiểm không có safeguards — output remediation suggestions, human executes |
| OpenTelemetry / OTLP pipeline | Separate observability discipline — dùng Docker API trực tiếp |
| Prometheus / Alertmanager integration | Production-grade complexity không cần thiết cho learning scope |
| Anthropic MCP spec chính thức | Custom context bundling đơn giản hơn và đủ cho mục tiêu học tập |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| DC-01 | TBD | Pending |
| DC-02 | TBD | Pending |
| DC-03 | TBD | Pending |
| AD-01 | TBD | Pending |
| AM-01 | TBD | Pending |
| AM-02 | TBD | Pending |
| AM-03 | TBD | Pending |
| AM-04 | TBD | Pending |
| AI-01 | TBD | Pending |
| AI-02 | TBD | Pending |
| AI-03 | TBD | Pending |
| CLI-01 | TBD | Pending |
| CLI-02 | TBD | Pending |
| CLI-03 | TBD | Pending |
| SIM-01 | TBD | Pending |
| SIM-02 | TBD | Pending |
| SIM-03 | TBD | Pending |
| SIM-04 | TBD | Pending |

**Coverage:**
- v1 requirements: 18 total
- Mapped to phases: 0 (TBD — updated by roadmapper)
- Unmapped: 18 ⚠️ (will be resolved by roadmap creation)

---
*Requirements defined: 2026-05-28*
*Last updated: 2026-05-28 after initial definition*
