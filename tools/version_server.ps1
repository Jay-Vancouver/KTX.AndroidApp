param(
    [string]$Version = '9.9.9',
    # A real APK to serve (e.g. a debug build with a higher versionCode); without it a dummy file is sent.
    [string]$ApkPath = ''
)
# Test server for the update check: /app/version.json and /app/*.apk on http://127.0.0.1:8099/, logging each request.
# Phone side: adb reverse tcp:8099 tcp:8099, and the debug extra debug_version_url.
$log = Join-Path $PSScriptRoot 'version_server.log'
$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add('http://127.0.0.1:8099/')
$listener.Start()
while ($listener.IsListening) {
    $ctx = $listener.GetContext()
    $path = $ctx.Request.Url.AbsolutePath
    Add-Content -Path $log -Value ('{0:HH:mm:ss} {1} UA={2}' -f (Get-Date), $path, $ctx.Request.UserAgent) -Encoding utf8
    if ($path -eq '/app/version.json') {
        $body = [Text.Encoding]::UTF8.GetBytes((@{ version = $Version; apk = "http://127.0.0.1:8099/app/ktx-driver-$Version.apk"; notes = "Test update $Version" } | ConvertTo-Json -Compress))
        $ctx.Response.ContentType = 'application/json'
    } elseif ($path -like '/app/*.apk') {
        $body = if ($ApkPath) { [IO.File]::ReadAllBytes($ApkPath) } else { [Text.Encoding]::ASCII.GetBytes('not a real apk') }
        $ctx.Response.ContentType = 'application/vnd.android.package-archive'
    } else {
        $ctx.Response.StatusCode = 404
        $body = [byte[]]@()
    }
    $ctx.Response.ContentLength64 = $body.Length
    $ctx.Response.OutputStream.Write($body, 0, $body.Length)
    $ctx.Response.Close()
}
