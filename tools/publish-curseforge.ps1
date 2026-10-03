#!/usr/bin/env pwsh
<#
.SYNOPSIS
    把一个已构建好的 jar 上传到 CurseForge。

.DESCRIPTION
    走 CurseForge 官方 Upload API：
        POST {base}/api/projects/{projectId}/upload-file   (multipart: metadata + file)
        Header: X-Api-Token
    参考：https://support.curseforge.com/support/solutions/articles/9000197321

    token 来源优先级：-Token > $env:CURSEFORGE_TOKEN > -TokenFile 的内容。
    token 永远不会被打印；-DryRun 会把命令里的 token 替换成 <redacted>。

    注意：CurseForge 的上传接口会被 WAF 拦掉一部分来源 IP（本机 PowerShell/curl
    访问会得到 403），这个脚本主要给 GitHub Actions 用。

.EXAMPLE
    # 本地干跑：只打印将发送的元数据与命令，不发请求
    ./tools/publish-curseforge.ps1 -DryRun

.EXAMPLE
    # CI 里：token 由 secrets.CURSEFORGE_TOKEN 注入环境变量
    ./tools/publish-curseforge.ps1
#>
[CmdletBinding()]
param(
    [string]$ProjectId = '1723436',
    [string]$Jar,
    [string]$Changelog,
    [string]$ChangelogFile,
    [string]$Version,
    [ValidateSet('release', 'beta', 'alpha')][string]$ReleaseType = 'release',
    [string[]]$GameVersionNames = @('1.21', 'Fabric', 'Client', 'Server'),
    [string]$RelatedSlug = 'fabric-api',
    [string]$Token,
    [string]$TokenFile,
    [string]$BaseUrl = 'https://minecraft.curseforge.com',
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot

function Read-Prop([string]$name, [string]$default) {
    $file = Join-Path $repoRoot 'gradle.properties'
    if (-not (Test-Path $file)) { return $default }
    $m = Select-String -Path $file -Pattern "^$([regex]::Escape($name))=(.+)$" | Select-Object -First 1
    if ($m) { return $m.Matches[0].Groups[1].Value.Trim() }
    return $default
}

if (-not $Version) { $Version = Read-Prop 'mod_version' '0.0.0' }
if (-not $Jar) {
    $artifact = Read-Prop 'archives_base_name' 'mod'
    $candidate = Join-Path $repoRoot "build/libs/$artifact-$Version.jar"
    if (-not (Test-Path $candidate)) {
        throw "找不到 jar：$candidate（先跑 ./gradlew build）"
    }
    $Jar = $candidate
}
if (-not (Test-Path $Jar)) { throw "找不到 jar：$Jar" }

if (-not $Token) { $Token = $env:CURSEFORGE_TOKEN }
if (-not $Token -and $TokenFile -and (Test-Path $TokenFile)) { $Token = (Get-Content -Raw $TokenFile).Trim() }

if (-not $Changelog) {
    if ($ChangelogFile -and (Test-Path $ChangelogFile)) {
        $Changelog = Get-Content -Raw $ChangelogFile
    } else {
        $Changelog = "More Hoppers $Version"
    }
}

$metadata = [ordered]@{
    changelog        = $Changelog
    changelogType    = 'markdown'
    displayName      = "More Hoppers $Version"
    releaseType      = $ReleaseType
    gameVersionNames = $GameVersionNames
    relations        = @{ projects = @(@{ slug = $RelatedSlug; type = 'requiredDependency' }) }
} | ConvertTo-Json -Depth 6 -Compress

$uri = "$BaseUrl/api/projects/$ProjectId/upload-file"
$curl = if ($env:OS -eq 'Windows_NT') { 'curl.exe' } else { 'curl' }

if ($DryRun) {
    Write-Host "DRY RUN"
    Write-Host "  uri      : $uri"
    Write-Host "  jar      : $Jar ($((Get-Item $Jar).Length) bytes)"
    Write-Host "  token    : $(if ($Token) { "<redacted> (len=$($Token.Length))" } else { '<MISSING>' })"
    Write-Host "  metadata : $metadata"
    Write-Host "  command  : $curl -sS -X POST -H 'X-Api-Token: <redacted>' -F 'metadata=<tmpfile' -F 'file=@$Jar' $uri"
    return
}

if (-not $Token) {
    Write-Host "::warning:: 没有 CURSEFORGE_TOKEN，跳过 CurseForge 发布（本地可存到 F:\dswork\.cf-token.txt 后用 -TokenFile 指定）"
    return
}

# 元数据走临时文件：避免命令行参数编码问题（changelog 里可能有中文）
$tmp = [IO.Path]::Combine([IO.Path]::GetTempPath(), "cf-metadata-$([guid]::NewGuid().ToString('N')).json")
[IO.File]::WriteAllText($tmp, $metadata, (New-Object System.Text.UTF8Encoding($false)))
try {
    $response = & $curl -sS -X POST -H "X-Api-Token: $Token" -F "metadata=<$tmp" -F "file=@$Jar" $uri
    Write-Host "CurseForge 响应: $response"
    if ($response -notmatch '"id"') {
        throw "上传似乎失败（响应里没有 id）：$response"
    }
} finally {
    Remove-Item $tmp -ErrorAction SilentlyContinue
}
