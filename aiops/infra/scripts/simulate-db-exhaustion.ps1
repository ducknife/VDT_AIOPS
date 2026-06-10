# simulate-db-exhaustion.ps1
# SIM-03: PostgreSQL CAN KIET connection slot (server-side).
# Postgres max_connections = 100. Mo ~85 ket noi dai (pg_sleep) -> chiem >80% slot
# -> connUtilPercent > 80 -> DB_EXHAUSTION. Connection moi (ke ca node-api pool)
# bat dau bi tu choi -> 500 that.
#
# Recover: .\recover.ps1 db-exhaustion   (kill cac pg_sleep)

param(
    [int]$BlockerConnections = 85,   # +baseline ~2 -> ~88/100 slot -> vuot 80%, van con headroom cho exporter
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

# Blocker DETACHED (docker exec -d) + pg_sleep dài -> giữ connection VĨNH VIỄN,
# sống qua cả khi script thoát. Chỉ nhả khi .\recover.ps1 db-exhaustion
1..$BlockerConnections | ForEach-Object {
    docker exec -d aiops-postgres psql -U appuser -d appdb -c "SELECT pg_sleep(86400);" | Out-Null
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

# KHONG nha ngay: giu connection VINH VIEN cho AIOps detect + analyze
Write-Host ""
Write-Host "[hold] $BlockerConnections connections HELD (vĩnh viễn) so AIOps can detect + analyze." -ForegroundColor Red
Write-Host "       Recover khi xong: .\recover.ps1 db-exhaustion   (hoặc .\recover.ps1 all)" -ForegroundColor Gray
Write-Host ""
