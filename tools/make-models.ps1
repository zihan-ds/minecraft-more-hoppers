# 更多漏斗 / More Hoppers - 模型生成脚本
#
# 四种金属漏斗的模型 = 原版 hopper / hopper_side 模型，元素一根线都不动，只把四个贴图引用换成我们的；
# 逆向漏斗的模型 = 原版模型 + 原版贴图（显式命名空间），上下翻转由 blockstates 里的 "x": 180 完成。
#
# 用法:  powershell -ExecutionPolicy Bypass -File tools\make-models.ps1

Add-Type -AssemblyName System.IO.Compression.FileSystem

$clientJar = Join-Path $env:USERPROFILE '.gradle\caches\fabric-loom\1.21\minecraft-client.jar'
$outRoot   = Join-Path $PSScriptRoot '..\src\main\resources\assets\morehoppers\models\block'
if (-not (Test-Path $clientJar)) { throw "找不到原版客户端 jar: $clientJar" }
if (-not (Test-Path $outRoot)) { New-Item -ItemType Directory -Force -Path $outRoot | Out-Null }

$zip = [System.IO.Compression.ZipFile]::OpenRead($clientJar)

function Get-VanillaJson([string]$entry) {
    $e = $zip.GetEntry($entry)
    if (-not $e) { throw "原版模型缺失: $entry" }
    $sr = New-Object System.IO.StreamReader($e.Open())
    $text = $sr.ReadToEnd()
    $sr.Close()
    return $text
}

$variants = @(
    @{ Src = 'hopper';      Metals = 'hopper' },
    @{ Src = 'hopper_side'; Metals = 'hopper_side' })

foreach ($v in $variants) {
    $vanilla = Get-VanillaJson "assets/minecraft/models/block/$($v.Src).json"

    # 金属漏斗：换贴图引用
    foreach ($metal in @('gold', 'diamond', 'netherite', 'copper')) {
        $text = $vanilla
        $text = $text -replace '"block/hopper_outside"', "`"morehoppers:block/$($metal)_hopper_outside`""
        $text = $text -replace '"block/hopper_top"',     "`"morehoppers:block/$($metal)_hopper_top`""
        $text = $text -replace '"block/hopper_inside"',  "`"morehoppers:block/$($metal)_hopper_inside`""
        Set-Content -Path (Join-Path $outRoot "$($metal)_$($v.Metals).json") -Value $text -Encoding UTF8
    }

    # 逆向漏斗：沿用原版贴图，只补上命名空间
    $text = $vanilla
    $text = $text -replace '"block/hopper_outside"', '"minecraft:block/hopper_outside"'
    $text = $text -replace '"block/hopper_top"',     '"minecraft:block/hopper_top"'
    $text = $text -replace '"block/hopper_inside"',  '"minecraft:block/hopper_inside"'
    Set-Content -Path (Join-Path $outRoot "reverse_$($v.Metals).json") -Value $text -Encoding UTF8
}

$zip.Dispose()
Write-Host "完成: $outRoot"
