# simulate-redis-oom.ps1
# SIM-04: Redis dat maxmemory -> eviction -> cache miss storm
# Nhet du lieu vao Redis cho den khi cham gioi han 256MB.
# Redis se bat dau evict keys cu (allkeys-lru policy).
# Hau qua: cache miss -> node-api phai query postgres -> latency tang.

param(
    [int]$KeyCount    = 2800,   # ~2800 keys x 100KB ~ 280MB > maxmemory 256MB
    [int]$ValueSizeKB = 100
)

Write-Host ""
Write-Host "=== [SIM-04] Redis OOM (maxmemory exhaustion) ===" -ForegroundColor Yellow
Write-Host "    Inserting $KeyCount keys x ${ValueSizeKB}KB = $([math]::Round($KeyCount * $ValueSizeKB / 1024))MB (limit: 256MB)"
Write-Host ""

# Redis stats truoc
Write-Host "[before] Redis memory stats:" -ForegroundColor Cyan
docker exec aiops-redis redis-cli INFO memory 2>&1 | Select-String "used_memory_human|maxmemory_human"
docker exec aiops-redis redis-cli INFO stats  2>&1 | Select-String "evicted_keys"

# Tao value lon
$value = "X" * ($ValueSizeKB * 1024)

Write-Host ""
Write-Host "[fill] Inserting data into Redis..." -ForegroundColor Red

$batchSize = 100
$batches   = [math]::Ceiling($KeyCount / $batchSize)

for ($b = 0; $b -lt $batches; $b++) {
    $start = $b * $batchSize
    $end   = [math]::Min($start + $batchSize - 1, $KeyCount - 1)

    $pipeline = ($start..$end | ForEach-Object { "SET oom:flood:$_ $value EX 300" }) -join "`n"
    $pipeline | docker exec -i aiops-redis redis-cli --pipe 2>&1 | Out-Null

    if ($b % 5 -eq 0) {
        $pct     = [math]::Round(($b + 1) * 100 / $batches)
        $memLine = docker exec aiops-redis redis-cli INFO memory 2>&1 | Select-String "used_memory_human:"
        $mem     = if ($memLine) { ($memLine -split ":")[1].Trim() } else { "?" }
        $evLine  = docker exec aiops-redis redis-cli INFO stats 2>&1 | Select-String "evicted_keys:"
        $evicted = if ($evLine)  { ($evLine  -split ":")[1].Trim() } else { "0" }

        $evMsg = if ([int]$evicted -gt 0) { " *** EVICTION STARTED: evicted=$evicted ***" } else { "" }
        Write-Host "  $pct% -- $($start + $batchSize) keys -- mem=$mem$evMsg" -ForegroundColor $(if ($evMsg) {"Red"} else {"White"})
    }
}

# Stats sau khi fill
Write-Host ""
Write-Host "[after] Redis memory stats:" -ForegroundColor Magenta
docker exec aiops-redis redis-cli INFO memory 2>&1 | Select-String "used_memory_human|maxmemory_human"
docker exec aiops-redis redis-cli INFO stats  2>&1 | Select-String "evicted_keys|keyspace_hits|keyspace_misses"

# Cache miss storm demo
Write-Host ""
Write-Host "[impact] Forcing cache miss -- calling /api/users 5x without cache:" -ForegroundColor Yellow
1..5 | ForEach-Object {
    docker exec aiops-redis redis-cli DEL "users:all" | Out-Null
    try {
        $r    = Invoke-WebRequest -Uri "http://localhost:8080/api/users" -UseBasicParsing -TimeoutSec 5
        $body = $r.Content | ConvertFrom-Json
        Write-Host "  Call $_ : $($r.StatusCode) source=$($body.source)"
    } catch {
        Write-Host "  Call $_ : FAILED -- $($_.Exception.Message)" -ForegroundColor Red
    }
}

# Don dep
Write-Host ""
Write-Host "[cleanup] Removing flood keys..." -ForegroundColor Gray
$scanResult = docker exec aiops-redis redis-cli --scan --pattern "oom:flood:*" 2>&1
$scanResult | Where-Object { $_ -match "oom:flood:" } | ForEach-Object {
    docker exec aiops-redis redis-cli DEL $_.Trim() | Out-Null
}
Write-Host "          Done -- Redis back to normal"

Write-Host ""
Write-Host "[context] Redis logs for AIOps analysis:" -ForegroundColor Magenta
docker logs aiops-redis --tail 20 2>&1
Write-Host ""
