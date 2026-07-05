# simulate-minor-latency.ps1
# SIM-07 (P4 candidate): blip latency NHE - P99 chi HOI vuot nguong 2000ms roi tu hoi nhanh.
# Muc dich: tao bat thuong NHE & THOANG QUA de agent cham muc P4 (minor/informational).
# Cach: ban IT request /api/slow?ms=~2050 (nhinh hon nguong), concurrency THAP, giu vua qua
# quiet-window (~30s) roi DUNG -> P99 tut ngay -> RESOLVE.
#   -> severity DO AGENT cham theo bang chung (khong hard-code).
#      Neu ra P3: ha -Ms 2050 -Concurrency 6 ; neu khong fire: tang -Concurrency 16
#
# Ban THANG vao port instance: node-api=3000 orders=3001 payments=3002 inventory=3003
#
# Usage:
#   .\simulate-minor-latency.ps1
#   .\simulate-minor-latency.ps1 -Service node-api-payments -Ms 2100 -Concurrency 10 -DurationSec 45
param(
    [ValidateSet("node-api", "node-api-orders", "node-api-payments", "node-api-inventory")]
    [string]$Service = "node-api",
    [int]$Concurrency = 10,            # THAP -> tac dong nho
    [int]$Ms = 2050,          # chi nhinh hon nguong 2000 -> "barely over"
    [int]$DurationSec = 45             # vua qua quiet-window(30s) roi dung -> thoang qua
)

$port = @{ "node-api" = 3000; "node-api-orders" = 3001; "node-api-payments" = 3002; "node-api-inventory" = 3003 }[$Service]
$Target = "http://localhost:$port/api/slow?ms=$Ms"

Write-Host ""
Write-Host "=== [SIM-07] Minor Latency Blip on $Service (P4 candidate): $Concurrency x slow(${Ms}ms) for ${DurationSec}s ===" -ForegroundColor DarkCyan
Write-Host "    Target: $Target  (P99 chi hoi vuot 2000ms -> bat thuong NHE, tu hoi nhanh)" -ForegroundColor Gray
Write-Host ""

$deadline = (Get-Date).AddSeconds($DurationSec)
$round = 0

while ((Get-Date) -lt $deadline) {
    $round++
    $procs = @()
    for ($i = 0; $i -lt $Concurrency; $i++) {
        $procs += Start-Process -FilePath "curl.exe" `
            -ArgumentList "-s", "-o", "NUL", "--max-time", "30", $Target `
            -NoNewWindow -PassThru
    }
    $procs | Wait-Process -ErrorAction SilentlyContinue

    $left = [int](($deadline - (Get-Date)).TotalSeconds)
    Write-Host ("[round {0}] ban {1} request cham nhe (~{2}s left)" -f $round, $Concurrency, $left) -ForegroundColor Cyan
}

Write-Host ""
Write-Host "=== [done] dung tai -> P99 tut ngay -> alert HIGH_LATENCY tu RESOLVE ($Service) ===" -ForegroundColor Cyan
Write-Host "    Ky vong: agent cham P4 (minor). Neu ra P3 -> ha -Ms/-Concurrency." -ForegroundColor Gray
Write-Host ""
