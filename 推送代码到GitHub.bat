@echo off
chcp 65001 >nul 2>&1
title Margin - Push to GitHub
cd /d "%~dp0"
echo.
echo   ==========================================
echo     Margin「留白」 - 推送到 GitHub
echo   ==========================================
echo.
echo   1) 令牌只在需要时生成一次：
echo      GitHub - Settings - Developer settings - Personal access tokens
echo        方式 A（推荐，细粒度）：Tokens (fine-grained) - Generate new token
echo          Repository access: Only select repositories - 勾选 Margin
echo          Permissions - Repository permissions - Contents: Read and write
echo        方式 B（经典）：Tokens (classic) - 勾选 repo 和 workflow
echo.
echo   2) 复制令牌到剪贴板，然后在这里按任意键。
echo.
pause >nul

powershell -NoProfile -ExecutionPolicy Bypass -File "tools\push-to-github.ps1"
if errorlevel 1 goto failed

echo.
pause
exit /b 0

:failed
echo.
echo   [已停止] 按上面的提示处理后，重新双击本文件。
echo.
pause
exit /b 1
