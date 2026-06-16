# simulate-load-spike.ps1
# SIM-02: HTTP traffic dot bien -> error rate spike
# Gui lien tuc request den /api/flaky -> node-api tra 500 -> error rate tang vot.
#
# KHAC ban cu: chay theo DEADLINE (khong phai dem Rounds roi thoat).
# Ly do: alert chi dispatch khi song lien tuc >= quiet-window (30s).
# Burst chop nhoang ~20s se tu RESOLVE (active=false) truoc khi CorrelationTick kip gom.
# -> Phai giu error rate cao >= ~40-45s (detection lag + 30s quiet) thi investigate moi chay.
#
# Usage:
#   .\simulate-load-spike.ps1
#   .\simulate-load-spike.ps1 -Concurrency 30 -DurationSec 90

param(
    [int]$Concurrency = 30,            # so request dong thoi moi vong
    [int]$DurationSec = 60,            # tong thoi gian giu loi (giay) - phai > quiet-window(30s)
    [string]$Target   = "http://localhost:8080/api/flaky?rate=0.5"
)

Write-Host ""
Write-Host "=== [SIM-02] Load Spike: $Concurrency concurrent for ${DurationSec}s ===" -ForegroundColor Yellow
Write-Host "    Target: $Target" -ForegroundColor Gray
Write-Host "    (Ctrl+C de dung som - error rate se tu hoi -> alert RESOLVE)" -ForegroundColor Gray
Write-Host ""

$deadline = (Get-Date).AddSeconds($DurationSec)
$round    = 0
$totalOk  = 0
$totalErr = 0

while ((Get-Date) -lt $deadline) {
    $round++

    # Ban $Concurrency tien trinh curl nen -> in ra http_code de dem.
    $procs = 1..$Concurrency | ForEach-Object {
        Start-Process -FilePath "curl.exe" `
            -ArgumentList "-s", "-o", "NUL", "-w", "%{http_code}`n", "--max-time", "10", $Target `
            -NoNewWindow -PassThru -RedirectStandardOutput "$env:TEMP\spike_$_.txt"
    }
    $procs | Wait-Process -ErrorAction SilentlyContinue

    # Gom http_code tu cac file tam
    $codes = 1..$Concurrency | ForEach-Object {
        $f = "$env:TEMP\spike_$_.txt"
        $c = (Get-Content $f -ErrorAction SilentlyContinue | Select-Object -First 1)
        Remove-Item $f -ErrorAction SilentlyContinue
        if ($c) { [int]$c } else { 0 }
    }

    $ok  = ($codes | Where-Object { $_ -ge 200 -and $_ -lt 400 }).Count
    $err = ($codes | Where-Object { $_ -ge 400 -or  $_ -eq 0  }).Count
    $totalOk  += $ok
    $totalErr += $err

    $errRate = if (($ok + $err) -gt 0) { [math]::Round($err * 100 / ($ok + $err), 1) } else { 0 }
    $bar     = "#" * [math]::Min($err, 30)
    $color   = if ($errRate -gt 30) { "Red" } else { "White" }
    $left    = [int](($deadline - (Get-Date)).TotalSeconds)

    Write-Host ("[round {0}] OK={1} ERR={2} error_rate={3}% {4} (~{5}s left)" -f `
        $round, $ok, $err, $errRate, $bar, $left) -ForegroundColor $color
}

Write-Host ""
Write-Host "=== Result ===" -ForegroundColor Yellow
$finalRate = if (($totalOk + $totalErr) -gt 0) {
    [math]::Round($totalErr * 100 / ($totalOk + $totalErr), 1)
} else { 0 }
Write-Host "  Total OK   : $totalOk"
Write-Host "  Total ERROR: $totalErr"
Write-Host "  Error rate : $finalRate%"
Write-Host ""
Write-Host "=== [done] het tai -> error rate tut -> AnomalyDetector se RESOLVE alert ===" -ForegroundColor Cyan
Write-Host "  Soi log node-api: docker logs aiops-node-api --tail 40" -ForegroundColor Gray
Write-Host ""
