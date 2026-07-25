@echo off
rem === lsrelics 리빌드 + 서버/클라 설치 (Blockbench 수정 후 더블클릭) ===
cd /d D:\Study\MC\CustomServer1\moddev\lsrelics
set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.11"
call gradlew.bat build --no-daemon
if errorlevel 1 (
  echo.
  echo [실패] 빌드 에러 - 위 로그 확인
  pause
  exit /b 1
)
copy /y build\libs\lsrelics-1.0.0.jar "D:\Study\MC\CustomServer1\server\mods\" >nul
copy /y build\libs\lsrelics-1.0.0.jar "C:\Users\user\AppData\Roaming\PrismLauncher\instances\Last Stardust\minecraft\mods\" >nul
echo.
echo [완료] 서버 + 클라 mods 설치됨. 클라이언트 완전 재시작하면 반영!
pause
