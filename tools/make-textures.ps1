# 更多漏斗 / More Hoppers - 材质生成脚本
#
# 所有贴图都由原版 Minecraft 贴图程序化改写而来，不新画、不使用外部素材：
#   * 金/钻石/下界合金/铜漏斗的方块贴图与物品图标 = 原版 hopper 贴图做「离散调色板替换」
#     （像素位置、alpha、明暗层级完全不变，只把中性灰换成对应金属色阶）
#   * 逆向漏斗不新建方块贴图（模型直接用原版贴图），物品图标 = 原版图标垂直翻转
#   * 铜漏斗 GUI = 原版 hopper.png 插入一行 18px 槽位带（10 槽面板）
#
# 用法:  powershell -ExecutionPolicy Bypass -File tools\make-textures.ps1

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem

$clientJar = Join-Path $env:USERPROFILE '.gradle\caches\fabric-loom\1.21\minecraft-client.jar'
$assets    = Join-Path $PSScriptRoot '..\src\main\resources\assets\morehoppers\textures'
if (-not (Test-Path $clientJar)) { throw "找不到原版客户端 jar: $clientJar" }

$zip = [System.IO.Compression.ZipFile]::OpenRead($clientJar)

function Get-VanillaBitmap([string]$entry) {
    $e = $zip.GetEntry($entry)
    if (-not $e) { throw "原版贴图缺失: $entry" }
    $ms = New-Object System.IO.MemoryStream
    $e.Open().CopyTo($ms); $ms.Position = 0
    $bmp = New-Object System.Drawing.Bitmap ([System.Drawing.Image]::FromStream($ms))
    $ms.Dispose()
    return $bmp
}

function Save-Png($bmp, [string]$path) {
    $dir = Split-Path $path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
}

function Get-Luma($c) { return (0.2126 * $c.R + 0.7152 * $c.G + 0.0722 * $c.B) }

function Get-Hex($c) { return ('#{0:X2}{1:X2}{2:X2}' -f $c.R, $c.G, $c.B) }

# 统计一张贴图里不透明颜色的出现次数（键为 "R,G,B"）
function Get-ColorCounts($bmp) {
    $counts = @{}
    for ($y = 0; $y -lt $bmp.Height; $y++) {
        for ($x = 0; $x -lt $bmp.Width; $x++) {
            $c = $bmp.GetPixel($x, $y)
            if ($c.A -eq 0) { continue }
            $k = '{0},{1},{2}' -f $c.R, $c.G, $c.B
            if ($counts.ContainsKey($k)) { $counts[$k]++ } else { $counts[$k] = 1 }
        }
    }
    return $counts
}

# ---------------------------------------------------------------- 1. 灰阶梯
# 收集原版漏斗全部贴图里出现过的颜色（手绘平色，必须逐个保留层级）
$hopperSources = @(
    'assets/minecraft/textures/block/hopper_outside.png',
    'assets/minecraft/textures/block/hopper_inside.png',
    'assets/minecraft/textures/block/hopper_top.png',
    'assets/minecraft/textures/item/hopper.png')

$greyColors = @{}
foreach ($src in $hopperSources) {
    $bmp = Get-VanillaBitmap $src
    foreach ($kv in (Get-ColorCounts $bmp).GetEnumerator()) {
        if (-not $greyColors.ContainsKey($kv.Key)) {
            $p = $kv.Key -split ','
            $greyColors[$kv.Key] = [System.Drawing.Color]::FromArgb(255, [int]$p[0], [int]$p[1], [int]$p[2])
        }
    }
    $bmp.Dispose()
}
$ladder = @($greyColors.Values | Sort-Object { Get-Luma $_ })
Write-Host ("灰阶梯 {0} 级: {1}" -f $ladder.Count, (($ladder | ForEach-Object { Get-Hex $_ }) -join ' '))

# ---------------------------------------------------------------- 2. 金属色阶
# 主色 = 原版金属锭贴图「较亮那一半像素的平均色」（受光面招牌色，避免选到纯白/纯黑极值），
# 再乘一个可读性系数；灰阶梯的归一化亮度决定金属色的明暗位置
# （最暗一级保留 55% 亮度，避免与原版灰一样发黑而看不出颜色）。
$lumaMin = Get-Luma $ladder[0]
$lumaMax = Get-Luma $ladder[$ladder.Count - 1]

