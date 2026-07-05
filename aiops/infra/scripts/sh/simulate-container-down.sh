#!/usr/bin/env bash
# simulate-container-down.sh
# SIM: container bi stop dot ngot (crash / OOM-kill / process die) -> SERVICE_DOWN.
# De container DOWN cho AIOps phat hien + dieu tra (KHONG tu hoi).
# Recover: ./recover.sh container-down
#
# Usage:
#   ./simulate-container-down.sh                          # mac dinh nginx
#   ./simulate-container-down.sh --service node-api-orders
#   ./simulate-container-down.sh --service postgres       # downstream CHUNG
R=$'\033[31m'; Y=$'\033[33m'; C=$'\033[36m'; GR=$'\033[90m'; N=$'\033[0m'

SERVICE="nginx"
while [ $# -gt 0 ]; do case "$1" in
  --service) SERVICE="$2"; shift 2;;
  *) echo "unknown arg: $1"; exit 1;;
esac; done

case "$SERVICE" in
  nginx|node-api|node-api-orders|node-api-payments|node-api-inventory|\
  postgres|postgres-payments|postgres-inventory|redis|redis-payments|redis-inventory) ;;
  *) echo "invalid --service: $SERVICE"; exit 1;;
esac

container="aiops-$SERVICE"
BASE="http://localhost:8080"

printf "\n%s=== [SIM] Container Down: %s ===%s\n\n" "$R" "$container" "$N"

printf "%s[before] 10 dong log cuoi cua %s:%s\n" "$C" "$container" "$N"
docker logs "$container" --tail 10 2>&1

printf "\n%s[kill] dang stop %s ...%s\n" "$R" "$container" "$N"
docker stop "$container" >/dev/null
printf "       stopped luc %s\n" "$(date +%H:%M:%S)"

printf "\n%s[probe] goi API sau khi %s down:%s\n" "$Y" "$SERVICE" "$N"
sleep 2
for i in 1 2 3 4; do
  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 "$BASE/api/users" 2>/dev/null || echo "000")
  if [ "$code" = "200" ]; then
    printf "  probe %s : %s OK\n" "$i" "$code"
  else
    printf "  %sprobe %s : FAILED (http=%s)%s\n" "$R" "$i" "$code" "$N"
  fi
  sleep 1
done

printf "\n%s[hold] %s DE NGUYEN DOWN cho AIOps detect + analyze.%s\n" "$R" "$container" "$N"
printf "%s       Recover: ./recover.sh container-down%s\n\n" "$GR" "$N"
