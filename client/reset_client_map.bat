@echo off
REM Last Stardust - 클라이언트 지도 초기화 (실행용 껍데기)
REM
REM 실제 내용은 옆의 reset_client_map.ps1 에 있다. 목록을 보여주고 고르게 하는
REM 대화형이라 배치로 쓰면 지저분해진다.
REM
REM 쓰는 법: 이 파일 두 개를 .minecraft 폴더(인스턴스 폴더) 안에 넣고 더블클릭.
REM          xaero, Distant_Horizons_server_data, local 폴더가 있는 그 자리다.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0reset_client_map.ps1"