function Get-MetalBase([string]$itemTexture) {
    $bmp = Get-VanillaBitmap $itemTexture
    $px = @()
    for ($y = 0; $y -lt $bmp.Height; $y++) {
        for ($x = 0; $x -lt $bmp.Width; $x++) {
            $c = $bmp.GetPixel($x, $y)
            if ($c.A -lt 200) { continue }
            $px += $c
        }
    }
    $bmp.Dispose()

    $sorted = @($px | Sort-Object { Get-Luma $_ })
    $take = [Math]::Max(1, [int][Math]::Floor($sorted.Count / 2))
    $lit = @($sorted[($sorted.Count - $take)..($sorted.Count - 1)])
    $r = 0; $g = 0; $b = 0
    foreach ($c in $lit) { $r += $c.R; $g += $c.G; $b += $c.B }
    return [System.Drawing.Color]::FromArgb(255,
        [int][Math]::Round($r / $lit.Count), [int][Math]::Round($g / $lit.Count), [int][Math]::Round($b / $lit.Count))
}

function Get-MetalRamp([string]$itemTexture, [double]$lift, [string]$tintHex, [double]$tintMix) {
    $base = Get-MetalBase $itemTexture
    $base = [System.Drawing.Color]::FromArgb(255,
        [int][Math]::Min(255, [Math]::Round($base.R * $lift)),
        [int][Math]::Min(255, [Math]::Round($base.G * $lift)),
        [int][Math]::Min(255, [Math]::Round($base.B * $lift)))
    if ($tintHex -and $tintMix -gt 0) {
        # 朝指定色偏一点：下界合金在游戏里是带紫调的深色，纯取锭的平均色会与原版灰分不清
        $tr = [Convert]::ToInt32($tintHex.Substring(1, 2), 16)
        $tg = [Convert]::ToInt32($tintHex.Substring(3, 2), 16)
        $tb = [Convert]::ToInt32($tintHex.Substring(5, 2), 16)
        $base = [System.Drawing.Color]::FromArgb(255,
            [int][Math]::Round($base.R * (1 - $tintMix) + $tr * $tintMix),
            [int][Math]::Round($base.G * (1 - $tintMix) + $tg * $tintMix),
            [int][Math]::Round($base.B * (1 - $tintMix) + $tb * $tintMix))
    }

    $ramp = @()
    foreach ($g in $ladder) {
        $t = 1.0
        if ($lumaMax -gt $lumaMin) { $t = ((Get-Luma $g) - $lumaMin) / ($lumaMax - $lumaMin) }
        $f = 0.55 + 0.45 * $t
        $ramp += [System.Drawing.Color]::FromArgb(255,
            [int][Math]::Round($base.R * $f),
            [int][Math]::Round($base.G * $f),
            [int][Math]::Round($base.B * $f))
    }
    Write-Host ("  主色 {0} → 色阶 {1}" -f (Get-Hex $base), (($ramp | ForEach-Object { Get-Hex $_ }) -join ' '))
    return , $ramp
}

# ---------------------------------------------------------------- 3. 逐像素换色
function Convert-HopperTexture($srcBmp, $ramp) {
    $out = New-Object System.Drawing.Bitmap $srcBmp.Width, $srcBmp.Height, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $srcBmp.Height; $y++) {
        for ($x = 0; $x -lt $srcBmp.Width; $x++) {
            $c = $srcBmp.GetPixel($x, $y)
            if ($c.A -eq 0) { continue }
            $luma = Get-Luma $c
            $best = 0; $bestD = [double]::MaxValue
            for ($i = 0; $i -lt $ladder.Count; $i++) {
                $d = [Math]::Abs($luma - (Get-Luma $ladder[$i]))
                if ($d -lt $bestD) { $bestD = $d; $best = $i }
            }
            $t = $ramp[$best]
            $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, $t.R, $t.G, $t.B))
        }
    }
    return $out
}

