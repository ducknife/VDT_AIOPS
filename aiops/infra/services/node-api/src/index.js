const express = require('express');
const { Pool }  = require('pg');
const { createClient } = require('redis');

const app  = express();
const PORT = process.env.PORT || 3000;

app.use(express.json());

// ─── PostgreSQL ───────────────────────────────────────────────────────────────
// max: 10  →  simulation DB exhaustion khi ta mở >10 connections đồng thời
const pgPool = new Pool({
  connectionString:    process.env.DATABASE_URL,
  max:                 10,
  idleTimeoutMillis:   30_000,
  connectionTimeoutMillis: 2_000,
});

pgPool.on('error', (err) =>
  console.error('[POSTGRES ERROR]', err.message)
);

// ─── Redis ────────────────────────────────────────────────────────────────────
const redisClient = createClient({ url: process.env.REDIS_URL });
redisClient.on('error', (err) => console.error('[REDIS ERROR]', err.message));
redisClient.connect();

// ─── Metrics counters (cho Prometheus scrape) ────────────────────────────────
const metrics = { requests: 0, errors: 0, totalLatencyMs: 0 };

// ─── Request logger middleware ────────────────────────────────────────────────
app.use((req, res, next) => {
  const start = Date.now();
  res.on('finish', () => {
    const ms = Date.now() - start;
    metrics.requests++;
    metrics.totalLatencyMs += ms;
    if (res.statusCode >= 500) metrics.errors++;
    const level = res.statusCode >= 500 ? '[ERROR]'
                : res.statusCode >= 400 ? '[WARN]'
                : '[INFO]';
    console.log(`${level} ${req.method} ${req.path} → ${res.statusCode} (${ms}ms)`);
  });
  next();
});

// ─── Endpoints ────────────────────────────────────────────────────────────────

// Prometheus metrics — AIOps scrape endpoint này mỗi 15 giây
app.get('/metrics', (_req, res) => {
  const avgLatency = metrics.requests > 0
    ? (metrics.totalLatencyMs / metrics.requests).toFixed(2)
    : 0;
  res.set('Content-Type', 'text/plain; version=0.0.4');
  res.send([
    `http_requests_total ${metrics.requests}`,
    `http_errors_total ${metrics.errors}`,
    `http_latency_avg_ms ${avgLatency}`,
  ].join('\n'));
});

// Health check — AIOps filter endpoint này như "noise" (không index vào ES)
app.get('/health', (_req, res) => {
  res.json({ status: 'healthy', timestamp: new Date().toISOString() });
});

// Lấy danh sách users: cache-aside pattern (Redis → PostgreSQL)
app.get('/api/users', async (_req, res) => {
  try {
    const cacheKey = 'users:all';
    const cached   = await redisClient.get(cacheKey);
    if (cached) {
      return res.json({ source: 'cache', data: JSON.parse(cached) });
    }
    const { rows } = await pgPool.query(
      'SELECT id, name, email, created_at FROM users ORDER BY created_at DESC LIMIT 20'
    );
    await redisClient.setEx(cacheKey, 30, JSON.stringify(rows)); // cache 30s
    res.json({ source: 'db', data: rows });
  } catch (err) {
    console.error('[ERROR] GET /api/users —', err.message);
    res.status(500).json({ error: 'Database query failed' });
  }
});

// Tạo user mới; xóa cache sau khi write
app.post('/api/users', async (req, res) => {
  const { name, email } = req.body;
  if (!name || !email) {
    return res.status(400).json({ error: 'name and email are required' });
  }
  try {
    const { rows } = await pgPool.query(
      'INSERT INTO users(name, email) VALUES($1, $2) RETURNING *',
      [name, email]
    );
    await redisClient.del('users:all');
    res.status(201).json({ data: rows[0] });
  } catch (err) {
    console.error('[ERROR] POST /api/users —', err.message);
    res.status(500).json({ error: 'Failed to create user' });
  }
});

// Tạo CPU load nhẹ — dùng bởi simulate-load.sh để spike latency
app.get('/api/compute', (req, res) => {
  const n = parseInt(req.query.n || '500000', 10);
  let sum = 0;
  for (let i = 0; i < n; i++) sum += Math.random();
  res.json({ result: sum, iterations: n });
});

// Endpoint trả lỗi ngẫu nhiên — dùng để simulate error rate spike
app.get('/api/flaky', (req, res) => {
  const rate = parseFloat(req.query.rate || '0.3'); // 30% lỗi mặc định
  if (Math.random() < rate) {
    console.error('[ERROR] /api/flaky — simulated failure triggered');
    return res.status(500).json({ error: 'Service temporarily unavailable' });
  }
  res.json({ message: 'OK' });
});

app.listen(PORT, () =>
  console.log(`[INFO] node-api started on port ${PORT}`)
);
