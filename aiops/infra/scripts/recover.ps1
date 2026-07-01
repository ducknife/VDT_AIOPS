# recover.ps1 -- dua he ve khoe sau khi mo phong loi. Heal DONG moi instance (khong hardcode).
#   - container-down : container bi stop      -> docker start
#   - redis-oom      : redis day flood keys   -> xoa keys (moi redis instance)
#   - db-exhaustion  : pg_sleep giu connection-> kill connection (moi postgres instance)
# Sim "nhe" (load-spike / high-latency / minor-latency) TU HOI khi ngung tai -> KHONG can recover.
#
# Usage:
#   .\recover.ps1                              # heal TAT CA moi instance (mac dinh)
#   .\recover.ps1 redis-oom                    # xoa flood keys tren MOI redis
#   .\recover.ps1 db-exhaustion                # kill pg_sleep tren MOI postgres
#   .\recover.ps1 container-down               # start lai MOI container dang stop
#   .\recover.ps1 redis-oom -Service redis-payments   # chi 1 instance
param(
    [ValidateSet("all", "redis-oom", "container-down", "db-exhaustion")]
    [string]$Scenario = "all",
    [string]$Service = ""   # rong = tat ca; hoac ten compose service cu the (vd redis-payments)
)

# Liet ke container datastore theo tien to (loai tru exporter). Neu -Service -> chi cai do.
function Stores([string]$prefix) {
    if ($Service) { return @("aiops-$Service") }
    docker ps -a --filter "label=com.docker.compose.project=aiops-sim" --format "{{.Names}}" |
    Where-Object { $_ -like "aiops-$prefix*" -and $_ -notlike "*exporter*" }
}
function DbName([string]$container) {
    switch -Wildcard ($container) {
        "*payments*" { "paymentsdb" }
        "*inventory*" { "inventorydb" }
        default { "appdb" }
    }
}

function Recover-RedisOom {
    foreach ($c in Stores "redis") {
        $n = docker exec $c redis-cli EVAL `
            "local ks=redis.call('keys','oom:flood:*') for _,k in ipairs(ks) do redis.call('del',k) end return #ks" 0 2>&1
        Write-Host "[recover] $c : deleted $n flood keys" -ForegroundColor Green
    }
}
function Recover-Db {
    foreach ($c in Stores "postgres") {
        docker exec $c psql -U appuser -d (DbName $c) -c `
            "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE query LIKE 'SELECT pg_sleep%';" 2>&1 | Out-Null
        Write-Host "[recover] $c : terminated pg_sleep blockers" -ForegroundColor Green
    }
}
function Recover-Containers {
    $names = if ($Service) {
        @("aiops-$Service")
    }
    else {
        docker ps -a --filter "label=com.docker.compose.project=aiops-sim" --filter "status=exited" --format "{{.Names}}"
    }
    foreach ($c in $names) {
        $running = docker inspect -f '{{.State.Running}}' $c 2>$null
        if ($running -eq "false") {
            docker start $c | Out-Null
            Write-Host "[recover] started $c" -ForegroundColor Green
        }
    }
}

switch ($Scenario) {
    "redis-oom" { Recover-RedisOom }
    "container-down" { Recover-Containers }
    "db-exhaustion" { Recover-Db }
    "all" { Recover-Containers; Recover-RedisOom; Recover-Db }
}

Write-Host "[done] recovered: $Scenario $(if($Service){"($Service)"})" -ForegroundColor Cyan
