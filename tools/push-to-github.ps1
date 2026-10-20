# Margin「留白」· 读取剪贴板里的令牌，推送代码并同步 GitHub Release
# 由「推送代码到GitHub.bat」调用；也可在终端直接运行本脚本
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$owner = 'ZwZ-1206'
$repo = 'Margin'
$branch = 'main'
$cleanRemote = "https://github.com/$owner/$repo.git"

function Fail($msg) {
    Write-Host ""
    Write-Host "  [失败] $msg" -ForegroundColor Red
    exit 1
}

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
git fetch origin $branch 2>$null | Out-Null
$pending = @(git log --oneline "origin/$branch..HEAD" 2>$null)
if ($LASTEXITCODE -ne 0 -or $pending.Count -eq 0) {
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
        git push $url $branch 2>&1 | ForEach-Object { Write-Host "    $_" }
    } else {
        $env:HTTP_PROXY = ''
        $env:HTTPS_PROXY = ''
        $env:http_proxy = ''
        $env:https_proxy = ''
        git push $url $branch 2>&1 | ForEach-Object { Write-Host "    $_" }
    }
    if ($LASTEXITCODE -eq 0) { $pushOk = $true; break }
    git remote set-url origin $cleanRemote 2>$null
    Write-Host "    第 $i 次未成功，重试..." -ForegroundColor DarkYellow
    Start-Sleep -Seconds 5
}
git remote set-url origin $cleanRemote 2>$null

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
exit 0
