#!/usr/bin/env bash
# simulate-redis-oom.sh
# SIM: Redis cham maxmemory -> eviction (allkeys-lru) -> cache miss storm -> REDIS_OOM.
# Nhet keys lon vao redis cho vuot maxmemory -> evicted_keys tang. Keys KHONG co TTL -> BEN.
# Recover: ./recover.sh redis-oom
#
# Usage:
#   ./simulate-redis-oom.sh
#   ./simulate-redis-oom.sh --service redis-payments --keycount 2800 --valuekb 100
R=$'\033[31m'; Y=$'\033[33m'; C=$'\033[36m'; M=$'\033[35m'; GR=$'\033[90m'; N=$'\033[0m'

SERVICE="redis"; KEYCOUNT=2800; VALUEKB=100
while [ $# -gt 0 ]; do case "$1" in
  --service) SERVICE="$2"; shift 2;;
  --keycount) KEYCOUNT="$2"; shift 2;;
  --valuekb) VALUEKB="$2"; shift 2;;
  *) echo "unknown arg: $1"; exit 1;;
esac; done

case "$SERVICE" in
  redis|redis-payments|redis-inventory) ;;
  *) echo "invalid --service: $SERVICE"; exit 1;;
esac
container="aiops-$SERVICE"
bytes=$(( VALUEKB * 1024 ))

printf "\n%s=== [SIM] Redis OOM: %s ===%s\n" "$Y" "$container" "$N"
printf "    Chen %s keys x %sKB ~ %sMB\n\n" "$KEYCOUNT" "$VALUEKB" "$(( KEYCOUNT * VALUEKB / 1024 ))"

printf "%s[before] Redis memory:%s\n" "$C" "$N"
docker exec "$container" redis-cli INFO memory 2>&1 | grep -E "used_memory_human|maxmemory_human"
docker exec "$container" redis-cli INFO stats 2>&1 | grep "evicted_keys"

printf "\n%s[fill] dang nhet du lieu vao Redis...%s\n" "$R" "$N"
docker exec "$container" redis-cli EVAL \
  "for i=1,$KEYCOUNT do redis.call('SET','oom:flood:'..i,string.rep('X',$bytes)) end return redis.call('DBSIZE')" 0 >/dev/null

mem=$(docker exec "$container" redis-cli INFO memory 2>&1 | grep "used_memory_human:" | cut -d: -f2 | tr -d '\r')
ev=$(docker exec "$container" redis-cli INFO stats 2>&1 | grep "evicted_keys:" | cut -d: -f2 | tr -d '\r')
printf "  filled -- mem=%s evicted=%s\n" "$mem" "$ev"

printf "\n%s[after] Redis stats:%s\n" "$M" "$N"
docker exec "$container" redis-cli INFO stats 2>&1 | grep -E "evicted_keys|keyspace_hits|keyspace_misses"

printf "\n%s[hold] OOM DE NGUYEN (keys khong TTL) cho AIOps detect + analyze.%s\n" "$R" "$N"
printf "%s       Recover: ./recover.sh redis-oom%s\n\n" "$GR" "$N"
