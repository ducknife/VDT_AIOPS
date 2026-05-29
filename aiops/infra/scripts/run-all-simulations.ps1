# run-all-simulations.ps1
# Chay toan bo 4 simulation lan luot de demo AIOps end-to-end pipeline.

param(
    [switch]$Auto   # bo qua Read-Host, chay tu dong
)

$ScriptDir = $PSScriptRoot

function Write-Sep { Write-Host ("=" * 60) -ForegroundColor DarkGray }

function Pause-Between {
    param([string]$Next, [int]$Secs = 10)
    Write-Sep
    Write-Host "Nghi $Secs giay truoc khi chay: $Next" -ForegroundColor DarkGray
    Write-Sep
    Start-Sleep -Seconds $Secs
}

Write-Sep
Write-Host "  VDT-AIOps -- Full Simulation Suite" -ForegroundColor Cyan
Write-Sep
Write-Host ""

if (-not $Auto) {
    Write-Host "Dam bao stack dang chay:"
    Write-Host "  docker compose -f $ScriptDir\..\docker-compose.yml ps"
    Write-Host ""
    Read-Host "Nhan Enter de bat dau"
}

# --- SIM-01: Container Down --------------------------------------------------
Write-Host ""
Write-Host ">>> [1/4] simulate-container-down (nginx)" -ForegroundColor White
& "$ScriptDir\simulate-container-down.ps1" -Service nginx

Pause-Between "Load Spike" -Secs 10

# --- SIM-02: Load Spike ------------------------------------------------------
Write-Host ""
Write-Host ">>> [2/4] simulate-load-spike" -ForegroundColor White
& "$ScriptDir\simulate-load-spike.ps1" -Concurrency 20 -Rounds 3

Pause-Between "DB Exhaustion" -Secs 10

# --- SIM-03: DB Exhaustion ---------------------------------------------------
Write-Host ""
Write-Host ">>> [3/4] simulate-db-exhaustion" -ForegroundColor White
& "$ScriptDir\simulate-db-exhaustion.ps1" -BlockerConnections 12 -BlockDurationSecs 20

Pause-Between "Redis OOM" -Secs 10

# --- SIM-04: Redis OOM -------------------------------------------------------
Write-Host ""
Write-Host ">>> [4/4] simulate-redis-oom" -ForegroundColor White
& "$ScriptDir\simulate-redis-oom.ps1" -KeyCount 2800

# --- Tong ket -----------------------------------------------------------------
Write-Host ""
Write-Sep
Write-Host "  Simulation hoan tat!" -ForegroundColor Green
Write-Sep
Write-Host ""
Write-Host "Kiem tra log tong hop:"
Write-Host "  docker compose -f $ScriptDir\..\docker-compose.yml logs --tail 50"
Write-Host ""
