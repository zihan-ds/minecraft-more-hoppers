# 生成 Modrinth 项目图标：把五个漏斗的物品图标最近邻放大后拼成 512x512。
# 只读取模组自己的物品贴图，不引入新美术（与"材质由原版材质程序化改写"的原则一致）。
# 产物写到 build/icon/（build/ 已被 .gitignore 忽略，可重复生成）。
Add-Type -AssemblyName System.Drawing

$root = Split-Path $PSScriptRoot -Parent
$src = Join-Path $root 'src\main\resources\assets\morehoppers\textures\item'
$names = @('gold_hopper.png', 'diamond_hopper.png', 'netherite_hopper.png', 'copper_hopper.png', 'reverse_hopper.png')

$size = 512
$cell = 128
$gap = 16
$layout = @(@(0, 1, 2), @(3, 4))

$icon = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($icon)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$g.Clear([System.Drawing.Color]::FromArgb(255, 46, 46, 46))

$totalH = 2 * $cell + $gap
for ($r = 0; $r -lt $layout.Count; $r++) {
    $row = $layout[$r]
    $totalW = $row.Count * $cell + ($row.Count - 1) * $gap
    $x0 = [int](($size - $totalW) / 2)
    $y0 = [int](($size - $totalH) / 2) + $r * ($cell + $gap)
    for ($c = 0; $c -lt $row.Count; $c++) {
        $file = Join-Path $src $names[$row[$c]]
        $bmp = [System.Drawing.Image]::FromFile($file)
        $rect = New-Object System.Drawing.Rectangle ($x0 + $c * ($cell + $gap)), $y0, $cell, $cell
        $g.DrawImage($bmp, $rect)
        $bmp.Dispose()
    }
}
$g.Dispose()

$outDir = Join-Path $root 'build\icon'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$out = Join-Path $outDir 'more-hoppers-icon.png'
$icon.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$icon.Dispose()

# 自检：产物必须是 512x512
$check = [System.Drawing.Image]::FromFile($out)
$w = $check.Width; $h = $check.Height
$check.Dispose()
if ($w -ne $size -or $h -ne $size) {
    throw "图标尺寸错误: ${w}x${h}，期望 ${size}x${size}"
}
Write-Host "已生成: $out (${w}x${h})"
