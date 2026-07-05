#!/usr/bin/env bash
# simulate-minor-latency.sh
# SIM-07 (P4 candidate): blip latency NHE - P99 chi HOI vuot 2000ms roi tu hoi nhanh.
# Ban IT request /api/slow?ms=~2050 (nhinh hon nguong), concurrency THAP, giu vua qua
# quiet-window (~30s) roi DUNG -> P99 tut ngay. Severity DO AGENT cham theo bang chung.
#   node-api=3000 orders=3001 payments=3002 inventory=3003
#
# Usage:
#   ./simulate-minor-latency.sh
#   ./simulate-minor-latency.sh --service node-api-payments --ms 2100 --concurrency 10 --duration 45
Y=$'\033[33m'; C=$'\033[36m'; GR=$'\033[90m'; N=$'\033[0m'

SERVICE="node-api"; CONCURRENCY=10; MS=2050; DURATION=45
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

printf "\n%s=== [SIM-07] Minor Latency Blip on %s (P4 candidate): %s x slow(%sms) for %ss ===%s\n" \
  "$Y" "$SERVICE" "$CONCURRENCY" "$MS" "$DURATION" "$N"
printf "%s    Target: %s  (P99 chi hoi vuot 2000ms -> bat thuong NHE)%s\n\n" "$GR" "$TARGET" "$N"

end=$(( $(date +%s) + DURATION )); round=0
while [ "$(date +%s)" -lt "$end" ]; do
  round=$((round + 1))
  for _ in $(seq 1 "$CONCURRENCY"); do
    curl -s -o /dev/null --max-time 30 "$TARGET" &
  done
  wait
  left=$(( end - $(date +%s) ))
  printf "%s[round %s] ban %s request cham nhe (~%ss left)%s\n" "$C" "$round" "$CONCURRENCY" "$left" "$N"
done

printf "\n%s=== [done] dung tai -> P99 tut ngay -> HIGH_LATENCY tu RESOLVE (%s) ===%s\n" "$C" "$SERVICE" "$N"
printf "%s    Ky vong: agent cham P4 (minor). Neu ra P3 -> ha --ms/--concurrency.%s\n\n" "$GR" "$N"
