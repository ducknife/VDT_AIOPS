# simulate-db-exhaustion.ps1
# SIM: PostgreSQL can kiet connection slot.
# max_connections=100. Mo ~85 connection dai (pg_sleep) -> chiem >80% slot
# -> connUtilPercent > 80 -> DB_EXHAUSTION. Connection moi (ke ca node-api pool max=10) bi tu choi.
# Connection giu VINH VIEN (docker exec -d + pg_sleep dai) -> song qua ca khi script thoat.
# Recover: .\recover.ps1 db-exhaustion   (kill cac pg_sleep)
param(
    [int]$BlockerConnections = 85   # >80/100 slot de vuot nguong, nhung chua dung 100 (chua het exporter)
)

$Base = "http://localhost:8080"

Write-Host ""
Write-Host "=== [SIM] DB Connection Exhaustion ===" -ForegroundColor Yellow
Write-Host "    Mo $BlockerConnections connection (node-api pool max=10)"
Write-Host ""

Write-Host "[before] So connection hien tai toi postgres:" -ForegroundColor Cyan
docker exec aiops-postgres psql -U appuser -d appdb -c `
    "SELECT count(*) AS active_conn FROM pg_stat_activity WHERE datname='appdb';" 2>&1

Write-Host ""
Write-Host "[exhaust] mo $BlockerConnections connection dai (pg_sleep, detached)..." -ForegroundColor Red
1..$BlockerConnections | ForEach-Object {
    docker exec -d aiops-postgres psql -U appuser -d appdb -c "SELECT pg_sleep(86400);" | Out-Null
}
Start-Sleep -Seconds 2

Write-Host ""
Write-Host "[during] So connection khi dang can kiet:" -ForegroundColor Cyan
docker exec aiops-postgres psql -U appuser -d appdb -c `
    "SELECT count(*) AS active_conn FROM pg_stat_activity WHERE datname='appdb';" 2>&1

Write-Host ""
Write-Host "[probe] goi /api/users khi pool can:" -ForegroundColor Yellow
1..5 | ForEach-Object {
    try {
        $r = Invoke-WebRequest -Uri "$Base/api/users" -UseBasicParsing -TimeoutSec 5
        Write-Host "  probe $_ : $($r.StatusCode) OK (pool chua day)"
    }
    catch {
        Write-Host "  probe $_ : FAILED -- $($_.Exception.Message)" -ForegroundColor Red
    }
    Start-Sleep -Milliseconds 500
}

Write-Host ""
Write-Host "[context] node-api logs (tim connection timeout):" -ForegroundColor Magenta
Start-Sleep -Seconds 3
docker logs aiops-node-api --tail 20 2>&1

Write-Host ""
Write-Host "[hold] $BlockerConnections connection DUOC GIU cho AIOps detect + analyze." -ForegroundColor Red
Write-Host "       Recover: .\recover.ps1 db-exhaustion" -ForegroundColor Gray
Write-Host ""
