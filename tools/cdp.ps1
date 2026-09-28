param([Parameter(Mandatory = $true)][string]$Expr)
# Evaluates a JS expression in the KTX Driver app's WebView (debug build) and prints the result.
$adb = "C:\Users\Admin\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$ErrorActionPreference = 'Stop'
$appPid = "$(& $adb shell pidof com.ktxtransport.driver)".Trim()
if (-not $appPid) { 'APP NOT RUNNING'; exit 1 }
& $adb forward tcp:9222 localabstract:webview_devtools_remote_$appPid | Out-Null
$page = (Invoke-WebRequest http://127.0.0.1:9222/json -UseBasicParsing).Content | ConvertFrom-Json | ForEach-Object { $_ } | Where-Object type -eq 'page' | Select-Object -First 1
$ws = New-Object System.Net.WebSockets.ClientWebSocket
$ct = [Threading.CancellationToken]::None
$ws.ConnectAsync([Uri]$page.webSocketDebuggerUrl, $ct).Wait()
$msg = @{ id = 1; method = 'Runtime.evaluate'; params = @{ expression = $Expr; returnByValue = $true; awaitPromise = $true } } | ConvertTo-Json -Compress -Depth 5
$b = [Text.Encoding]::UTF8.GetBytes($msg)
$ws.SendAsync([ArraySegment[byte]]$b, 'Text', $true, $ct).Wait()
$buf = New-Object byte[] 262144
$r = $ws.ReceiveAsync([ArraySegment[byte]]$buf, $ct).Result
$res = [Text.Encoding]::UTF8.GetString($buf, 0, $r.Count) | ConvertFrom-Json
$ws.Dispose()
& $adb forward --remove tcp:9222 | Out-Null
"[$($page.url)] " + ($res.result.result.value | ConvertTo-Json -Compress)
