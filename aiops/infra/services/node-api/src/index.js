const express = require('express');
const { Pool } = require('pg');
const { createClient } = require('redis');
const promClient = require('prom-client');

const app = express();
const PORT = process.env.PORT || 3000;
// Hành vi nghiệp vụ chọn theo env (users | orders | payments | inventory). Cùng 1 image, khác domain.
const DOMAIN = process.env.SERVICE_DOMAIN || 'users';

app.use(express.json());

// ─── PostgreSQL ───────────────────────────────────────────────────────────────
// max: 10 → simulation DB exhaustion khi mở >10 connection đồng thời
const pgPool = new Pool({
  connectionString: process.env.DATABASE_URL,
  max: 10,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 2_000,
});
pgPool.on('error', (err) => console.error('[POSTGRES ERROR]', err.message));

// ─── Redis ────────────────────────────────────────────────────────────────────
const redisClient = createClient({ url: process.env.REDIS_URL });
redisClient.on('error', (err) => console.error('[REDIS ERROR]', err.message));
redisClient.connect();

// ─── Prometheus metrics (DÙNG CHUNG mọi domain — BẮT BUỘC cho AIOps) ────────────
const register = new promClient.Registry();
promClient.collectDefaultMetrics({ register });

const httpRequestsTotal = new promClient.Counter({
  name: 'http_requests_total',
  help: 'Tong so HTTP request da xu ly (rate())',
  registers: [register],
});
const httpErrorsTotal = new promClient.Counter({
  name: 'http_errors_total',
  help: 'Tong so HTTP response 5xx (rate())',
  registers: [register],
});
const httpRequestDuration = new promClient.Histogram({
  name: 'http_request_duration_seconds',
  help: 'Do tre HTTP request (giay) → histogram_quantile cho P99',
  buckets: [0.01, 0.05, 0.1, 0.25, 0.5, 1, 2, 3, 5, 10],
  registers: [register],
});

// ─── Request logger + đo latency (dùng chung) ───────────────────────────────────
app.use((req, res, next) => {
  const endTimer = httpRequestDuration.startTimer();
  res.on('finish', () => {
    const seconds = endTimer();
    httpRequestsTotal.inc();
    if (res.statusCode >= 500) httpErrorsTotal.inc();
    const ms = (seconds * 1000).toFixed(0);
    const level = res.statusCode >= 500 ? '[ERROR]' : res.statusCode >= 400 ? '[WARN]' : '[INFO]';
    console.log(`${level} ${req.method} ${req.path} → ${res.statusCode} (${ms}ms)`);
  });
  next();
});

// ─── Endpoint hạ tầng (dùng chung) ──────────────────────────────────────────────
app.get('/metrics', async (_req, res) => {
  res.set('Content-Type', register.contentType);
  res.end(await register.metrics());
});
app.get('/health', (_req, res) => res.json({ status: 'healthy', domain: DOMAIN, timestamp: new Date().toISOString() }));

// ─── Endpoint mô phỏng (dùng chung — scripts sim bắn vào đây) ────────────────────
app.get('/api/compute', (req, res) => {
  const n = parseInt(req.query.n || '500000', 10);
  let sum = 0;
  for (let i = 0; i < n; i++) sum += Math.random();
  res.json({ result: sum, iterations: n });
});
app.get('/api/slow', async (req, res) => {
  const ms = parseInt(req.query.ms || '3000', 10);
  await new Promise((r) => setTimeout(r, ms));
  res.json({ slept: ms });
});
app.get('/api/flaky', (req, res) => {
  const rate = parseFloat(req.query.rate || '0.3');
  if (Math.random() < rate) {
    console.error('[ERROR] /api/flaky — simulated failure triggered');
    return res.status(500).json({ error: 'Service temporarily unavailable' });
  }
  res.json({ message: 'OK' });
});

// ─── Nghiệp vụ RIÊNG theo domain (mount tại /api) ───────────────────────────────
let buildDomain;
try {
  buildDomain = require(`./domains/${DOMAIN}`);
} catch (e) {
  console.error(`[WARN] domain '${DOMAIN}' khong ton tai → fallback 'users'`);
  buildDomain = require('./domains/users');
}
app.use('/api', buildDomain({ pgPool, redisClient }));

app.listen(PORT, () => console.log(`[INFO] node-api (domain=${DOMAIN}) started on port ${PORT}`));
