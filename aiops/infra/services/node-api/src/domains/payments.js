// Domain PAYMENTS — ghi giao dịch, mô phỏng độ trễ payment gateway (~80ms). Ghi nặng Postgres.
const express = require('express');

module.exports = ({ pgPool, redisClient }) => {
  const router = express.Router();

  router.post('/payments', async (req, res) => {
    const { orderRef, amount } = req.body;
    if (!amount) return res.status(400).json({ error: 'amount is required' });
    try {
      await new Promise((r) => setTimeout(r, 80)); // mô phỏng gọi gateway ngoài
      const { rows } = await pgPool.query(
        "INSERT INTO payments(order_ref, amount, status) VALUES($1, $2, 'captured') RETURNING *",
        [orderRef || null, amount],
      );
      await redisClient.del('payments:recent');
      res.status(201).json({ data: rows[0] });
    } catch (err) {
      console.error('[ERROR] POST /api/payments —', err.message);
      res.status(500).json({ error: 'Payment failed' });
    }
  });

  router.get('/payments', async (_req, res) => {
    try {
      const cached = await redisClient.get('payments:recent');
      if (cached) return res.json({ source: 'cache', data: JSON.parse(cached) });
      const { rows } = await pgPool.query(
        'SELECT id, order_ref, amount, status, created_at FROM payments ORDER BY created_at DESC LIMIT 20',
      );
      await redisClient.setEx('payments:recent', 15, JSON.stringify(rows));
      res.json({ source: 'db', data: rows });
    } catch (err) {
      console.error('[ERROR] GET /api/payments —', err.message);
      res.status(500).json({ error: 'Failed to list payments' });
    }
  });

  return router;
};
