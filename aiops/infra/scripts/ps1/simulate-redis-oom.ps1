# simulate-redis-oom.ps1
# SIM: Redis cham maxmemory 256MB -> eviction (allkeys-lru) -> cache miss storm.
# Nhet keys lon vao redis cho vuot 256MB -> evicted_keys tang -> REDIS_OOM.
# Keys KHONG co TTL -> trang thai BEN, khong tu hoi.
# Recover: .\recover.ps1 redis-oom   (xoa oom:flood:* tren moi redis)
#
# Usage:
#   .\simulate-redis-oom.ps1                           # redis (mac dinh)
#   .\simulate-redis-oom.ps1 -Service redis-payments   # instance rieng
param(
    [ValidateSet("redis", "redis-payments", "redis-inventory")]
    [string]$Service = "redis",
    [int]$KeyCount = 2800, # ~2800 x 100KB ~ 280MB > maxmemory 256MB
    [int]$ValueSizeKB = 100
)

$container = "aiops-$Service"

Write-Host ""
Write-Host "=== [SIM] Redis OOM: $container ===" -ForegroundColor Yellow
Write-Host "    Chen $KeyCount keys x ${ValueSizeKB}KB ~ $([math]::Round($KeyCount * $ValueSizeKB / 1024))MB (limit 256MB)"
Write-Host ""

Write-Host "[before] Redis memory:" -ForegroundColor Cyan
docker exec $container redis-cli INFO memory 2>&1 | Select-String "used_memory_human|maxmemory_human"
docker exec $container redis-cli INFO stats  2>&1 | Select-String "evicted_keys"

# Fill server-side bang Lua (EVAL) -- tin cay tren Windows (KHONG dung --pipe).
Write-Host ""
Write-Host "[fill] dang nhet du lieu vao Redis..." -ForegroundColor Red
$bytes = $ValueSizeKB * 1024
docker exec $container redis-cli EVAL `
    "for i=1,$KeyCount do redis.call('SET','oom:flood:'..i,string.rep('X',$bytes)) end return redis.call('DBSIZE')" 0 | Out-Null

$evLine = docker exec $container redis-cli INFO stats 2>&1 | Select-String "evicted_keys:"
$evicted = if ($evLine) { ($evLine -split ":")[1].Trim() } else { "0" }
$memLine = docker exec $container redis-cli INFO memory 2>&1 | Select-String "used_memory_human:"
$mem = if ($memLine) { ($memLine -split ":")[1].Trim() } else { "?" }
Write-Host "  filled -- mem=$mem evicted=$evicted" -ForegroundColor $(if ([int]$evicted -gt 0) { "Red" } else { "White" })

Write-Host ""
Write-Host "[after] Redis stats:" -ForegroundColor Magenta
docker exec $container redis-cli INFO stats 2>&1 | Select-String "evicted_keys|keyspace_hits|keyspace_misses"

Write-Host ""
Write-Host "[hold] OOM DE NGUYEN (keys khong TTL) cho AIOps detect + analyze." -ForegroundColor Red
Write-Host "       Recover: .\recover.ps1 redis-oom" -ForegroundColor Gray
Write-Host ""
