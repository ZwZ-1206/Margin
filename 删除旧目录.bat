@echo off
chcp 65001 >nul 2>&1
title Margin - 删除旧目录
cd /d "%~dp0"
set OLD=C:\Users\ZwZ\WorkBuddy\Off Screen

echo.
echo   ==========================================
echo     删除旧目录（搬迁遗留）
echo   ==========================================
echo.
echo   新位置：C:\Code\Margin
echo   旧位置：%OLD%
echo.
echo   注意：如果 WorkBuddy 还开着旧目录，删除会失败。
echo         请先在 WorkBuddy 里打开 C:\Code\Margin，或直接关闭 WorkBuddy。
echo.

if not exist "C:\Code\Margin\README.md" (
  echo   [中止] 新目录不完整，为安全起见不删除旧目录。
  echo.
  pause
  exit /b 1
)

set /p CONFIRM=  确认删除旧目录？输入 Y 回车继续，其他键取消：
if /i not "%CONFIRM%"=="Y" (
  echo   已取消。
  pause
  exit /b 0
)

echo.
echo   正在删除...
rmdir /s /q "%OLD%"
if exist "%OLD%" (
  echo.
  echo   [失败] 旧目录仍存在，通常是 WorkBuddy 或资源管理器占用。
  echo          请关闭 WorkBuddy / 资源管理器窗口后重试。
  echo.
  pause
  exit /b 1
)

echo.
echo   已删除。项目现在只保留在 C:\Code\Margin
echo.
pause
exit /b 0
