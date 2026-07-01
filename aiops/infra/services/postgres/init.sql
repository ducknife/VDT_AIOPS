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

-- Bảng cho các domain node-api (init.sql chạy ở MỌI db nên mọi db đều có đủ bảng — vô hại).

-- domain ORDERS ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS orders (
    id         SERIAL PRIMARY KEY,
    item       VARCHAR(100) NOT NULL,
    qty        INT NOT NULL DEFAULT 1,
    amount     NUMERIC(10,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO orders (item, qty, amount)
    SELECT 'Widget', 2, 19.98 WHERE NOT EXISTS (SELECT 1 FROM orders WHERE item = 'Widget');
INSERT INTO orders (item, qty, amount)
    SELECT 'Gadget', 1, 9.99  WHERE NOT EXISTS (SELECT 1 FROM orders WHERE item = 'Gadget');

-- domain PAYMENTS --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payments (
    id         SERIAL PRIMARY KEY,
    order_ref  VARCHAR(64),
    amount     NUMERIC(10,2) NOT NULL,
    status     VARCHAR(20) DEFAULT 'captured',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- domain INVENTORY -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS inventory (
    sku   VARCHAR(40) PRIMARY KEY,
    name  VARCHAR(100),
    stock INT NOT NULL DEFAULT 0
);
INSERT INTO inventory (sku, name, stock) VALUES
    ('SKU-1', 'Widget', 100),
    ('SKU-2', 'Gadget', 50)
ON CONFLICT (sku) DO NOTHING;
