# runsystem.ps1 - Chay thu OpenSRE (AI SRE agent ma nguon mo) qua Docker de doi chieu voi Duckompose.
#
# Boi canh: binary Windows cua OpenSRE hong + Python 3.14 khong hop litellm, nen ta chay bang Docker
# (Dockerfile tu pin dung Python). Image mac dinh chay webapp (uvicorn) va VO vi bug dat ten package
# 'platform' trung stdlib -> phai ep --entrypoint opensre de chay CLI. File tests/ khong nam trong image
# nen phai -v mount thu muc tests cua repo vao container.
#
# Cach dung (PowerShell, tai thu muc nay hoac bat ky):
#   .\runsystem.ps1 build                  # build image 'opensre' tu repo da clone
#   .\runsystem.ps1 list                   # liet ke cac file alert mau co the dieu tra
#   .\runsystem.ps1 investigate            # dieu tra alert mac dinh (datadog_k8s_alert.json)
#   .\runsystem.ps1 investigate -alert chaos_engineering/experiments/dns-error/dns-error-alert.json
#   .\runsystem.ps1 interactive            # che do tuong tac (giong chat cua Duckompose)
#
# Truoc khi investigate/interactive, dat key (chi ton tai trong RAM phien nay, KHONG luu file):
#   $env:ANTHROPIC_API_KEY = 'sk-ant-...'

param(
    [ValidateSet('build', 'list', 'investigate', 'interactive')][string]$cmd = 'investigate',
    [string]$alert = ''
)

# === Cau hinh: doi cho dung thu muc da clone OpenSRE tren may con ===
$REPO = 'D:\OpenSRETest\opensre'
$IMAGE = 'opensre'

if (-not (Test-Path $REPO)) {
    Write-Host "[loi] Khong thay repo OpenSRE tai: $REPO" -ForegroundColor Red
    Write-Host "      Sua bien `$REPO trong script cho dung, hoac clone:" -ForegroundColor Yellow
    Write-Host "      git clone https://github.com/Tracer-Cloud/opensre $REPO" -ForegroundColor Gray
    exit 1
}

# duong dan host -> dung dau '/' cho Docker Desktop mount
$mount = ($REPO -replace '\\', '/') + '/tests:/tests'

function Need-Key {
    if (-not $env:ANTHROPIC_API_KEY) {
        Write-Host "[loi] Chua co ANTHROPIC_API_KEY. Chay truoc:" -ForegroundColor Red
        Write-Host "      `$env:ANTHROPIC_API_KEY = 'sk-ant-...'" -ForegroundColor Gray
        exit 1
    }
}

function Run-Opensre([string[]]$cliArgs) {
    Need-Key
    docker run -it `
        -e LLM_PROVIDER=anthropic `
        -e ANTHROPIC_API_KEY=$env:ANTHROPIC_API_KEY `
        -v $mount `
        --entrypoint opensre $IMAGE @cliArgs
}

switch ($cmd) {
    'build' {
        Write-Host "[build] docker build -t $IMAGE $REPO" -ForegroundColor Green
        docker build -t $IMAGE $REPO
    }
    'list' {
        Write-Host "[list] Cac file alert mau (duong dan tuong doi tu tests/):" -ForegroundColor Cyan
        Get-ChildItem -Recurse -File -Filter '*alert*.json' (Join-Path $REPO 'tests') |
        ForEach-Object { '  ' + $_.FullName.Substring((Join-Path $REPO 'tests').Length + 1).Replace('\', '/') }
    }
    'investigate' {
        if ($alert) { $path = "/tests/$alert" } else { $path = '/tests/e2e/kubernetes/fixtures/datadog_k8s_alert.json' }
        Write-Host "[investigate] $path" -ForegroundColor Green
        Run-Opensre @('investigate', '-i', $path)
    }
    'interactive' {
        Write-Host "[interactive] che do tuong tac (Ctrl+C de thoat)" -ForegroundColor Green
        Run-Opensre @()
    }
}
