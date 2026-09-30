# Prints the install guides to PDF with Microsoft Edge (no extra software needed).
#   powershell -ExecutionPolicy Bypass -File docs\manual\build_pdf.ps1
$here = $PSScriptRoot
$edge = @(
    "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    "C:\Program Files\Microsoft\Edge\Application\msedge.exe"
) | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $edge) { throw "Microsoft Edge not found" }

$profileDir = Join-Path $env:TEMP "ktx-manual-edge"
foreach ($pair in @(@('install_ko.html', 'KTX_Driver_Install_Guide_KO.pdf'), @('install_en.html', 'KTX_Driver_Install_Guide_EN.pdf'))) {
    $html = Join-Path $here $pair[0]
    $pdf = Join-Path $here $pair[1]
    $url = ([Uri]$html).AbsoluteUri
    if (Test-Path $pdf) { Remove-Item $pdf }
    # Start-Process -Wait: calling msedge.exe directly returns before the PDF is written.
    Start-Process -FilePath $edge -Wait -ArgumentList @(
        '--headless', '--disable-gpu', '--no-first-run', "--user-data-dir=$profileDir",
        '--no-pdf-header-footer', "--print-to-pdf=$pdf", $url)
    if (Test-Path $pdf) { "{0}  {1:N0} KB" -f $pdf, ((Get-Item $pdf).Length / 1KB) } else { "FAILED: $pdf" }
}
