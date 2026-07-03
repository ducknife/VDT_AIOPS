# Duckompose · Infrastructure

Everything needed to **run** Duckompose locally: the simulated microservice topology that gets
monitored, and the platform services the engine depends on — all as Docker Compose.

> Part of [Duckompose](../../README.md). See also the [engine](../app/README.md) and [TUI](../tui/README.md).

---

## Two Compose projects

| File | Project | What it runs |
|------|---------|--------------|
| [`docker-compose.yml`](docker-compose.yml) | **`aiops-sim`** | The **monitored** topology — the system Duckompose watches |
| [`docker-compose.backend.yml`](docker-compose.backend.yml) | **`aiops-platform`** | The **platform** — engine, Prometheus, state DB, Docker proxy |

They are split on purpose: the engine (platform) monitors the sim as an *external* system, exactly
as a real AIOps tool would. The sim creates the shared Docker network `aiops-shared-network`; the
platform joins it — so **start the sim first**.

---

## The simulated topology (`aiops-sim`)

```
traffic-gen ─▶ nginx ─▶ node-api (users / orders / payments / inventory) ─▶ postgres + redis
```

- **nginx** — reverse proxy in front of the node-api instances ([`services/nginx/nginx.conf`](services/nginx/nginx.conf)).
- **node-api** ×4 — a shared Node.js scaffold ([`services/node-api/`](services/node-api/)); each instance mounts a
  different business domain via the `SERVICE_DOMAIN` env (`users`, `orders`, `payments`, `inventory`),
  and exposes `/metrics`, `/health`, and fault-injection endpoints.
  - `node-api` (users) + `node-api-orders` **share** one `postgres` + `redis` → a shared blast radius.
  - `node-api-payments` and `node-api-inventory` each get their **own** datastores.
- **postgres** / **redis** — datastores, seeded by [`services/postgres/init.sql`](services/postgres/init.sql).
- **exporters** — one `postgres-exporter` / `redis-exporter` / `nginx-exporter` per datastore, scraped by Prometheus.
- **traffic-gen** — drives continuous load so there are real logs & metrics to observe ([`services/traffic-gen/traffic.sh`](services/traffic-gen/traffic.sh)).

Topology edges are derived by the engine from each container's `com.docker.compose.depends_on` label.

## The platform (`aiops-platform`)

- **aiops-engine** — the Java Spring Boot brain (built from [`../app`](../app)).
- **prometheus** — scrapes the sim per [`prometheus.yml`](prometheus.yml) (per-target `service` labels).
- **postgres-aiops** — the engine's own state DB (alerts, incidents, logs), on port `5433`.
- **docker-proxy** — a Socat proxy exposing the Docker socket over TCP (`tcp://docker-proxy:2375`), so the
  engine talks to the Docker Engine without enabling the insecure `2375` toggle on Docker Desktop.

---

## Running

**Prerequisites:** Docker Desktop, and an `.env` in this folder:
```env
ANTHROPIC_API_KEY=sk-ant-...
AIOPS_DB_PASSWORD=change_me
APP_DB_PASSWORD=change_me
```

```bash
# 1) Sim FIRST (creates the shared network)
docker compose -f docker-compose.yml up -d

# 2) Platform (engine + prometheus + state DB + proxy)
docker compose -p aiops-platform -f docker-compose.backend.yml up -d --build
```

Then launch the [TUI](../tui/README.md) (`duckompose`). Ports: engine `8088`, Prometheus `9090`, state DB `5433`.

> 💡 Low on RAM? Bring up only one stack (e.g. `nginx node-api node-api-orders postgres redis` + exporters
> + `prometheus`) instead of the full topology.

---

## Simulating incidents

Run from [`scripts/`](scripts/) (PowerShell). Each injects a **known** fault — the ground truth to check
the agent's diagnosis against.

| Script | Fault | Typical severity |
|--------|-------|------------------|
| `simulate-container-down.ps1 -Service <name>` | stop a container → `SERVICE_DOWN` | P1 |
| `simulate-high-latency.ps1 -Service <name>` | inject latency on a node-api → `HIGH_LATENCY` | P3 |
| `simulate-redis-oom.ps1` | exhaust Redis memory → `REDIS_OOM` | P2 |
| `simulate-db-exhaustion.ps1 -Service <name>` | drain the DB connection pool → `DB_EXHAUSTION` | P2 |
| `simulate-load-spike.ps1 -Service <name>` | error/load spike → `ERROR_RATE_SPIKE` | P3 |
| `simulate-minor-latency.ps1` | mild latency (sub-threshold noise) | — |
| `run-all-simulations.ps1` | run the full scenario suite | — |
| `recover.ps1 <scenario>` | heal — restart/undo the injected fault | — |

`test-stack.sh` is a quick smoke check that the stack is up and reachable.
