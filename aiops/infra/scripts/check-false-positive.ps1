# check-false-positive.ps1
# =============================================================================
# KIEM TRA FALSE POSITIVE  (do PRECISION cua he thong phat hien)
# -----------------------------------------------------------------------------
# Y NGHIA: Cac script simulate-* deu BOM LOI va ky vong he PHAI sinh alert (do
#   recall / do nhay). Script nay lam NGUOC LAI: KHONG bom loi nao ca, gui traffic
#   BINH THUONG (request 200 nhanh: /api/users + /health, tuyet doi khong /api/slow
#   hay /api/flaky) suot mot cua so quan sat DAI HON cua so phat hien (~35-45s).
#   Sau do doi chieu so alert & incident TRUOC vs SAU:
#     - KHONG doi  -> PASS: he KHOE ma detector KHONG bao dong gia => false positive = 0.
#     - Tang len   -> FAIL: co alert giA (in ra de dieu tra).
#   Day la bang chung RUNTIME cho precision (song song voi AnomalyRuleTest o tang unit).
# KHONG pha gi -> khong can recover.
#
# Usage:
#   .\check-false-positive.ps1
#   .\check-false-positive.ps1 -Duration 90
# =============================================================================
param(
    [int]$Duration = 60
)

$StateDb = "aiops-postgres-state"   # DB trang thai cua AIOps (bang alerts / incidents)
$Base = "http://localhost:8080"     # nginx -> node-api

# Dem so dong 1 bang trong state-DB (dung password tu chinh env cua container -> khong lo secret)
function Count-Rows([string]$table) {
    $sql = "SELECT count(*) FROM $table"
    $inner = 'PGPASSWORD=$POSTGRES_PASSWORD psql -U aiops -d aiopsdb -t -A -c "' + $sql + '"'
    $n = (docker exec $StateDb sh -c $inner 2>$null | Out-String).Trim()
    if ($n -match '^\d+$') { return [int]$n } else { return 0 }
}

Write-Host ""
Write-Host "=== [CHECK] False Positive / Precision (he KHOE, khong bom loi) ===" -ForegroundColor Cyan
Write-Host "    Quan sat ${Duration}s (dai hon cua so phat hien ~35-45s), gui traffic 200 binh thuong." -ForegroundColor DarkGray
Write-Host ""

$a0 = Count-Rows "alerts"; $i0 = Count-Rows "incidents"
Write-Host "[before] alerts=$a0  incidents=$i0" -ForegroundColor Cyan
Write-Host ""

Write-Host "[traffic] gui request KHOE (/api/users, /health) va cho detector..." -ForegroundColor DarkGray
$deadline = (Get-Date).AddSeconds($Duration)
while ((Get-Date) -lt $deadline) {
    $procs = @()
    1..5 | ForEach-Object {
        $procs += Start-Process curl.exe -ArgumentList "-s", "-o", "NUL", "--max-time", "5", "$Base/api/users" -NoNewWindow -PassThru
        $procs += Start-Process curl.exe -ArgumentList "-s", "-o", "NUL", "--max-time", "5", "$Base/health" -NoNewWindow -PassThru
    }
    $procs | Wait-Process -ErrorAction SilentlyContinue
    $left = [int](($deadline - (Get-Date)).TotalSeconds)
    Write-Host "  ...he van khoe (~${left}s left)"
    Start-Sleep -Seconds 3
}

$a1 = Count-Rows "alerts"; $i1 = Count-Rows "incidents"
$da = $a1 - $a0; $di = $i1 - $i0
Write-Host ""
Write-Host "[after]  alerts=$a1 (+$da)  incidents=$i1 (+$di)" -ForegroundColor Cyan
Write-Host ""

if ($da -eq 0 -and $di -eq 0) {
    Write-Host "[PASS] He KHOE -> detector KHONG sinh alert giA => false positive = 0 (precision OK)." -ForegroundColor Green
    Write-Host ""
    exit 0
}
else {
    Write-Host "[FAIL] Co $da alert / $di incident MOI du khong bom loi -> false positive!" -ForegroundColor Red
    $q = 'PGPASSWORD=$POSTGRES_PASSWORD psql -U aiops -d aiopsdb -c "SELECT id, service, type, detected_at FROM alerts ORDER BY id DESC LIMIT 10"'
    docker exec $StateDb sh -c $q 2>$null
    Write-Host ""
    exit 1
}
