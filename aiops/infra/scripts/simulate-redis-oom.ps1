# simulate-redis-oom.ps1
# SIM-04: Redis dat maxmemory -> eviction -> cache miss storm
# Nhet du lieu vao Redis cho den khi cham gioi han 256MB.
# Redis se bat dau evict keys cu (allkeys-lru policy).
# Hau qua: cache miss -> node-api phai query postgres -> latency tang.

param(
    [int]$KeyCount    = 2800,   # ~2800 keys x 100KB ~ 280MB > maxmemory 256MB
    [int]$ValueSizeKB = 100
)

Write-Host ""
Write-Host "=== [SIM-04] Redis OOM (maxmemory exhaustion) ===" -ForegroundColor Yellow
Write-Host "    Inserting $KeyCount keys x ${ValueSizeKB}KB = $([math]::Round($KeyCount * $ValueSizeKB / 1024))MB (limit: 256MB)"
Write-Host ""

# Redis stats truoc
Write-Host "[before] Redis memory stats:" -ForegroundColor Cyan
docker exec aiops-redis redis-cli INFO memory 2>&1 | Select-String "used_memory_human|maxmemory_human"
docker exec aiops-redis redis-cli INFO stats  2>&1 | Select-String "evicted_keys"

# Tao value lon
$value = "X" * ($ValueSizeKB * 1024)

Write-Host ""
Write-Host "[fill] Inserting data into Redis..." -ForegroundColor Red

# Fill server-side bằng Lua (EVAL) — tin cậy trên Windows.
# KHÔNG dùng `... | docker exec -i ... --pipe`: PowerShell pipe stdin vào --pipe hỏng,
# lệnh SET không tới redis -> mem không tăng -> không evict.
$bytes = $ValueSizeKB * 1024
docker exec aiops-redis redis-cli EVAL "for i=1,$KeyCount do redis.call('SET','oom:flood:'..i,string.rep('X',$bytes)) end return redis.call('DBSIZE')" 0 | Out-Null

$evLine  = docker exec aiops-redis redis-cli INFO stats 2>&1 | Select-String "evicted_keys:"
$evicted = if ($evLine) { ($evLine -split ":")[1].Trim() } else { "0" }
$memLine = docker exec aiops-redis redis-cli INFO memory 2>&1 | Select-String "used_memory_human:"
$mem     = if ($memLine) { ($memLine -split ":")[1].Trim() } else { "?" }
Write-Host "  filled $KeyCount keys -- mem=$mem -- evicted=$evicted" -ForegroundColor $(if ([int]$evicted -gt 0) {"Red"} else {"White"})

# Stats sau khi fill
Write-Host ""
Write-Host "[after] Redis memory stats:" -ForegroundColor Magenta
docker exec aiops-redis redis-cli INFO memory 2>&1 | Select-String "used_memory_human|maxmemory_human"
docker exec aiops-redis redis-cli INFO stats  2>&1 | Select-String "evicted_keys|keyspace_hits|keyspace_misses"

# Cache miss storm demo
Write-Host ""
Write-Host "[impact] Forcing cache miss -- calling /api/users 5x without cache:" -ForegroundColor Yellow
1..5 | ForEach-Object {
    docker exec aiops-redis redis-cli DEL "users:all" | Out-Null
    try {
        $r    = Invoke-WebRequest -Uri "http://localhost:8080/api/users" -UseBasicParsing -TimeoutSec 5
        $body = $r.Content | ConvertFrom-Json
        Write-Host "  Call $_ : $($r.StatusCode) source=$($body.source)"
    } catch {
        Write-Host "  Call $_ : FAILED -- $($_.Exception.Message)" -ForegroundColor Red
    }
}

# KHONG don ngay: de loi "song" cho AnomalyDetector + Agent kip bat va dieu tra.
# Key co TTL EX 300 -> redis tu lanh sau ~5 phut.
Write-Host ""
Write-Host "[hold] OOM state LEFT ACTIVE (vĩnh viễn) so AIOps can detect + analyze." -ForegroundColor Red
Write-Host "       Recover khi xong: .\recover.ps1 redis-oom   (hoặc .\recover.ps1 all)" -ForegroundColor Gray

Write-Host ""
Write-Host "[context] Redis logs for AIOps analysis:" -ForegroundColor Magenta
docker logs aiops-redis --tail 20 2>&1
Write-Host ""
