@echo off
title Margin - Push to GitHub
cd /d "%~dp0"
echo.
echo   ==========================================
echo     Margin「留白」 - 推送到 GitHub
echo   ==========================================
echo.
echo   令牌只在需要时生成一次：
echo      GitHub - Settings - Developer settings - Personal access tokens
echo        方式 A（推荐，细粒度）：Tokens (fine-grained) - Generate new token
echo          Repository access: Only select repositories - 勾选 Margin
echo          Permissions - Repository permissions - Contents: Read and write
echo        方式 B（经典）：Tokens (classic) - 勾选 repo 和 workflow
echo.
echo   接下来会提示粘贴令牌：Ctrl+V 或右键粘贴，然后回车。
echo   输入过程中不显示任何字符，这是正常的。
echo.

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
