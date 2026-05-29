# simulate-db-exhaustion.ps1
# SIM-03: PostgreSQL connection pool bi can kiet
# Mo 15 ket noi dai (pg_sleep) vao postgres, nhieu hon node-api pool max (10).
# Khi node-api co lay connection -> timeout -> 500 Internal Server Error.

param(
    [int]$BlockerConnections = 15,
    [int]$BlockDurationSecs  = 25
)

Write-Host ""
Write-Host "=== [SIM-03] DB Connection Exhaustion ===" -ForegroundColor Yellow
Write-Host "    Opening $BlockerConnections connections for $BlockDurationSecs s (node-api pool max=10)"
Write-Host ""

# Baseline: dem connections hien tai
Write-Host "[before] Active connections to postgres:" -ForegroundColor Cyan
docker exec aiops-postgres psql -U appuser -d appdb -c `
    "SELECT count(*) AS active_conn FROM pg_stat_activity WHERE datname='appdb';" 2>&1

# Mo blocker connections song song
Write-Host ""
Write-Host "[exhaust] Opening $BlockerConnections long-running connections (pg_sleep)..." -ForegroundColor Red

$blockers = 1..$BlockerConnections | ForEach-Object {
    $n   = $_
    $dur = $BlockDurationSecs
    Start-Job -ScriptBlock {
        docker exec aiops-postgres psql -U appuser -d appdb `
            -c "SELECT pg_sleep($using:dur), $using:n AS blocker_id;" 2>&1
    }
}

Start-Sleep -Seconds 2

# Kiem tra connections dang giu
Write-Host ""
Write-Host "[during] Active connections while exhausted:" -ForegroundColor Cyan
docker exec aiops-postgres psql -U appuser -d appdb -c `
    "SELECT count(*) AS active_conn FROM pg_stat_activity WHERE datname='appdb';" 2>&1

# Thu goi API (se fail vi pool can)
Write-Host ""
Write-Host "[probe] Calling /api/users while pool is exhausted..." -ForegroundColor Yellow
1..5 | ForEach-Object {
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:8080/api/users" -UseBasicParsing -TimeoutSec 5
        Write-Host "  Probe $_ : $($r.StatusCode) OK (pool not full yet)"
    } catch {
        Write-Host "  Probe $_ : FAILED -- $($_.Exception.Message)" -ForegroundColor Red
    }
    Start-Sleep -Milliseconds 500
}

# Log de AIOps phan tich
Write-Host ""
Write-Host "[context] node-api logs (look for connection timeout):" -ForegroundColor Magenta
Start-Sleep -Seconds 3
docker logs aiops-node-api --tail 20 2>&1

Write-Host ""
Write-Host "[context] postgres logs:" -ForegroundColor Magenta
docker logs aiops-postgres --tail 15 2>&1

# Cho blocker tu giai phong
Write-Host ""
Write-Host "[wait] Waiting $BlockDurationSecs s for blocker connections to close..." -ForegroundColor Gray
$blockers | Wait-Job | Out-Null
$blockers | Remove-Job

Write-Host ""
Write-Host "[after] Connections after release:" -ForegroundColor Green
docker exec aiops-postgres psql -U appuser -d appdb -c `
    "SELECT count(*) AS active_conn FROM pg_stat_activity WHERE datname='appdb';" 2>&1

Write-Host ""
Write-Host "[verify] Calling API again (should be OK):" -ForegroundColor Green
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8080/api/users" -UseBasicParsing -TimeoutSec 5
    Write-Host "  $($r.StatusCode) -- RECOVERED" -ForegroundColor Green
} catch {
    Write-Host "  $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""
