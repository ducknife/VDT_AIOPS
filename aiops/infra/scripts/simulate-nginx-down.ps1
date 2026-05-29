# simulate-nginx-down.ps1
# Simulate nginx crash, then collect logs/metrics from nginx and related
# services (node-api) -- this is the "context bundle" AIOps will build.

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== [SIMULATE] nginx DOWN scenario ===" -ForegroundColor Yellow
Write-Host ""

# Step 1: Baseline metrics before nginx dies
Write-Host "[1/5] Collecting nginx metrics before incident..." -ForegroundColor Cyan
try {
    $status = Invoke-WebRequest -Uri "http://localhost:8080/nginx_status" -UseBasicParsing -TimeoutSec 3
    Write-Host "  nginx_status (before):"
    $status.Content -split "`n" | ForEach-Object { Write-Host "    $_" }
} catch {
    Write-Host "  (not available -- no traffic yet)" -ForegroundColor Gray
}

# Step 2: Record incident timestamp
$incidentTime = Get-Date
Write-Host ""
Write-Host "[2/5] Stopping nginx at $incidentTime ..." -ForegroundColor Red
docker stop aiops-nginx | Out-Null
Write-Host "      aiops-nginx STOPPED"

# Step 3: Wait for traffic-gen to write error logs
Write-Host ""
Write-Host "[3/5] Waiting 10s for system to react..." -ForegroundColor Cyan
Start-Sleep -Seconds 10

# Step 4: Collect context bundle
Write-Host ""
Write-Host "[4/5] Collecting CONTEXT BUNDLE..." -ForegroundColor Cyan

Write-Host ""
Write-Host "  --- nginx logs (last 30 lines before crash) ---" -ForegroundColor Magenta
docker logs aiops-nginx --tail 30 2>&1

Write-Host ""
Write-Host "  --- node-api logs (upstream -- directly related) ---" -ForegroundColor Magenta
docker logs aiops-node-api --tail 20 2>&1

Write-Host ""
Write-Host "  --- traffic-gen logs (evidence: requests failing) ---" -ForegroundColor Magenta
docker logs aiops-traffic-gen --tail 15 2>&1

Write-Host ""
Write-Host "  --- Probe http://localhost:8080 with nginx down ---" -ForegroundColor Magenta
try {
    Invoke-WebRequest -Uri "http://localhost:8080/api/users" -UseBasicParsing -TimeoutSec 3 | Out-Null
    Write-Host "  200 OK (unexpected)"
} catch {
    Write-Host "  FAILED: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "  -> This is the anomaly signal AIOps uses to detect service-down"
}

# Step 5: Restart nginx
Write-Host ""
Write-Host "[5/5] Restarting nginx (remediation)..." -ForegroundColor Green
docker start aiops-nginx | Out-Null
Start-Sleep -Seconds 3
try {
    Invoke-WebRequest -Uri "http://localhost:8080/health" -UseBasicParsing -TimeoutSec 5 | Out-Null
    Write-Host "      nginx RECOVERED"
} catch {
    Write-Host "      nginx not yet responding -- wait a moment" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== Simulation complete ===" -ForegroundColor Green
Write-Host ""
Write-Host "Context bundle collected:"
Write-Host "  - nginx logs  : last lines before crash"
Write-Host "  - node-api    : upstream service (directly related)"
Write-Host "  - traffic-gen : request failure evidence"
Write-Host "  - metrics     : nginx_status (active connections)"
Write-Host ""
Write-Host "AIOps will send this bundle to Claude for root-cause analysis."
Write-Host ""
