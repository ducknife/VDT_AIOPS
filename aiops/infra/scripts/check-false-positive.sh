#!/usr/bin/env bash
# check-false-positive.sh
# =============================================================================
# KIEM TRA FALSE POSITIVE  (do PRECISION cua he thong phat hien)
# -----------------------------------------------------------------------------
# Y NGHIA: Cac script simulate-* deu BOM LOI va ky vong he PHAI sinh alert (do
#   recall / do nhay). Script nay lam NGUOC LAI: KHONG bom loi nao ca, gui traffic
#   BINH THUONG (request 200 nhanh: /api/users + /health, tuyet doi khong /api/slow
#   hay /api/flaky) suot mot cua so quan sat DAI HON cua so phat hien (~35-45s).
#   Sau do doi chieu so alert & incident TRUOC vs SAU:
#     - KHONG doi  -> PASS: he KHOE ma detector KHONG bao dong gia => false positive = 0.
#     - Tang len   -> FAIL: co alert giA (in ra de dieu tra).
#   Day la bang chung RUNTIME cho precision (song song voi AnomalyRuleTest o tang unit).
# KHONG pha gi -> khong can recover.
#
# Usage:
#   ./check-false-positive.sh
#   ./check-false-positive.sh --duration 90
# =============================================================================
R=$'\033[31m'; G=$'\033[32m'; C=$'\033[36m'; GR=$'\033[90m'; N=$'\033[0m'

DURATION=60
while [ $# -gt 0 ]; do case "$1" in
  --duration) DURATION="$2"; shift 2;;
  *) echo "unknown arg: $1"; exit 1;;
esac; done

STATE_DB="aiops-postgres-state"   # DB trang thai cua AIOps (bang alerts / incidents)
BASE="http://localhost:8080"      # nginx -> node-api

# Dem so dong 1 bang trong state-DB (dung password tu chinh env cua container -> khong lo secret)
count_rows() {
  local n
  n=$(docker exec "$STATE_DB" sh -c \
    "PGPASSWORD=\$POSTGRES_PASSWORD psql -U aiops -d aiopsdb -t -A -c 'SELECT count(*) FROM $1'" 2>/dev/null \
    | tr -d '[:space:]')
  case "$n" in (''|*[!0-9]*) echo 0;; (*) echo "$n";; esac
}

printf "\n%s=== [CHECK] False Positive / Precision (he KHOE, khong bom loi) ===%s\n" "$C" "$N"
printf "%s    Quan sat %ss (dai hon cua so phat hien ~35-45s), gui traffic 200 binh thuong.%s\n\n" "$GR" "$DURATION" "$N"

a0=$(count_rows alerts); i0=$(count_rows incidents)
printf "%s[before] alerts=%s  incidents=%s%s\n\n" "$C" "$a0" "$i0" "$N"

printf "%s[traffic] gui request KHOE (/api/users, /health) va cho detector...%s\n" "$GR" "$N"
end=$(( $(date +%s) + DURATION ))
while [ "$(date +%s)" -lt "$end" ]; do
  for _ in 1 2 3 4 5; do
    curl -s -o /dev/null --max-time 5 "$BASE/api/users" &
    curl -s -o /dev/null --max-time 5 "$BASE/health" &
  done
  wait
  left=$(( end - $(date +%s) ))
  printf "  ...he van khoe (~%ss left)\n" "$left"
  sleep 3
done

a1=$(count_rows alerts); i1=$(count_rows incidents)
da=$(( a1 - a0 )); di=$(( i1 - i0 ))
printf "\n%s[after]  alerts=%s (+%s)  incidents=%s (+%s)%s\n\n" "$C" "$a1" "$da" "$i1" "$di" "$N"

if [ "$da" -eq 0 ] && [ "$di" -eq 0 ]; then
  printf "%s[PASS] He KHOE -> detector KHONG sinh alert giA => false positive = 0 (precision OK).%s\n\n" "$G" "$N"
  exit 0
else
  printf "%s[FAIL] Co %s alert / %s incident MOI dù khong bom loi -> false positive!%s\n" "$R" "$da" "$di" "$N"
  docker exec "$STATE_DB" sh -c \
    "PGPASSWORD=\$POSTGRES_PASSWORD psql -U aiops -d aiopsdb -c 'SELECT id, service, type, detected_at FROM alerts ORDER BY id DESC LIMIT 10'" 2>/dev/null
  printf "\n"
  exit 1
fi
