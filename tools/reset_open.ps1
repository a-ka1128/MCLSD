param([switch]$Force, [switch]$SkipBackup, [switch]$PlayerOnly)

# Last Stardust — 오픈 직전 초기화 (백업 + 플레이 데이터 삭제) 한 번에.
#
#   실행:  powershell -ExecutionPolicy Bypass -File tools\reset_open.ps1
#
# ── 이건 reset_world.bat 이 «아니다» ──
# server\reset_world.bat 은 world 폴더를 통째로 옮겨서 «지형부터 새로» 만든다.
# 이 스크립트는 정반대다 — **지형·건축·NPC·전송석은 그대로 두고**
# 플레이 데이터만 지운다. 「내 계정도 남들과 똑같이 시작한다」를 만드는 절차.
#
# ── 왜 이게 필요한가 ──
# 2026-08-13 점검에서 firstSiegeDone=1 이 나왔다. 첫 공세가 이미 «소모된» 상태라
# 지금 그대로 열면 새로 들어온 사람이 별의 파편을 못 받고, 파편이 없으니
# 제단에 바칠 게 없어서 **아무도 유물을 못 연다.** day=23 · threat=5 도 같이 걸린다.
# 그 넷의 출처가 전부 data\laststardust.dat 하나라, 그 파일을 지우는 게 곧 해결이다.
#
# ── 되돌릴 수 없다 ──
# 그래서 백업이 «파일로 떨어진 것을 확인»하기 전에는 한 글자도 안 지운다.
# 2026-08-13 기준 server\backups\ 는 한 번도 생긴 적이 없었다 —
# 「백업은 돌고 있겠지」가 이 절차에서 제일 위험한 가정이다.

$ErrorActionPreference = 'Stop'

$root    = Split-Path -Parent $PSScriptRoot
$srv     = Join-Path $root 'server'
$world   = Join-Path $srv  'world'
$backups = Join-Path $srv  'backups'
$bat     = Join-Path $srv  'backup.bat'
$restore = Join-Path $PSScriptRoot 'reset_restore.txt'

function Say($t, $c) { Write-Host $t -ForegroundColor $c }

# ── ① 있어야 할 것들 ──
if (-not (Test-Path $world)) {
    Say "✘ world 폴더가 없다: $world" Red
    Say '   이미 reset_world.bat 으로 통째로 밀었거나 경로가 틀렸다.' Yellow
    exit 1
}

# 복구 목록이 없으면 «지우고 나서» 성역·제단 좌표를 되찾을 방법이 없다.
# laststardust.dat 안에만 있던 값이라 월드 어디에도 안 남는다. 여기서 막는다.
if (-not (Test-Path $restore)) {
    Say "✘ 복구 목록이 없다: $restore" Red
    Say '   이 파일이 성역·성벽·제단 좌표의 유일한 사본이다.' Yellow
    Say '   없이 지우면 열두 번 다시 찾아가 다시 찍어야 한다. 중단한다.' Yellow
    exit 1
}

# ── backup.bat 이 cp949 인가 ──
# 2026-08-13: backup.bat 이 UTF-8 로 저장돼 있어서 **한 번도 동작한 적이 없었다.**
# cmd 의 배치 파서는 바이트 오프셋으로 줄을 읽는데, 한글이 UTF-8(3바이트)로 들어 있으면
# 그걸 cp949(2바이트)로 세면서 어긋난다 — 뒷줄의 «앞 글자가 먹힌다».
#   'cho' is not recognized as an internal or external command
# 이게 `echo` 였다. 스크립트는 끝까지 실행되고 「완료」까지 찍는데 zip 은 안 생긴다.
# 조용한 실패라 「백업은 돌고 있겠지」로 몇 주가 지나갔다. 여기서 이름을 붙여 준다.
if (Test-Path $bat) {
    $bb = [IO.File]::ReadAllBytes($bat)
    $nonAscii = $false
    foreach ($b in $bb) { if ($b -gt 127) { $nonAscii = $true; break } }
    if ($nonAscii) {
        $strict = New-Object System.Text.UTF8Encoding($false, $true)
        $isUtf8 = $true
        try { [void]$strict.GetString($bb) } catch { $isUtf8 = $false }
        if ($isUtf8) {
            Say '✘ backup.bat 이 UTF-8 이다 — cmd 배치 파서가 한글에서 어긋난다.' Red
            Say '   「완료」까지 찍히는데 zip 은 안 생기는 조용한 실패다.' Yellow
            Say '   cp949(ANSI)로 다시 저장할 것. 아무것도 안 지웠다.' Yellow
            exit 1
        }
    }
}

# ── ② 서버가 돌고 있으면 안 된다 ──
# 켜진 채로 세이브 파일을 지우면 서버가 «메모리에 있는 옛 상태»를 그대로
# 다시 써 버려서, 지운 게 조용히 되살아난다. 그게 제일 나쁜 실패다 —
# 지운 줄 알고 오픈했는데 firstSiegeDone 이 그대로 1 이다.
$javaUp = Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -like '*CustomServer1*' -or $_.CommandLine -like '*nogui*' }

