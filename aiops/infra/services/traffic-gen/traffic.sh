#!/bin/sh
# Sinh traffic lien tuc vao TAT CA node-api instance -> tao log + metric nen cho AIOps.
# Trong Docker network goi qua TEN SERVICE : cong noi bo 3000 (khong phai host port).
NGINX="http://nginx"                       # -> node-api (domain users)
ORDERS="http://node-api-orders:3000"
PAYMENTS="http://node-api-payments:3000"
INVENTORY="http://node-api-inventory:3000"
echo "[traffic-gen] started — users(via nginx) + orders + payments + inventory"

i=0
while true; do
  i=$((i + 1))

  # ── users (qua nginx) ──
  CODE=$(curl -s -o /dev/null -w "%{http_code}" "$NGINX/api/users")
  echo "[INFO] #$i users     GET /api/users -> $CODE"
  curl -s -o /dev/null "$NGINX/health"                                  # noise
  CODE=$(curl -s -o /dev/null -w "%{http_code}" "$NGINX/api/flaky?rate=0.3")
  echo "[INFO] #$i users     GET /api/flaky -> $CODE"

  # ── orders / payments / inventory (goi thang instance) ──
  CODE=$(curl -s -o /dev/null -w "%{http_code}" "$ORDERS/api/orders")
  echo "[INFO] #$i orders    GET /api/orders -> $CODE"
  CODE=$(curl -s -o /dev/null -w "%{http_code}" "$PAYMENTS/api/payments")
  echo "[INFO] #$i payments  GET /api/payments -> $CODE"
  CODE=$(curl -s -o /dev/null -w "%{http_code}" "$INVENTORY/api/inventory/SKU-1")
  echo "[INFO] #$i inventory GET /api/inventory/SKU-1 -> $CODE"

  # ── moi 8 vong: cac thao tac GHI (invalidate cache -> query postgres) ──
  if [ $((i % 8)) -eq 0 ]; then
    RAND=$(od -An -N2 -tu2 /dev/urandom | tr -d ' ')
    curl -s -o /dev/null -X POST "$NGINX/api/users" -H "Content-Type: application/json" \
      -d "{\"name\":\"User$RAND\",\"email\":\"u$RAND@sim.local\"}"
    curl -s -o /dev/null -X POST "$ORDERS/api/orders" -H "Content-Type: application/json" \
      -d "{\"item\":\"Item$RAND\",\"qty\":1,\"amount\":9.99}"
    curl -s -o /dev/null -X POST "$PAYMENTS/api/payments" -H "Content-Type: application/json" \
      -d "{\"orderRef\":\"o$RAND\",\"amount\":19.99}"
    echo "[INFO] #$i writes done (users/orders/payments)"
  fi

  sleep 5
done