$metals = @(
    @{ Name = 'gold';      Item = 'assets/minecraft/textures/item/gold_ingot.png';      Lift = 1.05; Tint = '';        TintMix = 0.0 },
    @{ Name = 'diamond';   Item = 'assets/minecraft/textures/item/diamond.png';         Lift = 1.05; Tint = '';        TintMix = 0.0 },
    @{ Name = 'netherite'; Item = 'assets/minecraft/textures/item/netherite_ingot.png'; Lift = 1.15; Tint = '#6A4E8C'; TintMix = 0.35 },
    @{ Name = 'copper';    Item = 'assets/minecraft/textures/item/copper_ingot.png';    Lift = 1.15; Tint = '';        TintMix = 0.0 })

foreach ($m in $metals) {
    Write-Host $m['Name']
    $ramp = Get-MetalRamp $m['Item'] $m['Lift'] $m['Tint'] $m['TintMix']
    $parts = @(
        @{ Src = 'assets/minecraft/textures/block/hopper_outside.png'; Dst = "block\$($m['Name'])_hopper_outside.png" },
        @{ Src = 'assets/minecraft/textures/block/hopper_inside.png';  Dst = "block\$($m['Name'])_hopper_inside.png" },
        @{ Src = 'assets/minecraft/textures/block/hopper_top.png';     Dst = "block\$($m['Name'])_hopper_top.png" },
        @{ Src = 'assets/minecraft/textures/item/hopper.png';          Dst = "item\$($m['Name'])_hopper.png" })
    foreach ($part in $parts) {
        $src = Get-VanillaBitmap $part['Src']
        $res = Convert-HopperTexture $src $ramp
        Save-Png $res (Join-Path $assets $part['Dst'])
        $src.Dispose(); $res.Dispose()
    }
}

# ---------------------------------------------------------------- 4. 逆向漏斗图标 = 原版图标垂直翻转
$icon = Get-VanillaBitmap 'assets/minecraft/textures/item/hopper.png'
$flipped = New-Object System.Drawing.Bitmap $icon.Width, $icon.Height, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
for ($y = 0; $y -lt $icon.Height; $y++) {
    for ($x = 0; $x -lt $icon.Width; $x++) {
        $flipped.SetPixel($x, ($icon.Height - 1 - $y), $icon.GetPixel($x, $y))
    }
}
Save-Png $flipped (Join-Path $assets 'item\reverse_hopper.png')
$icon.Dispose(); $flipped.Dispose()

# ---------------------------------------------------------------- 5. 铜漏斗 GUI = 原版 hopper.png 插入第二行槽位带
# 原版面板 176x133（HopperScreen.backgroundHeight=133），槽位节距 18px：
# 第 1 行槽位方块 y=20（边框带 y=19..36），玩家背包首行边框 y=50。
# 新版在 y=37 处插入同样的边框带，y>=37 整体下移 18px → 面板 176x151，槽位行 y=20 / y=38。
$panelWidth = 176
$panelHeight = 133
$gui = Get-VanillaBitmap 'assets/minecraft/textures/gui/container/hopper.png'
$panel = New-Object System.Drawing.Bitmap $panelWidth, ($panelHeight + 18), ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
for ($y = 0; $y -lt 37; $y++) {
    for ($x = 0; $x -lt $panelWidth; $x++) { $panel.SetPixel($x, $y, $gui.GetPixel($x, $y)) }
}
for ($y = 19; $y -lt 37; $y++) {
    for ($x = 0; $x -lt $panelWidth; $x++) { $panel.SetPixel($x, $y + 18, $gui.GetPixel($x, $y)) }
}
for ($y = 37; $y -lt $panelHeight; $y++) {
    for ($x = 0; $x -lt $panelWidth; $x++) { $panel.SetPixel($x, $y + 18, $gui.GetPixel($x, $y)) }
}
# 画布必须 256x256（和原版容器贴图一样）：DrawContext.drawTexture(Identifier,int,int,int,int,int,int)
# 这个 7 参数重载内部写死 textureWidth/Height=256，贴图宽高不是 256 就会被 UV 错位 + 拉伸。
$newGui = New-Object System.Drawing.Bitmap 256, 256, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
for ($y = 0; $y -lt $panel.Height; $y++) {
    for ($x = 0; $x -lt $panel.Width; $x++) { $newGui.SetPixel($x, $y, $panel.GetPixel($x, $y)) }
}
Save-Png $newGui (Join-Path $assets 'gui\container\copper_hopper.png')
$gui.Dispose(); $panel.Dispose(); $newGui.Dispose()

$zip.Dispose()
Write-Host "完成: $assets"
