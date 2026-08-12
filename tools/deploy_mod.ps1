# lsrelics 를 빌드해서 **서버와 클라 양쪽에** 넣는다.
#
#   py 대신 PowerShell 인 이유: 클라 인스턴스 경로가 %APPDATA% 아래라 셸이 더 편하다.
#   실행:  powershell -ExecutionPolicy Bypass -File tools\deploy_mod.ps1
#
# ── 왜 이게 필요한가 ──
# 2026-08-13, 새 패킷 채널(lsrelics:relic_view)을 추가하고 서버에만 jar 을 넣었더니
# 접속이 **거부**됐다: 「서버에 필요한 네트워크 채널이 클라이언트에 존재하지 않습니다」.
# 그때 클라 jar 은 사흘 묵은 것이었다 — 서버만 갱신하는 습관이 그대로 쌓여 있었다.
#
# 패킷을 안 건드린 변경은 클라가 낡아도 «조용히» 굴러가서 더 위험하다.
# 그래서 한 번에 둘 다 넣는다.

$ErrorActionPreference = 'Stop'
$root   = Split-Path -Parent $PSScriptRoot
$jar    = Join-Path $root 'moddev\lsrelics\build\libs\lsrelics-1.0.0.jar'
$server = Join-Path $root 'server\mods\lsrelics-1.0.0.jar'
$client = "$env:APPDATA\PrismLauncher\instances\Last Stardust\minecraft\mods\lsrelics-1.0.0.jar"

Write-Host '── 빌드 ──' -ForegroundColor Cyan
Push-Location (Join-Path $root 'moddev\lsrelics')
try {
    & .\gradlew.bat build -q
    if ($LASTEXITCODE -ne 0) { throw "빌드 실패 (exit $LASTEXITCODE)" }
} finally { Pop-Location }

if (-not (Test-Path $jar)) { throw "빌드 산출물이 없다: $jar" }

Copy-Item $jar $server -Force
Write-Host "서버 ✔ $server" -ForegroundColor Green

# 클라 경로는 사람마다 다를 수 있다. 없으면 «조용히 건너뛰지» 않고 크게 말한다 —
# 그냥 넘어가면 다음 접속에서 또 채널 오류를 보게 된다.
if (Test-Path (Split-Path -Parent $client)) {
    Copy-Item $jar $client -Force
    Write-Host "클라 ✔ $client" -ForegroundColor Green
} else {
    Write-Host "클라 ✘ mods 폴더를 못 찾았다: $client" -ForegroundColor Red
    Write-Host "     인스턴스 이름이 바뀌었으면 이 스크립트의 `$client 를 고칠 것." -ForegroundColor Yellow
    exit 1
}

Write-Host ''
Write-Host '서버 재시작 + 클라 재시작이 필요하다 (패킷 채널은 접속할 때 맞춰본다).' -ForegroundColor Yellow
