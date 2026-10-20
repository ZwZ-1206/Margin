@echo off
title Margin - 首次保存 Git 凭据
cd /d "%~dp0"
echo.
echo   ==========================================
echo     首次保存 Git 凭据（只做一次）
echo   ==========================================
echo.
echo   运行后会要求输入：
echo      用户名：ZwZ-1206
echo      密码  ：你的 GitHub 令牌（粘贴后回车，不显示）
echo.
echo   凭据会存进 Windows 凭据管理器（加密），
echo   之后 push 不再需要令牌，AI 也能直接帮你推送。
echo.
pause
powershell -NoProfile -ExecutionPolicy Bypass -File "tools\save-git-credential.ps1"
if errorlevel 1 goto failed
echo.
pause
exit /b 0

:failed
echo.
echo   [失败] 检查用户名/令牌是否正确，或网络是否可达。
pause
exit /b 1
