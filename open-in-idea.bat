@echo off
set IDEA_EXE=D:\CS\Idea\IntelliJ IDEA 2022.3.2\bin\idea64.exe
set PROJECT_DIR=%~dp0
if not exist "%IDEA_EXE%" (
  echo IDEA not found: %IDEA_EXE%
  exit /b 1
)
start "" "%IDEA_EXE%" "%PROJECT_DIR%"
echo Opened project in IntelliJ IDEA: %PROJECT_DIR%
