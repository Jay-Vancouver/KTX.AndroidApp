# Minimal OsmAnd receiver for testing: logs each POST body with the receive time, answers empty 200.
$log = Join-Path $PSScriptRoot 'gps_received.log'
$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add('http://127.0.0.1:8099/')
$listener.Start()
while ($listener.IsListening) {
    $ctx = $listener.GetContext()
    $reader = New-Object System.IO.StreamReader($ctx.Request.InputStream)
    $body = $reader.ReadToEnd()
    $line = '{0:HH:mm:ss} {1} {2} UA={3} {4}' -f (Get-Date), $ctx.Request.HttpMethod, $ctx.Request.Url.AbsolutePath, $ctx.Request.UserAgent, $body
    Add-Content -Path $log -Value $line -Encoding utf8
    $ctx.Response.StatusCode = 200
    $ctx.Response.Close()
}
