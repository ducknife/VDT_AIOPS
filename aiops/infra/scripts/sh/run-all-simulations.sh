#!/usr/bin/env bash
# run-all-simulations.sh
# Chay lan luot cac sim de demo pipeline AIOps end-to-end.
# Moi sim: chay -> PAUSE cho AIOps kip detect + dieu tra (loi con LIVE) -> RECOVER -> sim ke tiep.
# Container-down (kill nginx = entry point) chay CUOI cung.
#
# Usage:
#   ./run-all-simulations.sh            # hoi Enter truoc khi bat dau
#   ./run-all-simulations.sh --auto     # chay thang
#   ./run-all-simulations.sh --wait 120
C=$'\033[36m'; G=$'\033[32m'; GR=$'\033[90m'; N=$'\033[0m'

WAIT=90; AUTO=0
while [ $# -gt 0 ]; do case "$1" in
  --wait) WAIT="$2"; shift 2;;
  --auto) AUTO=1; shift;;
  *) echo "unknown arg: $1"; exit 1;;
esac; done

DIR="$(cd "$(dirname "$0")" && pwd)"
sep() { printf "%s%s%s\n" "$GR" "============================================================" "$N"; }
wait_aiops() {
  sep
  printf "%sCho %ss cho AIOps xu ly (loi dang LIVE)... -> tiep: %s%s\n" "$GR" "$WAIT" "$1" "$N"
  sep
  sleep "$WAIT"
}

sep
printf "%s  VDT-AIOps -- Full Simulation Suite%s\n" "$C" "$N"
sep
if [ "$AUTO" -eq 0 ]; then
  printf "Nhan Enter de bat dau (dam bao stack dang chay)... "
  read -r _
fi

# 1) Load spike (tu hoi khi het tai -> KHONG can recover)
printf "\n%s>>> [1/4] load-spike%s\n" "$N" "$N"
"$DIR/simulate-load-spike.sh" --concurrency 30 --duration 60
wait_aiops "redis-oom"

# 2) Redis OOM (persistent -> recover sau khi AIOps da phan tich)
printf "\n%s>>> [2/4] redis-oom%s\n" "$N" "$N"
"$DIR/simulate-redis-oom.sh" --keycount 2800
wait_aiops "db-exhaustion"
"$DIR/recover.sh" redis-oom

# 3) DB exhaustion (persistent -> recover)
printf "\n%s>>> [3/4] db-exhaustion%s\n" "$N" "$N"
"$DIR/simulate-db-exhaustion.sh" --connections 85
wait_aiops "container-down"
"$DIR/recover.sh" db-exhaustion

# 4) Container down nginx (kill entry point -> chay CUOI)
printf "\n%s>>> [4/4] container-down (nginx)%s\n" "$N" "$N"
"$DIR/simulate-container-down.sh" --service nginx
wait_aiops "ket thuc"
"$DIR/recover.sh" all

sep
printf "%s  Hoan tat -- da recover toan bo.%s\n" "$G" "$N"
sep
