# simulate-load-spike.ps1
# SIM-02: HTTP traffic dot bien -> error rate spike.
# Gui lien tuc request den /api/flaky -> node-api tra 500 -> error rate tang vot -> ERROR_RATE_SPIKE.
# Chay theo DEADLINE: alert chi dispatch khi song lien tuc >= quiet-window (30s),
# nen phai giu error rate cao >= ~40-45s thi investigate moi chay.
#
# Ban THANG vao port instance: node-api=3000 orders=3001 payments=3002 inventory=3003
#
# Usage:
#   .\simulate-load-spike.ps1
#   .\simulate-load-spike.ps1 -Service node-api-orders -Concurrency 30 -DurationSec 90
param(
    [ValidateSet("node-api", "node-api-orders", "node-api-payments", "node-api-inventory")]
    [string]$Service = "node-api",
    [int]$Concurrency = 30,            # so request dong thoi moi vong
    [int]$DurationSec = 60,            # tong thoi gian giu loi (giay) - phai > quiet-window(30s)
    [double]$Rate = 0.5               # ti le loi cua /api/flaky
)

$port = @{ "node-api" = 3000; "node-api-orders" = 3001; "node-api-payments" = 3002; "node-api-inventory" = 3003 }[$Service]
$Target = "http://localhost:$port/api/flaky?rate=$Rate"

Write-Host ""
Write-Host "=== [SIM-02] Load Spike on $Service : $Concurrency concurrent for ${DurationSec}s ===" -ForegroundColor Yellow
Write-Host "    Target: $Target" -ForegroundColor Gray
Write-Host "    (Ctrl+C de dung som - error rate se tu hoi -> alert RESOLVE)" -ForegroundColor Gray
Write-Host ""

$deadline = (Get-Date).AddSeconds($DurationSec)
$round = 0
$totalOk = 0
$totalErr = 0

while ((Get-Date) -lt $deadline) {
    $round++
    $procs = 1..$Concurrency | ForEach-Object {
        Start-Process -FilePath "curl.exe" `
            -ArgumentList "-s", "-o", "NUL", "-w", "%{http_code}`n", "--max-time", "10", $Target `
            -NoNewWindow -PassThru -RedirectStandardOutput "$env:TEMP\spike_$_.txt"
    }
    $procs | Wait-Process -ErrorAction SilentlyContinue

    $codes = 1..$Concurrency | ForEach-Object {
        $f = "$env:TEMP\spike_$_.txt"
        $c = (Get-Content $f -ErrorAction SilentlyContinue | Select-Object -First 1)
        Remove-Item $f -ErrorAction SilentlyContinue
        if ($c) { [int]$c } else { 0 }
    }

    $ok = ($codes | Where-Object { $_ -ge 200 -and $_ -lt 400 }).Count
    $err = ($codes | Where-Object { $_ -ge 400 -or $_ -eq 0 }).Count
    $totalOk += $ok
    $totalErr += $err

    $errRate = if (($ok + $err) -gt 0) { [math]::Round($err * 100 / ($ok + $err), 1) } else { 0 }
    $bar = "#" * [math]::Min($err, 30)
    $color = if ($errRate -gt 30) { "Red" } else { "White" }
    $left = [int](($deadline - (Get-Date)).TotalSeconds)

    Write-Host ("[round {0}] OK={1} ERR={2} error_rate={3}% {4} (~{5}s left)" -f `
            $round, $ok, $err, $errRate, $bar, $left) -ForegroundColor $color
}

Write-Host ""
Write-Host "=== Result ($Service) ===" -ForegroundColor Yellow
$finalRate = if (($totalOk + $totalErr) -gt 0) { [math]::Round($totalErr * 100 / ($totalOk + $totalErr), 1) } else { 0 }
Write-Host "  Total OK   : $totalOk"
Write-Host "  Total ERROR: $totalErr"
Write-Host "  Error rate : $finalRate%"
Write-Host ""
Write-Host "=== [done] het tai -> error rate tut -> AnomalyDetector se RESOLVE alert ===" -ForegroundColor Cyan
Write-Host ""
