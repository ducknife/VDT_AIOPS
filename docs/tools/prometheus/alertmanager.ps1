# alertmanager.ps1 - Chay thu Prometheus Alertmanager de doi chieu voi Duckompose.
# Muc dich: thay Alertmanager chi GOM + DINH TUYEN canh bao (theo nhan), KHONG dieu tra/chan doan.
#
# Cach dung (chay trong PowerShell):
#   .\alertmanager.ps1            # = start: chay container + ban 3 alert test + in cac nhom
#   .\alertmanager.ps1 groups     # xem lai cac nhom hien tai
#   .\alertmanager.ps1 stop       # xoa container khi xong
#
# UI xem truc quan (de chup anh): http://localhost:9093

param([ValidateSet('start', 'groups', 'stop')][string]$cmd = 'start')

$AM = 'http://localhost:9093'

function Show-Groups {
    Write-Host "`n--- Cac NHOM alert (group_by mac dinh = alertname) ---" -ForegroundColor Cyan
    Invoke-RestMethod -Uri "$AM/api/v2/alerts/groups" | ConvertTo-Json -Depth 8
}

function Start-AM {
    # 1) Chay container neu chua chay
    if (-not (docker ps -q -f name=try-alertmanager)) {
        Write-Host "[run] khoi dong Alertmanager..." -ForegroundColor Green
        docker run -d --name try-alertmanager -p 9093:9093 prom/alertmanager | Out-Null
        Start-Sleep 3
    }
    else {
        Write-Host "[run] container da chay san." -ForegroundColor Gray
    }

    # 2) Ban 3 alert test. Dat endsAt = +1 gio de KHONG tu het han
    #    (mac dinh khong co endsAt -> Alertmanager cho song 5 phut roi tu xoa).
    $end = (Get-Date).ToUniversalTime().AddHours(1).ToString("yyyy-MM-ddTHH:mm:ss.fff'Z'")
    $alerts = @(
        @{ endsAt = $end; labels = @{alertname = 'ServiceDown'; service = 'nginx'; severity = 'critical' }; annotations = @{summary = 'nginx is down' } },
        @{ endsAt = $end; labels = @{alertname = 'ServiceDown'; service = 'node-api'; severity = 'critical' }; annotations = @{summary = 'node-api is down' } },
        @{ endsAt = $end; labels = @{alertname = 'HighLatency'; service = 'node-api'; severity = 'warning' }; annotations = @{summary = 'P99 cao' } }
    )
    Invoke-RestMethod -Method Post -Uri "$AM/api/v2/alerts" -ContentType 'application/json' -Body (ConvertTo-Json $alerts -Depth 5) | Out-Null
    Start-Sleep 2
    Write-Host "[ok] da gui 3 alert (song 1 gio). Mo UI: $AM" -ForegroundColor Green

    # 3) In cac nhom -> ServiceDown gom 2 (nginx+node-api), HighLatency tach rieng 1.
    #    => gom theo NHAN tinh, KHONG theo do thi phu thuoc dich vu.
    Show-Groups
}

function Stop-AM {
    docker rm -f try-alertmanager | Out-Null
    Write-Host "[done] da xoa container try-alertmanager." -ForegroundColor Yellow
}

switch ($cmd) {
    'start' { Start-AM }
    'groups' { Show-Groups }
    'stop' { Stop-AM }
}
