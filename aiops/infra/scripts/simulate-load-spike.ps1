# simulate-load-spike.ps1
# SIM-02: HTTP traffic dot bien -> error rate spike
# Gui N request dong thoi -> node-api overload -> 500 errors tang vot

param(
    [int]$Concurrency = 30,
    [int]$Rounds      = 5,
    [string]$Target   = "http://localhost:8080/api/flaky?rate=0.5"
)

Write-Host ""
Write-Host "=== [SIM-02] Load Spike: $Concurrency concurrent x $Rounds rounds ===" -ForegroundColor Yellow
Write-Host "    Target: $Target"
Write-Host ""

$totalOk  = 0
$totalErr = 0

for ($round = 1; $round -le $Rounds; $round++) {
    Write-Host "[round $round/$Rounds] Firing $Concurrency concurrent requests..." -ForegroundColor Cyan

    $jobs = 1..$Concurrency | ForEach-Object {
        $url = $Target
        Start-Job -ScriptBlock {
            try {
                $r = Invoke-WebRequest -Uri $using:url -UseBasicParsing -TimeoutSec 10
                return $r.StatusCode
            } catch [System.Net.WebException] {
                if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
                return 0
            }
        }
    }

    $results  = $jobs | Wait-Job | Receive-Job
    $jobs | Remove-Job

    $ok      = ($results | Where-Object { $_ -ge 200 -and $_ -lt 400 }).Count
    $err     = ($results | Where-Object { $_ -ge 400 -or  $_ -eq 0  }).Count
    $totalOk  += $ok
    $totalErr += $err

    $errRate = if (($ok + $err) -gt 0) { [math]::Round($err * 100 / ($ok + $err), 1) } else { 0 }
    $bar     = "#" * [math]::Min($err, 30)
    $color   = if ($errRate -gt 30) { "Red" } else { "White" }

    Write-Host "         OK=$ok  ERR=$err  error_rate=$errRate%  $bar" -ForegroundColor $color
    Start-Sleep -Seconds 2
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
Write-Host "Check node-api logs to see [ERROR] spikes:"
Write-Host "  docker logs aiops-node-api --tail 40"
Write-Host ""
