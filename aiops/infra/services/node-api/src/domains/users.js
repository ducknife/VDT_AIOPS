// Domain USERS — cache-aside (Redis → PostgreSQL). Đọc nhiều, cache 30s.
const express = require('express');

module.exports = ({ pgPool, redisClient }) => {
  const router = express.Router();

  router.get('/users', async (_req, res) => {
    try {
      const cached = await redisClient.get('users:all');
      if (cached) return res.json({ source: 'cache', data: JSON.parse(cached) });
      const { rows } = await pgPool.query(
        'SELECT id, name, email, created_at FROM users ORDER BY created_at DESC LIMIT 20',
      );
      await redisClient.setEx('users:all', 30, JSON.stringify(rows));
      res.json({ source: 'db', data: rows });
    } catch (err) {
      console.error('[ERROR] GET /api/users —', err.message);
      res.status(500).json({ error: 'Database query failed' });
    }
  });

  router.post('/users', async (req, res) => {
    const { name, email } = req.body;
    if (!name || !email) return res.status(400).json({ error: 'name and email are required' });
    try {
      const { rows } = await pgPool.query(
        'INSERT INTO users(name, email) VALUES($1, $2) RETURNING *',
        [name, email],
      );
      await redisClient.del('users:all');
      res.status(201).json({ data: rows[0] });
    } catch (err) {
      console.error('[ERROR] POST /api/users —', err.message);
      res.status(500).json({ error: 'Failed to create user' });
    }
  });

  return router;
};
