# simulate-high-latency.ps1
# SIM-03: node-api CHAM (high latency P99). Danh vao /api/slow (cho BAT DONG BO)
# -> request cham nhung event loop VAN RANH -> /metrics & /health van tra loi -> up=1
# -> chi no HIGH_LATENCY, KHONG SERVICE_DOWN.
# Latency TU HOI khi dung script (het tai -> P99 tut -> RESOLVE).
#
# Ban THANG vao port cua instance (bypass nginx) de nham dung 1 node-api cu the.
#   node-api=3000  node-api-orders=3001  node-api-payments=3002  node-api-inventory=3003
#
# Usage:
#   .\simulate-high-latency.ps1
#   .\simulate-high-latency.ps1 -Service node-api-orders -Ms 4000 -Concurrency 30 -DurationSec 180
param(
    [ValidateSet("node-api", "node-api-orders", "node-api-payments", "node-api-inventory")]
    [string]$Service = "node-api",
    [int]$Concurrency = 25,            # so request cham dong thoi (curl nen)
    [int]$Ms = 3000,          # moi request cham bao nhieu ms (nguong P99 = 2000)
    [int]$DurationSec = 120            # tong thoi gian giu loi (giay)
)

$port = @{ "node-api" = 3000; "node-api-orders" = 3001; "node-api-payments" = 3002; "node-api-inventory" = 3003 }[$Service]
$Target = "http://localhost:$port/api/slow?ms=$Ms"

Write-Host ""
Write-Host "=== [SIM-03] High Latency P99 on $Service : $Concurrency x slow(${Ms}ms) for ${DurationSec}s ===" -ForegroundColor Yellow
Write-Host "    Target: $Target  (ban thang vao instance)" -ForegroundColor Gray
Write-Host "    (Ctrl+C de dung som - latency se tu hoi)" -ForegroundColor Gray
Write-Host ""

$deadline = (Get-Date).AddSeconds($DurationSec)
$round = 0

while ((Get-Date) -lt $deadline) {
    $round++
    $procs = @()
    for ($i = 0; $i -lt $Concurrency; $i++) {
        $procs += Start-Process -FilePath "curl.exe" `
            -ArgumentList "-s", "-o", "NUL", "--max-time", "60", $Target `
            -NoNewWindow -PassThru
    }
    $procs | Wait-Process -ErrorAction SilentlyContinue

    $left = [int](($deadline - (Get-Date)).TotalSeconds)
    Write-Host ("[round {0}] da ban {1} request cham (~{2}s left)" -f $round, $Concurrency, $left) -ForegroundColor Cyan
}

Write-Host ""
Write-Host "=== [done] het tai -> P99 tut -> AnomalyDetector se RESOLVE alert HIGH_LATENCY ($Service) ===" -ForegroundColor Cyan
Write-Host ""
