@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
echo ============================================
echo   停止班级管理系统后端
echo ============================================
set FOUND=0

rem Kill any process listening on 8080 (IPv4/IPv6)
for /f "tokens=5" %%a in ('netstat -ano ^| findstr /R /C:":8080 " ^| findstr /I "LISTENING"') do (
  echo 发现占用 8080 的进程 PID=%%a，正在结束...
  taskkill /PID %%a /F >nul 2>&1
  if not errorlevel 1 (
    echo   已结束 PID %%a
  ) else (
    echo   结束失败，尝试 /T ...
    taskkill /PID %%a /F /T >nul 2>&1
  )
  set FOUND=1
)

rem Also kill ClassMemberBackend windows if any
taskkill /FI "WINDOWTITLE eq ClassMemberBackend*" /F >nul 2>&1

timeout /t 1 /nobreak >nul
netstat -ano | findstr /R /C:":8080 " | findstr /I "LISTENING" >nul
if errorlevel 1 (
  echo.
  echo [OK] 后端已停止，浏览器将无法打开系统（请勿再刷新成功页面）。
) else (
  echo.
  echo [警告] 8080 仍有进程占用，可能在 IDEA 中运行，请在 IDEA 点 Stop，或管理员运行本脚本。
  netstat -ano | findstr /R /C:":8080 " | findstr /I "LISTENING"
)
echo.
pause
