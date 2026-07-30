# Last Stardust - 클라이언트 지도 초기화
#
# 서버 월드를 리셋해도 지도는 안 지워진다. Xaero·Distant Horizons·FTB Chunks 는
# 탐험한 지형을 각자 PC 에 캐시해 두기 때문이다. 그대로 두면 새 월드를 돌아다닐 때
# 옛 지형 위에 새 지형이 겹쳐 그려져서 지도가 엉망이 된다.
#
# 이 스크립트는 .minecraft 폴더(인스턴스 폴더) 안에 두고 실행한다.
#
# 접속 주소마다 폴더 이름이 달라서(localhost / IP / 도메인) 자동으로 못 고른다.
# 다른 서버 지도까지 날리면 안 되므로 목록을 보여주고 직접 고르게 한다.

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

function Size-Of($path) {
    if (-not (Test-Path $path)) { return 0 }
    $s = Get-ChildItem $path -Recurse -File -ErrorAction SilentlyContinue | Measure-Object Length -Sum
    return [double]$s.Sum
}

function Show-Header($t) {
    Write-Host ""
    Write-Host "=== $t" -ForegroundColor Cyan
}

Write-Host ""
Write-Host "===============================================" -ForegroundColor Yellow
Write-Host "  Last Stardust - 클라이언트 지도 초기화" -ForegroundColor Yellow
Write-Host "===============================================" -ForegroundColor Yellow
Write-Host "  폴더: $root"

# ── 1. Xaero (월드맵 + 미니맵 웨이포인트) ──
# 두 폴더가 같은 이름 규칙을 쓰므로 한 번 고르면 양쪽을 같이 지운다.
$wm = Join-Path $root 'xaero\world-map'
$mm = Join-Path $root 'xaero\minimap'

if (-not (Test-Path $wm)) {
    Write-Host "`n  Xaero 지도 데이터가 없습니다. (xaero\world-map 없음)" -ForegroundColor DarkGray
} else {
    Show-Header "Xaero 지도 - 지울 서버를 고르세요"
    $dirs = @(Get-ChildItem $wm -Directory | Sort-Object Name)
    if ($dirs.Count -eq 0) {
        Write-Host "  비어 있습니다."
    } else {
        for ($i = 0; $i -lt $dirs.Count; $i++) {
            $mb = (Size-Of $dirs[$i].FullName) / 1MB
            "{0,3}) {1,-45} {2,8:N1} MB" -f ($i + 1), $dirs[$i].Name, $mb | Write-Host
        }
        Write-Host ""
        Write-Host "  Last Stardust 서버는 접속 주소로 이름이 붙습니다." -ForegroundColor DarkGray
        Write-Host "  (이 PC 에서 직접 돌리면 Multiplayer_localhost, 친구는 Multiplayer_<서버IP>)" -ForegroundColor DarkGray
        Write-Host ""
        $pick = Read-Host "  번호 (여러 개면 쉼표, 건너뛰려면 Enter)"

        if ($pick.Trim()) {
            foreach ($n in $pick -split ',') {
                $idx = 0
                if (-not [int]::TryParse($n.Trim(), [ref]$idx)) { continue }
                if ($idx -lt 1 -or $idx -gt $dirs.Count) { Write-Host "  범위 밖: $n" -ForegroundColor Red; continue }
                $name = $dirs[$idx - 1].Name
                foreach ($base in @($wm, $mm)) {
                    $t = Join-Path $base $name
                    if (Test-Path $t) {
                        [System.IO.Directory]::Delete($t, $true)
                        Write-Host "  지움: $(Split-Path $base -Leaf)\$name" -ForegroundColor Green
                    }
                }
            }
        } else {
            Write-Host "  건너뜀"
        }
    }
}

# ── 2. Distant Horizons ──
# 원경 LOD 다. 안 지우면 멀리 옛 지형이 유령처럼 남는다.
$dh = Join-Path $root 'Distant_Horizons_server_data'
if (Test-Path $dh) {
    Show-Header "Distant Horizons 원경 데이터"
    $dirs = @(Get-ChildItem $dh -Directory | Sort-Object Name)
    for ($i = 0; $i -lt $dirs.Count; $i++) {
        $mb = (Size-Of $dirs[$i].FullName) / 1MB
        "{0,3}) {1,-45} {2,8:N1} MB" -f ($i + 1), $dirs[$i].Name, $mb | Write-Host
    }
    if ($dirs.Count -gt 0) {
        Write-Host ""
        Write-Host "  서버 목록에 저장한 이름으로 붙습니다 (예: Last+Stardust)." -ForegroundColor DarkGray
        $pick = Read-Host "  번호 (여러 개면 쉼표, 건너뛰려면 Enter)"
        if ($pick.Trim()) {
            foreach ($n in $pick -split ',') {
                $idx = 0
                if (-not [int]::TryParse($n.Trim(), [ref]$idx)) { continue }
                if ($idx -lt 1 -or $idx -gt $dirs.Count) { continue }
                [System.IO.Directory]::Delete($dirs[$idx - 1].FullName, $true)
                Write-Host "  지움: $($dirs[$idx-1].Name)" -ForegroundColor Green
            }
        } else { Write-Host "  건너뜀" }
    }
}

# ── 3. FTB Chunks ──
# 폴더 이름이 UUID 라 어느 서버 것인지 사람이 못 고른다. 순수 지도 캐시라
# 지워도 다시 그려지므로 통째로 비울지만 묻는다.
$ftb = Join-Path $root 'local\ftbchunks\data'
if (Test-Path $ftb) {
    Show-Header "FTB Chunks 지도 캐시"
    $mb = (Size-Of $ftb) / 1MB
    "  {0:N1} MB  ({1}개 월드)" -f $mb, (@(Get-ChildItem $ftb -Directory).Count) | Write-Host
    Write-Host "  폴더 이름이 UUID 라 서버별로 못 고릅니다. 지도 캐시라 다시 그려집니다." -ForegroundColor DarkGray
    $yn = Read-Host "  전부 비울까요? (y/N)"
    if ($yn -match '^[yY]') {
        Get-ChildItem $ftb -Directory | ForEach-Object { [System.IO.Directory]::Delete($_.FullName, $true) }
        Write-Host "  지움: local\ftbchunks\data\*" -ForegroundColor Green
    } else { Write-Host "  건너뜀" }
}

Write-Host ""
Write-Host "  완료. 게임을 켜면 지도가 처음부터 다시 그려집니다." -ForegroundColor Yellow
Write-Host ""
Write-Host "  ※ 게임이 켜져 있으면 먼저 끄세요. 켠 채로 지우면 다시 저장되면서 살아납니다." -ForegroundColor DarkGray
Write-Host ""
Read-Host "  Enter 를 누르면 닫습니다"
