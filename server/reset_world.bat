@echo off
setlocal EnableDelayedExpansion

REM =============================================================
REM  Last Stardust - 월드 완전 초기화
REM
REM  world 폴더 하나만 옮기면 끝난다. 리셋 대상이 전부 그 안에 있기 때문이다:
REM    지형/건축              region, DIM-1, DIM1, entities, poi
REM    플레이어 데이터        playerdata, stats, advancements, deaths
REM    스킬트리/경제          data\puffish_skills.dat, SDMEconomy, SDMShopData
REM    마을/공성 상태         kubejs_persistent_data.nbt, data\laststardust.dat
REM    FTB 팀/퀘스트/청크     ftbteams, ftbquests, ftbchunks
REM    NPC/지도/업적          easy_npc, data\map_*.dat
REM
REM  서버 루트에 남는 것 (리셋되면 안 되는 것들):
REM    ops.json, whitelist.json, banned-*.json    운영 권한과 접근 제어
REM    server.properties, config\, defaultconfigs\
REM    mods\, kubejs\ (스크립트는 소스다)
REM
REM  지우지 않고 world_backup_날짜 로 이름만 바꾼다. 30MB 남짓이라 비용이 거의 없고,
REM  잘못 눌렀을 때 되돌릴 수 있는 게 훨씬 중요하다. 백업이 쌓이면 직접 지우면 된다.
REM
REM  ※ 이 파일은 cp949(한국어 윈도 기본)로 저장해야 한다. UTF-8 로 두면 cmd 파서가
REM    한글 줄을 명령으로 잘못 읽어서, 취소를 눌러도 뒷부분이 실행된다.
REM =============================================================

cd /d "%~dp0"

if not exist "world\" (
    echo world 폴더가 없습니다. 이미 초기화된 상태입니다.
    echo 서버를 켜면 새 월드가 생성됩니다.
    pause
    exit /b 0
)

REM -- 서버가 켜져 있으면 절대 진행하지 않는다 --
REM 켜진 채로 월드를 옮기면 서버가 반쯤 지워진 월드에 계속 쓰면서 손상된다.
REM
REM 폴더 이름을 잠깐 바꿔본다. 안에 열린 파일이 하나라도 있으면 실패하므로
REM 서버 실행 여부를 그대로 알 수 있고, 성공하면 곧바로 되돌린다.
REM
REM ※ session.lock 에 써보는 방식은 쓰지 않는다. 두 가지가 위험하다 -
REM   파일이 없으면 append 가 새로 만들어버려서 켜져 있어도 통과하고,
REM   써지는 경우엔 서버가 들고 있는 락 내용을 건드린다.
set "LOCKED=1"
2>nul (
    ren "world" "world_lockcheck_tmp"
) && (
    ren "world_lockcheck_tmp" "world"
    set "LOCKED=0"
)

if "!LOCKED!"=="1" (
    echo.
    echo   [중단] 서버가 실행 중입니다.
    echo.
    echo   먼저 서버를 정상 종료하세요. 콘솔에 stop 을 치거나
    echo   RCON 으로 save-all flush 뒤 stop 을 보내면 됩니다.
    echo.
    pause
    exit /b 1
)

echo.
echo ===============================================
echo   월드를 초기화합니다
echo ===============================================
echo.
echo   지워지는 것 : 지형, 건축물, 플레이어 인벤토리/위치,
echo                 스킬트리, 경제, 마을/공성 진행도,
echo                 FTB 팀/퀘스트, NPC, 지도, 업적, 통계
echo.
echo   남는 것     : ops.json, whitelist, 밴 목록,
echo                 server.properties, config, mods, kubejs 스크립트
echo.
echo   현재 월드는 지워지지 않고 world_backup_날짜 로 이름만 바뀝니다.
echo.
set "CONFIRM="
set /p "CONFIRM=  진행하려면 RESET 을 그대로 입력하세요: "

if /i not "!CONFIRM!"=="RESET" (
    echo.
    echo   취소했습니다. 아무것도 바뀌지 않았습니다.
    pause
    exit /b 0
)

REM -- 백업 이름. 날짜 형식이 로케일을 타므로 PowerShell 로 만든다 --
set "STAMP="
for /f %%t in ('powershell -NoProfile -Command "Get-Date -Format yyyy-MM-dd_HHmm"') do set "STAMP=%%t"
if "!STAMP!"=="" (
    echo   [실패] 날짜를 만들지 못했습니다.
    pause
    exit /b 1
)
set "DEST=world_backup_!STAMP!"

if exist "!DEST!\" (
    echo   같은 이름의 백업이 이미 있습니다: !DEST!
    echo   1분 뒤에 다시 실행하거나 그 폴더를 옮겨주세요.
    pause
    exit /b 1
)

echo.
echo   world  ^-^>  !DEST!
move "world" "!DEST!" >nul
if errorlevel 1 (
    echo.
    echo   [실패] 폴더를 옮기지 못했습니다.
    echo   탐색기나 다른 프로그램이 world 폴더를 열고 있는지 확인하세요.
    pause
    exit /b 1
)

echo.
echo   완료. 서버를 켜면 새 월드가 생성됩니다.
echo.
echo   참고
echo     - 시드는 server.properties 의 level-seed 를 따릅니다 (비어 있으면 무작위)
echo     - 백업이 쌓이면 world_backup_* 폴더를 직접 지우면 됩니다
echo     - 유물/가호는 처음부터 다시 받아야 합니다 (/relic, /fate)
echo     - 성역도 다시 지정해야 합니다 (/sanctuary here)
echo.
pause
