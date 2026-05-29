-- Khởi tạo schema cho simulation environment
-- File này chạy tự động lần đầu khi postgres container khởi động

CREATE TABLE IF NOT EXISTS users (
    id         SERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO users (name, email) VALUES
    ('Alice Nguyen', 'alice@example.com'),
    ('Bob Tran',     'bob@example.com'),
    ('Charlie Le',   'charlie@example.com')
ON CONFLICT (email) DO NOTHING;
