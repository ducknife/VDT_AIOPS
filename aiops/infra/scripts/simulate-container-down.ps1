# simulate-container-down.ps1
# SIM: container bi stop dot ngot (crash / OOM-kill / process die) -> SERVICE_DOWN.
# De container DOWN cho AIOps phat hien + dieu tra (KHONG tu hoi).
# Recover: .\recover.ps1 container-down   (hoac: docker start aiops-<service>)
#
# Usage: .\simulate-container-down.ps1 [-Service nginx|node-api|postgres|redis]
param(
    [ValidateSet("nginx", "node-api", "postgres", "redis")]
    [string]$Service = "nginx"
)

$container = "aiops-$Service"
$Base = "http://localhost:8080"

Write-Host ""
Write-Host "=== [SIM] Container Down: $container ===" -ForegroundColor Red
Write-Host ""

Write-Host "[before] 10 dong log cuoi cua ${container}:" -ForegroundColor Cyan
docker logs $container --tail 10 2>&1

Write-Host ""
Write-Host "[kill] dang stop $container ..." -ForegroundColor Red
docker stop $container | Out-Null
Write-Host "       stopped luc $(Get-Date -Format 'HH:mm:ss')"

Write-Host ""
Write-Host "[probe] goi API sau khi $Service down:" -ForegroundColor Yellow
Start-Sleep -Seconds 2
1..4 | ForEach-Object {
    try {
        $r = Invoke-WebRequest -Uri "$Base/api/users" -UseBasicParsing -TimeoutSec 3
        Write-Host "  probe $_ : $($r.StatusCode) OK"
    }
    catch {
        Write-Host "  probe $_ : FAILED -- $($_.Exception.Message)" -ForegroundColor Red
    }
    Start-Sleep -Seconds 1
}

Write-Host ""
Write-Host "[context] node-api logs (lien quan):" -ForegroundColor Magenta
docker logs aiops-node-api --tail 15 2>&1

Write-Host ""
Write-Host "[hold] $container DE NGUYEN DOWN cho AIOps detect + analyze." -ForegroundColor Red
Write-Host "       Recover: .\recover.ps1 container-down" -ForegroundColor Gray
Write-Host ""
