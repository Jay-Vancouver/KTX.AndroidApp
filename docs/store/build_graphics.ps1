# Renders the Google Play feature graphics (1024 x 500 PNG) with Microsoft Edge.
#   powershell -ExecutionPolicy Bypass -File docs\store\build_graphics.ps1
$here = $PSScriptRoot
$edge = @(
    "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    "C:\Program Files\Microsoft\Edge\Application\msedge.exe"
) | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $edge) { throw "Microsoft Edge not found" }
Add-Type -AssemblyName System.Drawing

$profileDir = Join-Path $env:TEMP "ktx-store-edge"

function Shoot([string]$url, [string]$png, [int]$w, [int]$h) {
    if (Test-Path $png) { Remove-Item $png }
    Start-Process -FilePath $edge -Wait -ArgumentList @(
        '--headless', '--disable-gpu', '--no-first-run', '--hide-scrollbars',
        "--user-data-dir=$profileDir", '--force-device-scale-factor=1',
        "--window-size=$w,$h", "--screenshot=$png", "`"$url`"")
    if (Test-Path $png) {
        $img = [System.Drawing.Image]::FromFile($png)
        "{0}  {1}x{2}  {3:N0} KB" -f (Split-Path $png -Leaf), $img.Width, $img.Height, ((Get-Item $png).Length / 1KB)
        $img.Dispose()
    } else { "FAILED: $png" }
}

# Phone screenshots (1080 x 1920): image, English caption, Korean caption
$shots = @(
    @('screen_home.png',  'Location sends <em>by itself</em>', 'After pickup, your truck location is shared automatically.',
                          '위치는 <em>자동으로</em> 전송', '픽업하면 트럭 위치가 자동으로 공유됩니다.'),
    @('screen_pickup.png', 'Scan pallet tags <em>at pickup</em>', 'Or type the order number when there is no tag.',
                          '픽업 때 <em>태그 스캔</em>', '태그가 없으면 오더 번호를 입력하세요.'),
    @('screen_login.png', 'Sign in with <em>one text</em>', 'Enter your phone number and tap the link we send.',
                          '<em>문자 한 통</em>으로 로그인', '전화번호를 입력하고 문자로 받은 링크를 누르세요.')
)
$base = ([Uri](Join-Path $here 'screenshot.html')).AbsoluteUri
$n = 0
foreach ($s in $shots) {
    $n++
    foreach ($lang in 'en', 'ko') {
        $h = if ($lang -eq 'en') { $s[1] } else { $s[3] }
        $p = if ($lang -eq 'en') { $s[2] } else { $s[4] }
        $url = "{0}?img={1}&lang={2}&h={3}&p={4}" -f $base, $s[0], $lang, [Uri]::EscapeDataString($h), [Uri]::EscapeDataString($p)
        Shoot $url (Join-Path $here "KTX_Driver_screenshot_${n}_$lang.png") 1080 1920
    }
}
foreach ($lang in 'en', 'ko') {
    $html = Join-Path $here "feature_graphic_$lang.html"
    $png = Join-Path $here "KTX_Driver_feature_graphic_$lang.png"
    if (Test-Path $png) { Remove-Item $png }
    Start-Process -FilePath $edge -Wait -ArgumentList @(
        '--headless', '--disable-gpu', '--no-first-run', '--hide-scrollbars',
        "--user-data-dir=$profileDir", '--force-device-scale-factor=1',
        '--window-size=1024,500', "--screenshot=$png", ([Uri]$html).AbsoluteUri)
    if (Test-Path $png) {
        $img = [System.Drawing.Image]::FromFile($png)
        "{0}  {1}x{2}  {3:N0} KB" -f $png, $img.Width, $img.Height, ((Get-Item $png).Length / 1KB)
        $img.Dispose()
    } else { "FAILED: $png" }
}
