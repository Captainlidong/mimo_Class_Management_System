@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
set PORT=8080
rem 若 java 不在 PATH 中，请把下行改为 java.exe 的完整路径
set "JAVA=java"
rem 本机专属覆盖（可选，此文件不入库）：创建 start-backend.local.bat 并写入 set "JAVA=完整路径"
if exist "%~dp0start-backend.local.bat" call "%~dp0start-backend.local.bat"
set "JAR=%~dp0backend\target\class-member-backend-1.0.0.jar"
set "RUNJAR=%TEMP%\classmgmt-app.jar"

echo ============================================
echo   启动班级管理系统后端
echo ============================================

rem already running?
for /f "tokens=5" %%a in ('netstat -ano ^| findstr /R /C:":8080 " ^| findstr /I "LISTENING"') do (
  echo [提示] 后端已在运行 PID=%%a，无需重复启动。
  start "" http://127.0.0.1:8080
  pause
  exit /b 0
)

if not exist "%JAR%" (
  echo [错误] 未找到后端包：
  echo   %JAR%
  echo 请先构建：cd backend ^&^& mvn -DskipTests package
  pause
  exit /b 1
)

copy /Y "%JAR%" "%RUNJAR%" >nul

echo [1/2] 正在启动后端...
start "ClassMemberBackend" "%JAVA%" -Dfile.encoding=UTF-8 -jar "%RUNJAR%"

echo [2/2] 等待服务就绪...
set OK=0
for /L %%i in (1,1,20) do (
  timeout /t 1 /nobreak >nul
  netstat -ano | findstr /R /C:":8080 " | findstr /I "LISTENING" >nul
  if not errorlevel 1 (
    set OK=1
    goto :ready
  )
)
:ready
if "%OK%"=="1" (
  echo [OK] 后端已启动
  echo 正在把后台窗口最小化到任务栏...
  powershell -NoProfile -WindowStyle Hidden -Command "(New-Object -ComObject Shell.Application).Windows() | Where-Object {$_.LocationName -eq 'ClassMemberBackend'} | ForEach-Object {$_.Minimize()}"
  start "" http://127.0.0.1:8080
  echo.
  echo 后端已在最小化的 ClassMemberBackend 窗口里运行，平时不用管它。
  echo 本窗口现在可以关掉了（关掉不影响后端）。
  echo 要停止后端服务，请双击 stop-backend.bat。
) else (
  echo [警告] 20 秒内未检测到 8080，请查看 ClassMemberBackend 窗口里的报错。
)
echo.
echo 要关闭系统请运行 stop-backend.bat
pause
