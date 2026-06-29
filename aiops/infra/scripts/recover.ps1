# recover.ps1 -- dua he ve khoe sau khi mo phong loi.
# Chi recover cac sim de lai TRANG THAI BEN:
#   - container-down : container bi stop      -> docker start
#   - redis-oom      : redis day flood keys   -> xoa keys
#   - db-exhaustion  : pg_sleep giu connection-> kill connection
# Cac sim "nhe" (load-spike / high-latency / minor-latency) TU HOI khi ngung tai -> KHONG can recover.
#
# Usage:
#   .\recover.ps1                  # heal TAT CA (mac dinh)
#   .\recover.ps1 redis-oom        # chi xoa flood keys
#   .\recover.ps1 container-down   # start lai container sim dang stop
#   .\recover.ps1 db-exhaustion    # kill connection pg_sleep dang giu
param(
    [ValidateSet("all", "redis-oom", "container-down", "db-exhaustion")]
    [string]$Scenario = "all"
)

function Recover-RedisOom {
    Write-Host "[recover] redis-oom: xoa flood keys (oom:flood:*)..." -ForegroundColor Green
    $n = docker exec aiops-redis redis-cli EVAL `
        "local ks=redis.call('keys','oom:flood:*') for _,k in ipairs(ks) do redis.call('del',k) end return #ks" 0 2>&1
    Write-Host "          deleted $n keys" -ForegroundColor Gray
}

function Recover-Containers {
    Write-Host "[recover] start lai container sim dang stop..." -ForegroundColor Green
    foreach ($c in @("aiops-nginx", "aiops-node-api", "aiops-postgres", "aiops-redis")) {
        $running = docker inspect -f '{{.State.Running}}' $c 2>$null
        if ($running -eq "false") {
            docker start $c | Out-Null
            Write-Host "          started $c" -ForegroundColor Gray
        }
    }
}

function Recover-Db {
    Write-Host "[recover] db-exhaustion: kill connection pg_sleep..." -ForegroundColor Green
    docker exec aiops-postgres psql -U appuser -d appdb -c `
        "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE query LIKE 'SELECT pg_sleep%';" 2>&1 | Out-Null
    Write-Host "          terminated blocker connections" -ForegroundColor Gray
}

switch ($Scenario) {
    "redis-oom" { Recover-RedisOom }
    "container-down" { Recover-Containers }
    "db-exhaustion" { Recover-Db }
    "all" { Recover-Containers; Recover-RedisOom; Recover-Db }
}

Write-Host "[done] recovered: $Scenario" -ForegroundColor Cyan
