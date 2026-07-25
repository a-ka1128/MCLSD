@echo off
setlocal
rem ============================================================
rem  Last Stardust 서버 백업 (world + config + kubejs + properties)
rem  - 더블클릭으로 실행. backups\ 폴더에 타임스탬프 zip 생성, 최근 14개 유지
rem  - 서버 실행 중에도 대체로 동작하지만, 콘솔에서 save-all 입력 후 실행 권장
rem    (서버가 꺼진 상태가 가장 안전)
rem ============================================================
set "SRC=%~dp0"
set "DEST=%SRC%backups"
set "STAGE=%TEMP%\ls_backup_stage"
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set "TS=%%i"
if not exist "%DEST%" mkdir "%DEST%"
if exist "%STAGE%" rmdir /s /q "%STAGE%"

echo [1/3] 파일 수집 중...
robocopy "%SRC%world" "%STAGE%\world" /MIR /XF session.lock /R:0 /W:0 /NFL /NDL /NJH /NJS >nul
robocopy "%SRC%config" "%STAGE%\config" /MIR /R:0 /W:0 /NFL /NDL /NJH /NJS >nul
robocopy "%SRC%kubejs" "%STAGE%\kubejs" /MIR /R:0 /W:0 /NFL /NDL /NJH /NJS >nul
copy /y "%SRC%server.properties" "%STAGE%\" >nul 2>nul

echo [2/3] 압축 중...
powershell -NoProfile -Command "Compress-Archive -Path '%STAGE%\*' -DestinationPath '%DEST%\backup_%TS%.zip' -CompressionLevel Optimal -Force"
rmdir /s /q "%STAGE%"

echo [3/3] 오래된 백업 정리 (최근 14개 유지)...
powershell -NoProfile -Command "Get-ChildItem '%DEST%\backup_*.zip' | Sort-Object LastWriteTime -Descending | Select-Object -Skip 14 | Remove-Item -Force"

echo.
echo  [Last Stardust] 백업 완료: %DEST%\backup_%TS%.zip
echo.
pause
