#!/usr/bin/env bash
# Kiểm tra nhanh stack sau khi docker compose up
# Chạy: bash test-stack.sh

BASE="http://localhost:8080"

echo "=== Kiểm tra stack ==="

check() {
  local label=$1 url=$2 expected_code=$3
  code=$(curl -s -o /dev/null -w "%{http_code}" "$url")
  if [ "$code" = "$expected_code" ]; then
    echo "  OK  $label ($code)"
  else
    echo "  FAIL $label — expected $expected_code, got $code"
  fi
}

check "Health check"       "$BASE/health"       200
check "Nginx status"       "$BASE/nginx_status" 200
check "GET /api/users"     "$BASE/api/users"    200
check "GET /api/flaky"     "$BASE/api/flaky"    200
check "POST /api/users"    "$BASE/api/users"    200   # sẽ test riêng bên dưới

echo ""
echo "=== Tạo user mới ==="
curl -s -X POST "$BASE/api/users" \
  -H "Content-Type: application/json" \
  -d '{"name":"Test User","email":"test@example.com"}' | cat

echo ""
echo "=== Đọc lại danh sách users ==="
curl -s "$BASE/api/users" | python3 -m json.tool 2>/dev/null || curl -s "$BASE/api/users"
