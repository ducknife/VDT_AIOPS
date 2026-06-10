# simulate-high-latency.ps1
# SIM-03: node-api CHAM (high latency P99) - KHAC voi error-rate spike.
# Danh vao /api/slow (cho BAT DONG BO) -> request cham nhung event loop VAN RANH
# -> /metrics & /health van tra loi -> up=1 -> chi no HIGH_LATENCY, KHONG SERVICE_DOWN.
#
# Dung tien trinh curl.exe chay NEN (nhe) de tao concurrency THAT.
# (Start-Job cua PowerShell qua nang -> khong giu noi du request cham dong thoi.)
#
# Metric: latencyMs = histogram_quantile(0.99, ...) -> can ty le request cham > 1% cua cua so 1m.
# Latency TU HOI khi dung script (het tai -> P99 tut -> dot quet sau RESOLVE).
#
# LUU Y: nginx phai UP (traffic di qua localhost:8080 -> nginx -> node-api).
#
# Usage:
#   .\simulate-high-latency.ps1
#   .\simulate-high-latency.ps1 -Ms 4000 -Concurrency 30 -DurationSec 180

param(
    [int]$Concurrency = 25,            # so request cham dong thoi (curl nen)
    [int]$Ms          = 3000,          # moi request cham bao nhieu ms (nguong P99 = 2000)
    [int]$DurationSec = 120,           # tong thoi gian giu loi (giay)
    [string]$Base     = "http://localhost:8080"
)

$Target = "$Base/api/slow?ms=$Ms"

Write-Host ""
Write-Host "=== [SIM-03] High Latency P99: $Concurrency concurrent x slow(${Ms}ms) for ${DurationSec}s ===" -ForegroundColor Yellow
Write-Host "    Target: $Target  (qua nginx -> node-api; nginx phai UP)" -ForegroundColor Gray
Write-Host "    (Ctrl+C de dung som - latency se tu hoi)" -ForegroundColor Gray
Write-Host ""

$deadline = (Get-Date).AddSeconds($DurationSec)
$round    = 0

while ((Get-Date) -lt $deadline) {
    $round++

    # Ban $Concurrency tien trinh curl nen -> request /api/slow chay song song that su.
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
Write-Host "=== [done] het tai -> P99 tut -> AnomalyDetector se RESOLVE alert HIGH_LATENCY ===" -ForegroundColor Cyan
Write-Host "    Soi P99 that (ms):" -ForegroundColor Gray
Write-Host "    curl.exe -s `"http://localhost:9090/api/v1/query?query=histogram_quantile(0.99,sum(rate(http_request_duration_seconds_bucket[1m]))by(le))*1000`"" -ForegroundColor Gray
Write-Host ""
