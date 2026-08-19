param(
    [string[]]$Tasks = @(':app:testDebugUnitTest', ':app:assembleDebug'),
    [switch]$Clean,
    [switch]$Install,
    [string]$Serial
)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$buildRoot = Join-Path ([System.IO.Path]::GetTempPath()) 'warpscout-android-build'
$expectedTarget = [System.IO.Path]::GetFullPath($repoRoot).TrimEnd('\')

if (Test-Path -LiteralPath $buildRoot) {
    $existing = Get-Item -LiteralPath $buildRoot -Force
    $existingTarget = @($existing.Target) | Select-Object -First 1
    if ($existing.LinkType -ne 'Junction' -or [System.IO.Path]::GetFullPath($existingTarget).TrimEnd('\') -ne $expectedTarget) {
        throw "Build path is occupied by another target: $buildRoot"
    }
} else {
    New-Item -ItemType Junction -Path $buildRoot -Target $repoRoot | Out-Null
}

$androidHome = Join-Path $buildRoot '.cache\android-sdk'
$gradleHome = Join-Path $buildRoot '.cache\gradle-home'
$androidUserHome = Join-Path $buildRoot '.cache\android-user-build'
$debugKeystore = Join-Path $buildRoot '.cache\debug.keystore'
$androidProject = Join-Path $buildRoot 'android'

if (-not (Test-Path -LiteralPath $androidHome)) {
    throw "Android SDK is missing: $androidHome"
}

$gradleTasks = [System.Collections.Generic.List[string]]::new()
if ($Clean) {
    $gradleTasks.Add(':app:clean')
}
$gradleTasks.AddRange([string[]]$Tasks)

$env:ANDROID_HOME = $androidHome
$env:ANDROID_SDK_ROOT = $androidHome
$env:ANDROID_USER_HOME = $androidUserHome
if (Test-Path -LiteralPath $debugKeystore) {
    $env:WARPSCOUT_DEBUG_KEYSTORE = $debugKeystore
}

Push-Location $androidProject
try {
    & .\gradlew.bat --no-daemon --gradle-user-home $gradleHome @gradleTasks
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

if ($Install) {
    $adb = Join-Path $androidHome 'platform-tools\adb.exe'
    $apk = Join-Path $androidProject 'app\build\outputs\apk\debug\app-universal-debug.apk'
    if (-not (Test-Path -LiteralPath $apk)) {
        throw "Debug APK is missing: $apk"
    }

    $adbArguments = @()
    if ($Serial) {
        $adbArguments += @('-s', $Serial)
    }
    & $adb @adbArguments install -r $apk
    if ($LASTEXITCODE -ne 0) {
        throw "ADB install failed with exit code $LASTEXITCODE"
    }
}
