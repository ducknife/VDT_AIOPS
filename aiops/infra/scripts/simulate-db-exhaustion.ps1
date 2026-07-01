# simulate-db-exhaustion.ps1
# SIM: PostgreSQL can kiet connection slot.
# max_connections=100. Mo ~85 connection dai (pg_sleep) -> chiem >80% slot
# -> connUtilPercent > 80 -> DB_EXHAUSTION.
# Connection giu VINH VIEN (docker exec -d + pg_sleep dai) -> song qua ca khi script thoat.
# Recover: .\recover.ps1 db-exhaustion   (kill pg_sleep tren moi postgres)
#
# Usage:
#   .\simulate-db-exhaustion.ps1                              # postgres (mac dinh)
#   .\simulate-db-exhaustion.ps1 -Service postgres-payments  # instance rieng
param(
    [ValidateSet("postgres", "postgres-payments", "postgres-inventory")]
    [string]$Service = "postgres",
    [int]$BlockerConnections = 85   # >80/100 slot de vuot nguong
)

$container = "aiops-$Service"
$db = @{ "postgres" = "appdb"; "postgres-payments" = "paymentsdb"; "postgres-inventory" = "inventorydb" }[$Service]

Write-Host ""
Write-Host "=== [SIM] DB Connection Exhaustion: $container ($db) ===" -ForegroundColor Yellow
Write-Host "    Mo $BlockerConnections connection dai (pg_sleep)"
Write-Host ""

Write-Host "[before] So connection hien tai:" -ForegroundColor Cyan
docker exec $container psql -U appuser -d $db -c `
    "SELECT count(*) AS active_conn FROM pg_stat_activity WHERE datname='$db';" 2>&1

Write-Host ""
Write-Host "[exhaust] mo $BlockerConnections connection dai (pg_sleep, detached)..." -ForegroundColor Red
1..$BlockerConnections | ForEach-Object {
    docker exec -d $container psql -U appuser -d $db -c "SELECT pg_sleep(86400);" | Out-Null
}
Start-Sleep -Seconds 2

Write-Host ""
Write-Host "[during] So connection khi dang can kiet:" -ForegroundColor Cyan
docker exec $container psql -U appuser -d $db -c `
    "SELECT count(*) AS active_conn FROM pg_stat_activity WHERE datname='$db';" 2>&1

Write-Host ""
Write-Host "[hold] $BlockerConnections connection DUOC GIU cho AIOps detect + analyze." -ForegroundColor Red
Write-Host "       Recover: .\recover.ps1 db-exhaustion" -ForegroundColor Gray
Write-Host ""
