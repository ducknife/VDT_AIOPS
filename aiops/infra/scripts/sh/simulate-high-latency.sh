#!/usr/bin/env bash
# simulate-high-latency.sh
# SIM-03: node-api CHAM (high latency P99). Danh vao /api/slow (cho BAT DONG BO)
# -> request cham nhung event loop VAN RANH -> /health van up=1 -> chi no HIGH_LATENCY.
# Latency TU HOI khi dung script. Ban THANG vao port instance (bypass nginx).
#   node-api=3000 orders=3001 payments=3002 inventory=3003
#
# Usage:
#   ./simulate-high-latency.sh
#   ./simulate-high-latency.sh --service node-api-orders --ms 4000 --concurrency 30 --duration 180
Y=$'\033[33m'; C=$'\033[36m'; GR=$'\033[90m'; N=$'\033[0m'

SERVICE="node-api"; CONCURRENCY=25; MS=3000; DURATION=120
while [ $# -gt 0 ]; do case "$1" in
  --service) SERVICE="$2"; shift 2;;
  --concurrency) CONCURRENCY="$2"; shift 2;;
  --ms) MS="$2"; shift 2;;
  --duration) DURATION="$2"; shift 2;;
  *) echo "unknown arg: $1"; exit 1;;
esac; done

case "$SERVICE" in
  node-api) PORT=3000;;
  node-api-orders) PORT=3001;;
  node-api-payments) PORT=3002;;
  node-api-inventory) PORT=3003;;
  *) echo "invalid --service: $SERVICE"; exit 1;;
esac
TARGET="http://localhost:$PORT/api/slow?ms=$MS"

printf "\n%s=== [SIM-03] High Latency P99 on %s : %s x slow(%sms) for %ss ===%s\n" \
  "$Y" "$SERVICE" "$CONCURRENCY" "$MS" "$DURATION" "$N"
printf "%s    Target: %s  (ban thang vao instance; Ctrl+C de dung som)%s\n\n" "$GR" "$TARGET" "$N"

end=$(( $(date +%s) + DURATION )); round=0
while [ "$(date +%s)" -lt "$end" ]; do
  round=$((round + 1))
  for _ in $(seq 1 "$CONCURRENCY"); do
    curl -s -o /dev/null --max-time 60 "$TARGET" &
  done
  wait
  left=$(( end - $(date +%s) ))
  printf "%s[round %s] da ban %s request cham (~%ss left)%s\n" "$C" "$round" "$CONCURRENCY" "$left" "$N"
done

printf "\n%s=== [done] het tai -> P99 tut -> AnomalyDetector se RESOLVE HIGH_LATENCY (%s) ===%s\n\n" "$C" "$SERVICE" "$N"
