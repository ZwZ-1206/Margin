# Margin「留白」· 首次保存 GitHub 凭据到 Windows 凭据管理器
# 只需运行一次；之后 git push / 脚本推送都不再需要输入令牌
$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }
$orig = [Console]::OutputEncoding

function Resolve-Git {
    $cmd = Get-Command git -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $cands = @(
        "$env:ProgramFiles\Git\cmd\git.exe",
        "${env:ProgramFiles(x86)}\Git\cmd\git.exe",
        "$env:LOCALAPPDATA\Programs\Git\cmd\git.exe"
    )
    foreach ($c in $cands) { if (Test-Path $c) { return $c } }
    $base = Join-Path $env:USERPROFILE '.workbuddy\binaries\PortableGit\versions'
    if (Test-Path $base) {
        return Get-ChildItem $base -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            ForEach-Object { Join-Path $_.FullName 'cmd\git.exe' } |
            Where-Object { Test-Path $_ } |
            Select-Object -First 1
    }
    return $null
}

try {
    $git = Resolve-Git
    if (-not $git) { Write-Host "  没有找到 git。" -ForegroundColor Red; exit 1 }
    Set-Location (Split-Path -Parent $PSScriptRoot)

    Write-Host "  git: $git" -ForegroundColor DarkGray
    Write-Host ""
    Write-Host "  下面会要求输入用户名和密码（只输入一次）：" -ForegroundColor Cyan
    Write-Host "    用户名：ZwZ-1206"
    Write-Host "    密码  ：你的 GitHub 令牌（粘贴后回车，不显示）"
    Write-Host ""

    & $git fetch origin
    if ($LASTEXITCODE -ne 0) {
        Write-Host ""
        Write-Host "  [失败] 无法连接或凭据不正确。" -ForegroundColor Red
        exit 1
    }
    Write-Host ""
    Write-Host "  [OK] 凭据已保存到 Windows 凭据管理器。" -ForegroundColor Green
    Write-Host "       以后 git push 不再需要输入令牌。"
    exit 0
} finally {
    try { [Console]::OutputEncoding = $orig } catch { }
}