$lock = Join-Path $world 'session.lock'
$locked = $false
if (Test-Path $lock) {
    try { $fs = [IO.File]::Open($lock, 'Open', 'ReadWrite', 'None'); $fs.Close() }
    catch { $locked = $true }
}

if (($javaUp -or $locked) -and -not $Force) {
    Say '✘ 서버가 돌고 있다. 먼저 끄고 다시 실행할 것.' Red
    Say '   콘솔에 stop, 또는 tools\rcon.ps1 로 save-all flush 후 stop.' Yellow
    if ($locked) { Say '   (world\session.lock 이 잠겨 있다)' DarkGray }
    exit 1
}

# ── ③ 지울 것 / 남길 것 ──
$killDirs = @('playerdata', 'advancements', 'stats', 'deaths')
$killFiles = @(
    'kubejs_persistent_data.nbt',
    'data\laststardust.dat',
    'data\scoreboard.dat',
    'data\puffish_skills.dat',
    'data\perks.dat',
    'data\raids.dat'
)

# ── -PlayerOnly — 「세팅하러 들어갔던 흔적」만 지운다 ──
# 초기화 뒤에 성역·제단을 다시 등록하려면 OP 로 한 번 들어가야 하는데, 들어가는 순간
# **프롤로그가 소모된다** (`lsPrologueSeen` 이 playerdata 안에 있고, 첫 접속에만 뜬다).
# 그러면 「내 캐릭터도 처음 들어오는 사람과 똑같이」가 그 지점에서 깨진다.
#
# 그래서 등록을 끝낸 뒤 이 모드로 한 번 더 돈다 — 내 계정 기록만 지우고
# 방금 등록한 성역·제단(laststardust.dat)은 **손대지 않는다.**
# 가호는 그때 고른다. 세팅 중에 뜬 선택 화면은 ESC 로 닫아 둘 것.
if ($PlayerOnly) { $killFiles = @() }

