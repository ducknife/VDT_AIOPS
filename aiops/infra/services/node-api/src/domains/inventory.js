// Domain INVENTORY — tra tồn kho cache-heavy (cache 60s → nhạy cảm khi Redis OOM/evict) + trừ kho.
const express = require('express');

module.exports = ({ pgPool, redisClient }) => {
  const router = express.Router();

  router.get('/inventory/:sku', async (req, res) => {
    const sku = req.params.sku;
    try {
      const cached = await redisClient.get(`inv:${sku}`);
      if (cached) return res.json({ source: 'cache', data: JSON.parse(cached) });
      const { rows } = await pgPool.query('SELECT sku, name, stock FROM inventory WHERE sku = $1', [sku]);
      if (rows.length === 0) return res.status(404).json({ error: 'SKU not found' });
      await redisClient.setEx(`inv:${sku}`, 60, JSON.stringify(rows[0]));
      res.json({ source: 'db', data: rows[0] });
    } catch (err) {
      console.error('[ERROR] GET /api/inventory —', err.message);
      res.status(500).json({ error: 'Lookup failed' });
    }
  });

  router.post('/inventory/reserve', async (req, res) => {
    const { sku, qty = 1 } = req.body;
    if (!sku) return res.status(400).json({ error: 'sku is required' });
    try {
      const { rows } = await pgPool.query(
        'UPDATE inventory SET stock = stock - $2 WHERE sku = $1 AND stock >= $2 RETURNING sku, name, stock',
        [sku, qty],
      );
      if (rows.length === 0) return res.status(409).json({ error: 'Out of stock' });
      await redisClient.del(`inv:${sku}`);
      res.json({ data: rows[0] });
    } catch (err) {
      console.error('[ERROR] POST /api/inventory/reserve —', err.message);
      res.status(500).json({ error: 'Reserve failed' });
    }
  });

  return router;
};
