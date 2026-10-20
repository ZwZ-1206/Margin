# Margin「留白」· 粘贴令牌，推送代码并同步 GitHub Release
# 由「推送代码到GitHub.bat」调用；也可在终端直接运行本脚本
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

# git 输出是 UTF-8，控制台默认可能是 GBK：统一成 UTF-8，提交信息才不会乱码
$__origEncoding = [Console]::OutputEncoding
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }
try { $OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$owner = 'ZwZ-1206'
$repo = 'Margin'
$branch = 'main'
$cleanRemote = "https://github.com/$owner/$repo.git"

function Restore-Encoding {
    try { [Console]::OutputEncoding = $__origEncoding } catch { }
}

function Fail($msg) {
    Write-Host ""
    Write-Host "  [失败] $msg" -ForegroundColor Red
    Restore-Encoding
    exit 1
}


# ---------- 0. 定位 git（本机可能只有便携版） ----------
function Resolve-Git {
    $cmd = Get-Command git -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }

    $cands = @(
        "$env:ProgramFiles\Git\cmd\git.exe",
        "${env:ProgramFiles(x86)}\Git\cmd\git.exe",
        "$env:LOCALAPPDATA\Programs\Git\cmd\git.exe"
    )
    foreach ($c in $cands) { if (Test-Path $c) { return $c } }

    # 兜底：WorkBuddy 自带的便携版（本机唯一可用的 git）
    $base = Join-Path $env:USERPROFILE '.workbuddy\binaries\PortableGit\versions'
    if (Test-Path $base) {
        $found = Get-ChildItem $base -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            ForEach-Object { Join-Path $_.FullName 'cmd\git.exe' } |
            Where-Object { Test-Path $_ } |
            Select-Object -First 1
        if ($found) { return $found }
    }
    return $null
}

$git = Resolve-Git
if (-not $git) { Fail "没有找到 git。请安装 Git for Windows（https://git-scm.com/download/win）后重新运行。" }
Write-Host "  git: $git" -ForegroundColor DarkGray


# ---------- 1. 输入令牌 ----------
Write-Host "  请粘贴 GitHub 令牌（粘贴后回车，输入过程不显示）" -ForegroundColor Cyan
$token = ''
while ($true) {
    $secure = Read-Host -AsSecureString "  令牌"
    if (-not $secure -or $secure.Length -eq 0) {
        Write-Host "  没有输入内容，请重新粘贴。" -ForegroundColor Yellow
        continue
    }
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        $token = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
    $token = $token.Trim() -replace '\s', ''
    if ($token -match '^(ghp_|github_pat_)') { break }
    Write-Host "  格式不对（应以 ghp_ 或 github_pat_ 开头），请重新粘贴。" -ForegroundColor Yellow
}

$masked = $token.Substring(0, [Math]::Min(11, $token.Length)) + '...'
Write-Host "  已接收令牌: $masked" -ForegroundColor DarkGray

# ---------- 2. 待推送内容 ----------
# fetch 失败（离线/代理）不应中断流程，推送阶段会再试
try { & $git fetch origin $branch 2>$null | Out-Null } catch { }
$pending = @()
try { $pending = @(& $git log --oneline "origin/$branch..HEAD" 2>$null) } catch { }
if (-not $pending -or $pending.Count -eq 0) {
    Write-Host "  没有新的提交需要推送，仍会同步 Release。" -ForegroundColor Yellow
} else {
    Write-Host "  待推送提交：" -ForegroundColor Cyan
    $pending | ForEach-Object { Write-Host "    $_" }
}

# ---------- 3. 推送（先走代理，失败再直连；最多 4 次） ----------
$pushOk = $false
for ($i = 1; $i -le 4; $i++) {
    $url = "https://${owner}:$token@github.com/$owner/$repo.git"
    if ($i -le 2) {
        & $git push $url $branch 2>&1 | ForEach-Object { Write-Host "    $_" }
    } else {
        $env:HTTP_PROXY = ''
        $env:HTTPS_PROXY = ''
        $env:http_proxy = ''
        $env:https_proxy = ''
        & $git push $url $branch 2>&1 | ForEach-Object { Write-Host "    $_" }
    }
    if ($LASTEXITCODE -eq 0) { $pushOk = $true; break }
    try { & $git remote set-url origin $cleanRemote 2>$null } catch { }
    Write-Host "    第 $i 次未成功，重试..." -ForegroundColor DarkYellow
    Start-Sleep -Seconds 5
}
try { & $git remote set-url origin $cleanRemote 2>$null } catch { }

if (-not $pushOk) { Fail "推送失败：请检查网络（GitHub 是否可达）与令牌权限（Contents: Read and write）" }
Write-Host "  [OK] 代码已推送" -ForegroundColor Green

# ---------- 4. 同步 Release ----------
$versionFile = Join-Path $root 'offscreen-web\public\downloads\version.json'
if (-not (Test-Path $versionFile)) {
    Write-Host "  （未找到 version.json，跳过 Release）" -ForegroundColor DarkGray
    exit 0
}

$v = Get-Content $versionFile -Raw -Encoding UTF8 | ConvertFrom-Json
$tag = "v$($v.version)"
$apkName = "margin-$($v.version).apk"
$apk = Join-Path $root "offscreen-web\public\downloads\$apkName"

$headers = @{
    Authorization          = "Bearer $token"
    Accept                 = 'application/vnd.github+json'
    'X-GitHub-Api-Version' = '2022-11-28'
    'User-Agent'           = 'margin-release-script'
}

Write-Host "  同步 Release $tag ..." -ForegroundColor Cyan
$release = $null
try {
    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$owner/$repo/releases/tags/$tag" -Headers $headers -Method Get
} catch {
    $release = $null
}

if ($null -eq $release) {
    $noteText = "$($v.notes)"
    $body = @{
        tag_name         = $tag
        name             = "Margin「留白」$tag"
        body             = "$noteText`n`nSHA-256: $($v.apk.sha256)"
        draft            = $false
        prerelease       = $false
        target_commitish = $branch
    } | ConvertTo-Json -Depth 4
    $bodyBytes = [System.Text.Encoding]::UTF8.GetBytes($body)
    try {
        $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$owner/$repo/releases" -Headers $headers -Method Post -Body $bodyBytes -ContentType 'application/json; charset=utf-8'
        Write-Host "  [OK] 已创建 Release $tag" -ForegroundColor Green
    } catch {
        Fail "创建 Release 失败：$($_.Exception.Message)（令牌需要 Contents: Read and write）"
    }
} else {
    Write-Host "  Release $tag 已存在" -ForegroundColor DarkGray
}

$hasAsset = $false
if ($release.assets) {
    $hasAsset = @($release.assets | Where-Object { $_.name -eq $apkName }).Count -gt 0
}

if ($hasAsset) {
    Write-Host "  安装包已在 Release 中，跳过上传" -ForegroundColor DarkGray
} elseif (Test-Path $apk) {
    $uploadUrl = ($release.upload_url -replace '\{.*$', '') + "?name=$apkName"
    try {
        Invoke-RestMethod -Uri $uploadUrl -Headers $headers -Method Post -InFile $apk -ContentType 'application/vnd.android.package-archive' | Out-Null
        Write-Host "  [OK] 已上传 $apkName" -ForegroundColor Green
    } catch {
        Write-Host "  [警告] 上传安装包失败：$($_.Exception.Message)" -ForegroundColor Yellow
    }
} else {
    Write-Host "  [警告] 找不到 $apk，未上传安装包" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "  完成：https://github.com/$owner/$repo/releases/tag/$tag" -ForegroundColor Green
Restore-Encoding
exit 0
