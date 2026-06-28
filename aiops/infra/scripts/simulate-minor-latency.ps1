# simulate-minor-latency.ps1
# SIM-07 (P4 candidate): blip latency NHE - P99 chi HOI vuot nguong 2000ms roi tu hoi nhanh.
# Muc dich: tao bat thuong NHE & THOANG QUA de agent cham muc P4 (minor/informational),
# khac han SIM-03 high-latency nang (3000ms x25 -> P2/P3).
#
# Cach: ban IT request /api/slow?ms=2100 (chi nhinh hon nguong 2000 mot chut), concurrency THAP,
# giu VUA DU qua quiet-window (~30s) de alert dispatch roi DUNG -> P99 tut ngay -> RESOLVE.
# -> Evidence agent thay: P99 ~2.1s (sat nguong), it request bi anh huong, service UP, tu hoi
#    -> "minor / transient anomaly, no meaningful user impact" -> P4.
#
# LUU Y: severity DO AGENT cham theo bang chung (khong hard-code).
#   - Neu van ra P3 (agent thay "degradation"): ha nhe   ->  -Ms 2050 -Concurrency 6 -DurationSec 40
#   - Neu KHONG fire alert (P99 chua vuot 2000):  tang nhe ->  -Concurrency 16
#
# Usage:
#   .\simulate-minor-latency.ps1
#   .\simulate-minor-latency.ps1 -Ms 2100 -Concurrency 10 -DurationSec 45

param(
    [int]$Concurrency = 10,            # so request cham dong thoi (THAP -> tac dong nho)
    [int]$Ms          = 2050,          # chi nhinh hon nguong 2000 -> "barely over" -> minor (P99 thuc thap)
    [int]$DurationSec = 45,            # vua qua quiet-window(30s) roi dung -> thoang qua
    [string]$Base     = "http://localhost:8080"
)

$Target = "$Base/api/slow?ms=$Ms"

Write-Host ""
Write-Host "=== [SIM-07] Minor Latency Blip (P4 candidate): $Concurrency x slow(${Ms}ms) for ${DurationSec}s ===" -ForegroundColor DarkCyan
Write-Host "    Target: $Target  (P99 chi hoi vuot 2000ms -> bat thuong NHE, tu hoi nhanh)" -ForegroundColor Gray
Write-Host "    (Ctrl+C de dung som)" -ForegroundColor Gray
Write-Host ""

$deadline = (Get-Date).AddSeconds($DurationSec)
$round    = 0

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
Write-Host "=== [done] dung tai -> P99 tut ngay -> alert HIGH_LATENCY tu RESOLVE ===" -ForegroundColor Cyan
Write-Host "    Ky vong: agent cham P4 (minor/informational). Neu ra P3 -> ha -Ms/-Concurrency (xem header script)." -ForegroundColor Gray
Write-Host ""
