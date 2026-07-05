#!/usr/bin/env bash
# simulate-load-spike.sh
# SIM-02: HTTP traffic dot bien -> error rate spike. Gui lien tuc /api/flaky -> node-api 500
# -> error rate tang vot -> ERROR_RATE_SPIKE. Giu >= quiet-window (30s) thi investigate moi chay.
#   node-api=3000 orders=3001 payments=3002 inventory=3003
#
# Usage:
#   ./simulate-load-spike.sh
#   ./simulate-load-spike.sh --service node-api-orders --concurrency 30 --duration 90 --rate 0.5
R=$'\033[31m'; Y=$'\033[33m'; C=$'\033[36m'; GR=$'\033[90m'; N=$'\033[0m'

SERVICE="node-api"; CONCURRENCY=30; DURATION=60; RATE=0.5
while [ $# -gt 0 ]; do case "$1" in
  --service) SERVICE="$2"; shift 2;;
  --concurrency) CONCURRENCY="$2"; shift 2;;
  --duration) DURATION="$2"; shift 2;;
  --rate) RATE="$2"; shift 2;;
  *) echo "unknown arg: $1"; exit 1;;
esac; done

case "$SERVICE" in
  node-api) PORT=3000;;
  node-api-orders) PORT=3001;;
  node-api-payments) PORT=3002;;
  node-api-inventory) PORT=3003;;
  *) echo "invalid --service: $SERVICE"; exit 1;;
esac
TARGET="http://localhost:$PORT/api/flaky?rate=$RATE"

printf "\n%s=== [SIM-02] Load Spike on %s : %s concurrent for %ss ===%s\n" \
  "$Y" "$SERVICE" "$CONCURRENCY" "$DURATION" "$N"
printf "%s    Target: %s  (Ctrl+C de dung som)%s\n\n" "$GR" "$TARGET" "$N"

end=$(( $(date +%s) + DURATION )); round=0; totalOk=0; totalErr=0
while [ "$(date +%s)" -lt "$end" ]; do
  round=$((round + 1))
  tmp=$(mktemp -d)
  for i in $(seq 1 "$CONCURRENCY"); do
    ( curl -s -o /dev/null -w "%{http_code}" --max-time 10 "$TARGET" > "$tmp/$i" 2>/dev/null ) &
  done
  wait
  ok=0; err=0
  for i in $(seq 1 "$CONCURRENCY"); do
    c=$(cat "$tmp/$i" 2>/dev/null || echo 0); [ -z "$c" ] && c=0
    if [ "$c" -ge 200 ] && [ "$c" -lt 400 ]; then ok=$((ok + 1)); else err=$((err + 1)); fi
  done
  rm -rf "$tmp"
  totalOk=$((totalOk + ok)); totalErr=$((totalErr + err))
  tot=$((ok + err)); errRate=0; [ "$tot" -gt 0 ] && errRate=$(( err * 100 / tot ))
  left=$(( end - $(date +%s) ))
  col="$N"; [ "$errRate" -gt 30 ] && col="$R"
  printf "%s[round %s] OK=%s ERR=%s error_rate=%s%% (~%ss left)%s\n" "$col" "$round" "$ok" "$err" "$errRate" "$left" "$N"
done

printf "\n%s=== Result (%s) ===%s\n" "$Y" "$SERVICE" "$N"
tot=$((totalOk + totalErr)); finalRate=0; [ "$tot" -gt 0 ] && finalRate=$(( totalErr * 100 / tot ))
printf "  Total OK   : %s\n  Total ERROR: %s\n  Error rate : %s%%\n" "$totalOk" "$totalErr" "$finalRate"
printf "\n%s=== [done] het tai -> error rate tut -> AnomalyDetector se RESOLVE alert ===%s\n\n" "$C" "$N"
