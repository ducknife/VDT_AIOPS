#!/usr/bin/env bash
# simulate-db-exhaustion.sh
# SIM: PostgreSQL can kiet connection slot. max_connections=100.
# Mo ~85 connection dai (pg_sleep) -> chiem >80% slot -> connUtilPercent > 80 -> DB_EXHAUSTION.
# Connection giu VINH VIEN (docker exec -d + pg_sleep dai). Recover: ./recover.sh db-exhaustion
#
# Usage:
#   ./simulate-db-exhaustion.sh
#   ./simulate-db-exhaustion.sh --service postgres-payments --connections 85
R=$'\033[31m'; Y=$'\033[33m'; C=$'\033[36m'; GR=$'\033[90m'; N=$'\033[0m'

SERVICE="postgres"; CONNS=85
while [ $# -gt 0 ]; do case "$1" in
  --service) SERVICE="$2"; shift 2;;
  --connections) CONNS="$2"; shift 2;;
  *) echo "unknown arg: $1"; exit 1;;
esac; done

case "$SERVICE" in
  postgres) DB="appdb";;
  postgres-payments) DB="paymentsdb";;
  postgres-inventory) DB="inventorydb";;
  *) echo "invalid --service: $SERVICE"; exit 1;;
esac
container="aiops-$SERVICE"

printf "\n%s=== [SIM] DB Connection Exhaustion: %s (%s) ===%s\n" "$Y" "$container" "$DB" "$N"
printf "    Mo %s connection dai (pg_sleep)\n\n" "$CONNS"

printf "%s[before] So connection hien tai:%s\n" "$C" "$N"
docker exec "$container" psql -U appuser -d "$DB" \
  -c "SELECT count(*) AS active_conn FROM pg_stat_activity WHERE datname='$DB';" 2>&1

printf "\n%s[exhaust] mo %s connection dai (pg_sleep, detached)...%s\n" "$R" "$CONNS" "$N"
i=1
while [ "$i" -le "$CONNS" ]; do
  docker exec -d "$container" psql -U appuser -d "$DB" -c "SELECT pg_sleep(86400);" >/dev/null 2>&1
  i=$((i + 1))
done
sleep 2

printf "\n%s[during] So connection khi dang can kiet:%s\n" "$C" "$N"
docker exec "$container" psql -U appuser -d "$DB" \
  -c "SELECT count(*) AS active_conn FROM pg_stat_activity WHERE datname='$DB';" 2>&1

printf "\n%s[hold] %s connection DUOC GIU cho AIOps detect + analyze.%s\n" "$R" "$CONNS" "$N"
printf "%s       Recover: ./recover.sh db-exhaustion%s\n\n" "$GR" "$N"
