@echo off
REM === Last Stardust 서버 실행 ===
REM
REM JVM 인자는 user_jvm_args.txt 에, 프로그램 인자는 아래 %* 앞에 붙인다.
REM
REM --- java 를 PATH 에서 찾지 않는 이유 ---
REM PATH 의 java 는 보통 C:\Program Files\Common Files\Oracle\Java\javapath\java.exe 이고
REM 그건 JDK 21 이 아니다. 그 상태로 실행하면 서버가 제대로 못 뜨면서도 프로세스는 남아,
REM JVM 이 두 개 뜬 채로 하나만 포트를 잡는 상황이 된다 (2026-07-30 실제 발생, 유령 프로세스 1개).
REM 그래서 경로를 직접 박고, 없으면 조용히 엉뚱한 java 로 넘어가지 말고 여기서 멈춘다.
REM
REM ※ 이 파일은 cp949 로 저장해야 한다. UTF-8 이면 cmd 가 위 한글 주석을 명령으로 파싱한다.
set "JAVA_EXE=C:\Program Files\Java\jdk-21.0.11\bin\java.exe"

if not exist "%JAVA_EXE%" (
  echo.
  echo [실패] JDK 21 을 찾을 수 없습니다:
  echo        %JAVA_EXE%
  echo.
  echo        JDK 를 새로 깔았다면 이 파일의 JAVA_EXE 경로를 고치세요.
  echo        설치된 목록: dir "C:\Program Files\Java"
  echo.
  pause
  exit /b 1
)

"%JAVA_EXE%" @user_jvm_args.txt @libraries/net/neoforged/neoforge/21.1.241/win_args.txt %*
pause
