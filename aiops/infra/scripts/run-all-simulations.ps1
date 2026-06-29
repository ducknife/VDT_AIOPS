# run-all-simulations.ps1
# Chay lan luot cac sim de demo pipeline AIOps end-to-end.
# Moi sim: chay -> PAUSE cho AIOps kip detect + dieu tra (loi con LIVE) -> RECOVER -> sim ke tiep.
# Container-down (kill nginx = entry point) chay CUOI cung.
#
# Usage:
#   .\run-all-simulations.ps1            # hoi Enter truoc khi bat dau
#   .\run-all-simulations.ps1 -Auto      # chay thang
#   .\run-all-simulations.ps1 -WaitSec 120
param(
    [int]$WaitSec = 90, # cho AIOps: detect(5s) + quiet-window(30s) + dieu tra
    [switch]$Auto
)

$Dir = $PSScriptRoot
function Sep { Write-Host ("=" * 60) -ForegroundColor DarkGray }
function Wait-AIOps([string]$next) {
    Sep
    Write-Host "Cho $WaitSec s cho AIOps xu ly (loi dang LIVE)... -> tiep: $next" -ForegroundColor DarkGray
    Sep
    Start-Sleep -Seconds $WaitSec
}

Sep
Write-Host "  VDT-AIOps -- Full Simulation Suite" -ForegroundColor Cyan
Sep
if (-not $Auto) { Read-Host "Nhan Enter de bat dau (dam bao stack dang chay)" | Out-Null }

# 1) Load spike (tu hoi khi het tai -> KHONG can recover)
Write-Host "`n>>> [1/4] load-spike" -ForegroundColor White
& "$Dir\simulate-load-spike.ps1" -Concurrency 30 -DurationSec 60
Wait-AIOps "redis-oom"

# 2) Redis OOM (persistent -> recover sau khi AIOps da phan tich)
Write-Host "`n>>> [2/4] redis-oom" -ForegroundColor White
& "$Dir\simulate-redis-oom.ps1" -KeyCount 2800
Wait-AIOps "db-exhaustion"
& "$Dir\recover.ps1" redis-oom

# 3) DB exhaustion (persistent -> recover)
Write-Host "`n>>> [3/4] db-exhaustion" -ForegroundColor White
& "$Dir\simulate-db-exhaustion.ps1" -BlockerConnections 85
Wait-AIOps "container-down"
& "$Dir\recover.ps1" db-exhaustion

# 4) Container down nginx (kill entry point -> chay CUOI)
Write-Host "`n>>> [4/4] container-down (nginx)" -ForegroundColor White
& "$Dir\simulate-container-down.ps1" -Service nginx
Wait-AIOps "ket thuc"
& "$Dir\recover.ps1" all   # an toan: heal tat ca

Sep
Write-Host "  Hoan tat -- da recover toan bo." -ForegroundColor Green
Sep
