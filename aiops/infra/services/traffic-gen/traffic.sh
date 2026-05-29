#!/bin/sh
# Sinh traffic liên tục vào nginx để tạo log thực tế cho AIOps phân tích

NGINX="http://nginx"
echo "[traffic-gen] started — target: $NGINX"

i=0
while true; do
  i=$((i + 1))

  # Gọi GET /api/users — trigger nginx log + node-api log + redis/postgres
  CODE=$(curl -s -o /dev/null -w "%{http_code}" "$NGINX/api/users")
  echo "[INFO] #$i GET /api/users → $CODE"

  # Gọi /health — AIOps sẽ filter dòng này như "noise"
  curl -s -o /dev/null "$NGINX/health"

  # Gọi /api/flaky — 30% lỗi → tạo [ERROR] log trong node-api
  CODE=$(curl -s -o /dev/null -w "%{http_code}" "$NGINX/api/flaky?rate=0.3")
  echo "[INFO] #$i GET /api/flaky → $CODE"

  # Mỗi 8 vòng: tạo user mới → invalidate redis cache → node-api phải query postgres
  if [ $((i % 8)) -eq 0 ]; then
    RAND=$(od -An -N2 -tu2 /dev/urandom | tr -d ' ')
    curl -s -o /dev/null -X POST "$NGINX/api/users" \
      -H "Content-Type: application/json" \
      -d "{\"name\":\"User$RAND\",\"email\":\"u$RAND@sim.local\"}"
    echo "[INFO] #$i POST /api/users — created u$RAND@sim.local"
  fi

  sleep 5
done
