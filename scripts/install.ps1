param(
    [string]$Adb = "adb",
    [string]$Apk = "$PSScriptRoot\..\build\duokan-night.apk",
    [string]$Serial
)
$ErrorActionPreference = "Stop"
$targetArguments = @()
if ($Serial) { $targetArguments = @("-s", $Serial) }
if (-not (Test-Path -LiteralPath $Apk)) { throw "Build the signed helper APK first." }
& $Adb @targetArguments install -r $Apk
if ($LASTEXITCODE -ne 0) { throw "Helper installation failed." }
& $Adb @targetArguments shell am start -n "io.github.muyuchen.duokannight/.LauncherActivity"
if ($LASTEXITCODE -ne 0) { throw "Helper launch failed." }
