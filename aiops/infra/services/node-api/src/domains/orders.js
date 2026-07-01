// Domain ORDERS — ghi nhiều (tạo đơn) + đọc có cache ngắn. Phụ thuộc mạnh Postgres.
const express = require('express');

module.exports = ({ pgPool, redisClient }) => {
  const router = express.Router();

  router.get('/orders', async (_req, res) => {
    try {
      const cached = await redisClient.get('orders:recent');
      if (cached) return res.json({ source: 'cache', data: JSON.parse(cached) });
      const { rows } = await pgPool.query(
        'SELECT id, item, qty, amount, created_at FROM orders ORDER BY created_at DESC LIMIT 20',
      );
      await redisClient.setEx('orders:recent', 15, JSON.stringify(rows));
      res.json({ source: 'db', data: rows });
    } catch (err) {
      console.error('[ERROR] GET /api/orders —', err.message);
      res.status(500).json({ error: 'Failed to list orders' });
    }
  });

  router.post('/orders', async (req, res) => {
    const { item, qty = 1, amount = 0 } = req.body;
    if (!item) return res.status(400).json({ error: 'item is required' });
    try {
      const { rows } = await pgPool.query(
        'INSERT INTO orders(item, qty, amount) VALUES($1, $2, $3) RETURNING *',
        [item, qty, amount],
      );
      await redisClient.del('orders:recent');
      res.status(201).json({ data: rows[0] });
    } catch (err) {
      console.error('[ERROR] POST /api/orders —', err.message);
      res.status(500).json({ error: 'Failed to create order' });
    }
  });

  return router;
};
