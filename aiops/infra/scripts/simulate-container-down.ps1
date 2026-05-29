# simulate-container-down.ps1
# SIM-01: Container dot ngot bi kill (process crash, OOM kill, v.v.)
# Usage: .\simulate-container-down.ps1 [-Service nginx|node-api|postgres|redis]

param(
    [ValidateSet("nginx","node-api","postgres","redis")]
    [string]$Service = "nginx"
)

$container = "aiops-$Service"
$BASE      = "http://localhost:8080"

Write-Host ""
Write-Host "=== [SIM-01] Container Down: $container ===" -ForegroundColor Red
Write-Host ""

# Snapshot log truoc khi kill
Write-Host "[before] Last 10 log lines of $container :" -ForegroundColor Cyan
docker logs $container --tail 10 2>&1
Write-Host ""

# Kill container
Write-Host "[kill] Stopping $container ..." -ForegroundColor Red
docker stop $container | Out-Null
$killedAt = Get-Date
Write-Host "       Stopped at: $killedAt"

# Quan sat anh huong
Write-Host ""
Write-Host "[probe] Calling API after $Service is down..." -ForegroundColor Yellow
Start-Sleep -Seconds 2

1..4 | ForEach-Object {
    try {
        $r = Invoke-WebRequest -Uri "$BASE/api/users" -UseBasicParsing -TimeoutSec 3
        Write-Host "  Probe $_ : $($r.StatusCode) OK"
    } catch {
        Write-Host "  Probe $_ : FAILED -- $($_.Exception.Message)" -ForegroundColor Red
    }
    Start-Sleep -Seconds 1
}

# Log service lien quan
Write-Host ""
Write-Host "[context] node-api logs (upstream of nginx):" -ForegroundColor Magenta
docker logs aiops-node-api --tail 15 2>&1

Write-Host ""
Write-Host "[context] traffic-gen logs (request failure evidence):" -ForegroundColor Magenta
docker logs aiops-traffic-gen --tail 10 2>&1

# Restart
Write-Host ""
Write-Host "[recover] Restarting $container ..." -ForegroundColor Green
docker start $container | Out-Null
Start-Sleep -Seconds 3

try {
    Invoke-WebRequest -Uri "$BASE/health" -UseBasicParsing -TimeoutSec 5 | Out-Null
    Write-Host "          RECOVERED -- stack is responding" -ForegroundColor Green
} catch {
    Write-Host "          Not yet recovered -- check docker compose ps" -ForegroundColor Yellow
}
Write-Host ""
