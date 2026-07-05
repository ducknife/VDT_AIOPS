#!/usr/bin/env bash
# recover.sh -- dua he ve khoe sau khi mo phong loi. Heal DONG moi instance (khong hardcode).
#   container-down : container bi stop      -> docker start
#   redis-oom      : redis day flood keys   -> xoa keys (moi redis instance)
#   db-exhaustion  : pg_sleep giu connection-> kill connection (moi postgres instance)
# Sim "nhe" (load-spike / high-latency / minor-latency) TU HOI -> KHONG can recover.
#
# Usage:
#   ./recover.sh                                  # heal TAT CA
#   ./recover.sh redis-oom                        # xoa flood keys tren MOI redis
#   ./recover.sh db-exhaustion
#   ./recover.sh container-down
#   ./recover.sh redis-oom --service redis-payments   # chi 1 instance
G=$'\033[32m'; C=$'\033[36m'; N=$'\033[0m'

SCENARIO="all"; SERVICE=""
while [ $# -gt 0 ]; do case "$1" in
  all|redis-oom|container-down|db-exhaustion) SCENARIO="$1"; shift;;
  --service) SERVICE="$2"; shift 2;;
  *) echo "unknown arg: $1"; exit 1;;
esac; done

# Liet ke container datastore theo tien to (loai tru exporter). Neu --service -> chi cai do.
stores() {
  local prefix="$1"
  if [ -n "$SERVICE" ]; then echo "aiops-$SERVICE"; return; fi
  docker ps -a --filter "label=com.docker.compose.project=aiops-sim" --format "{{.Names}}" \
    | grep "^aiops-$prefix" | grep -v "exporter"
}
db_name() {
  case "$1" in
    *payments*) echo "paymentsdb";;
    *inventory*) echo "inventorydb";;
    *) echo "appdb";;
  esac
}

recover_redis() {
  for c in $(stores redis); do
    n=$(docker exec "$c" redis-cli EVAL \
      "local ks=redis.call('keys','oom:flood:*') for _,k in ipairs(ks) do redis.call('del',k) end return #ks" 0 2>&1)
    printf "%s[recover] %s : deleted %s flood keys%s\n" "$G" "$c" "$n" "$N"
  done
}
recover_db() {
  for c in $(stores postgres); do
    docker exec "$c" psql -U appuser -d "$(db_name "$c")" \
      -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE query LIKE 'SELECT pg_sleep%';" >/dev/null 2>&1
    printf "%s[recover] %s : terminated pg_sleep blockers%s\n" "$G" "$c" "$N"
  done
}
recover_containers() {
  local names
  if [ -n "$SERVICE" ]; then
    names="aiops-$SERVICE"
  else
    names=$(docker ps -a --filter "label=com.docker.compose.project=aiops-sim" \
      --filter "status=exited" --format "{{.Names}}")
  fi
  for c in $names; do
    running=$(docker inspect -f '{{.State.Running}}' "$c" 2>/dev/null)
    if [ "$running" = "false" ]; then
      docker start "$c" >/dev/null
      printf "%s[recover] started %s%s\n" "$G" "$c" "$N"
    fi
  done
}

case "$SCENARIO" in
  redis-oom) recover_redis;;
  container-down) recover_containers;;
  db-exhaustion) recover_db;;
  all) recover_containers; recover_redis; recover_db;;
esac

label="$SCENARIO"; [ -n "$SERVICE" ] && label="$SCENARIO ($SERVICE)"
printf "%s[done] recovered: %s%s\n" "$C" "$label" "$N"