Write-Host ''
if ($PlayerOnly) {
    Say '════ Last Stardust — 내 플레이 기록만 지우기 (-PlayerOnly) ════' Cyan
    Write-Host ''
    Say '  등록해 둔 성역·성벽·제단(laststardust.dat)은 그대로 둔다.' DarkGray
    Say '  프롤로그를 다시 볼 수 있게 계정 기록만 되돌린다.' DarkGray
    Write-Host ''
} else {
    Say '════ Last Stardust — 오픈 직전 초기화 ════' Cyan
    Write-Host ''
}
Say '  지운다 (플레이 데이터)' White
foreach ($d in $killDirs)  { Write-Host ("    world\{0}\" -f $d) -ForegroundColor DarkGray }
foreach ($f in $killFiles) { Write-Host ("    world\{0}"  -f $f) -ForegroundColor DarkGray }
Write-Host ''
Say '  남긴다 (지형·건축·설정)' White
Say '    region\ entities\ poi\ DIM-1\ DIM1\ level.dat' DarkGray
Say '    data\easy_npc_index.dat   ← 지우면 상인·린케우스가 통째로 사라진다' DarkGray
Say '    data\waystones.dat        ← 세워 둔 전송석' DarkGray
Say '    ops.json whitelist.json server.properties config\ mods\ kubejs\' DarkGray
Write-Host ''
Say '  되돌릴 수 없다. 백업이 실제로 떨어진 걸 확인한 뒤에만 지운다.' Yellow
Write-Host ''

if (-not $Force) {
    $ans = Read-Host '  진행하려면 RESET 을 그대로 입력'
    if ($ans -cne 'RESET') { Say '  중단했다. 아무것도 안 바뀌었다.' Green; exit 0 }
    Write-Host ''
}

# ── ④ 백업 — «새 zip 이 생겼는지»까지 본다 ──
# backup.bat 이 성공했다고 말하는 것과 파일이 생긴 것은 다른 일이다.
# robocopy 가 조용히 실패하거나 Compress-Archive 가 빈 zip 을 뱉을 수 있다.
if ($SkipBackup) {
    Say '⚠ -SkipBackup — 백업을 건너뛴다. 되돌릴 수 없다.' Red
} else {
    $before = $null
    if (Test-Path $backups) {
        $before = Get-ChildItem (Join-Path $backups 'backup_*.zip') -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
    }

    Say '── 백업 ──' Cyan
    # ⚠️ Push-Location 으로 cd 한 뒤 `cmd /c 'backup.bat'` 는 **안 된다.**
    # PowerShell 의 위치는 프로바이더 개념이라 네이티브 자식 프로세스가 안 물려받는다
    # (자식은 [Environment]::CurrentDirectory 를 본다). 「backup.bat 을 찾을 수 없다」로
    # 떨어진다. 전체 경로로 부른다.
    # `<nul` 은 backup.bat 끝의 pause 를 흘려보내는 용도다 — 없으면 여기서 영영 멈춘다.
    & cmd.exe /c "$bat <nul"

    $after = Get-ChildItem (Join-Path $backups 'backup_*.zip') -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1

    if (-not $after) {
        Say '✘ 백업 zip 이 안 생겼다. 아무것도 안 지웠다.' Red
        Say "   $backups 를 직접 확인할 것." Yellow
        exit 1
    }
    if ($before -and $after.FullName -eq $before.FullName) {
        Say '✘ 백업이 «새로» 안 생겼다 — 옛 zip 만 있다. 아무것도 안 지웠다.' Red
        Say ("   가장 최근: {0} ({1})" -f $after.Name, $after.LastWriteTime) Yellow
        exit 1
    }
    # 월드가 통째로 들어간 zip 이 1MB 를 밑돌 일은 없다. 밑돌면 수집이 실패한 것이다.
    if ($after.Length -lt 1MB) {
        Say ('✘ 백업 zip 이 너무 작다 ({0:N0} bytes). 수집이 실패했다.' -f $after.Length) Red
        exit 1
    }
    Say ('✔ 백업 {0}  ({1:N1} MB)' -f $after.Name, ($after.Length / 1MB)) Green
    Write-Host ''
}

# ── ⑤ laststardust.dat 은 원본도 따로 챙긴다 ──
# zip 안에도 들어 있지만, 되돌릴 때 zip 을 풀어 뒤지는 것보다 이게 빠르다.
# 「제단 좌표만 다시 보고 싶다」가 초기화 후 제일 흔한 요구다.
if (-not (Test-Path $backups)) { New-Item -ItemType Directory $backups | Out-Null }
$dat = Join-Path $world 'data\laststardust.dat'
if (Test-Path $dat) {
    $stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
    $side = Join-Path $backups ("laststardust_{0}.dat" -f $stamp)
    Copy-Item $dat $side -Force
    Say "✔ 원본 사본 $side" Green
} else {
    Say '· laststardust.dat 이 이미 없다 — 사본은 건너뛴다.' DarkGray
    Say "  복구 값은 $restore 에 있다." DarkGray
}

# ── ⑥ 삭제 ──
Write-Host ''
Say '── 삭제 ──' Cyan
$gone = 0; $absent = 0

foreach ($d in $killDirs) {
    $p = Join-Path $world $d
    if (Test-Path $p) {
        Remove-Item $p -Recurse -Force
        Say ("  ✔ world\{0}\" -f $d) Green; $gone++
    } else { Say ("  · world\{0}\ 없음" -f $d) DarkGray; $absent++ }
}

foreach ($f in $killFiles) {
    $p = Join-Path $world $f
    $hit = $false
    # .dat_old 는 마인크래프트가 남기는 «직전 세이브»다. 본체만 지우면
    # 서버가 그걸 읽어서 지운 상태가 그대로 되살아난다. 같이 지운다.
    foreach ($q in @($p, ($p + '_old'))) {
        if (Test-Path $q) { Remove-Item $q -Force; $hit = $true }
    }
    if ($hit) { Say ("  ✔ world\{0}" -f $f) Green; $gone++ }
    else { Say ("  · world\{0} 없음" -f $f) DarkGray; $absent++ }
}

Write-Host ''
Say ("삭제 {0}개 · 이미 없던 것 {1}개" -f $gone, $absent) White

# ── ⑦ 다음에 할 일 ──
Write-Host ''
if ($PlayerOnly) {
    Say '════ 끝. 이제 처음 들어오는 사람과 똑같다 ════' Cyan
    Write-Host ''
    Say '  서버를 켜고 접속하면 프롤로그부터 다시 흐른다.' Gray
    Say '  가호는 그때 고르면 된다 — 그게 진짜 첫 세션이다.' Gray
    Write-Host ''
    Say '  등록해 둔 성역·제단은 그대로다. /relic altar 로 한 번 확인할 것.' DarkGray
} else {
    Say '════ 서버를 켜고 아래를 다시 등록한다 ════' Cyan
    Write-Host ''
    Get-Content $restore -Encoding UTF8 | ForEach-Object {
        if ($_ -match '^\s*/') { Write-Host $_ -ForegroundColor Yellow }
        elseif ($_ -match '^──|^════') { Write-Host $_ -ForegroundColor Cyan }
        else { Write-Host $_ -ForegroundColor Gray }
    }
    Write-Host ''
    Say "이 목록은 $restore 에도 그대로 있다." DarkGray
    Write-Host ''
    Say '⚠ 등록하러 접속하면 프롤로그가 소모된다. 다 끝낸 뒤 서버를 끄고' Yellow
    Say '   tools\reset_open.ps1 -PlayerOnly 로 내 기록만 한 번 더 지울 것.' Yellow
    Say '   그때까지 가호 선택 화면은 ESC 로 닫아 둔다.' DarkGray
}
