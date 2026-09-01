$ErrorActionPreference = "Stop"
$PSNativeCommandUseErrorActionPreference = $true

$projectRoot = Split-Path -Parent $PSScriptRoot
$outputDirectory = Join-Path $projectRoot "android\app\libs"
$coreVersion = if ($env:WARPSCOUT_CORE_VERSION) { $env:WARPSCOUT_CORE_VERSION } else { "dev" }
$upstreamVersion = if ($env:WARPSCOUT_UPSTREAM_TAG) { $env:WARPSCOUT_UPSTREAM_TAG } else { "v0.16.0" }
$goMobileVersion = "v0.0.0-20260818145002-f020ddb2de58"
$localAndroidSDK = Join-Path $projectRoot ".cache\android-sdk"
$temporarySDKDrive = $null

if (-not $env:ANDROID_HOME -and (Test-Path $localAndroidSDK)) {
    if ($env:OS -eq "Windows_NT" -and $localAndroidSDK -match "\s") {
        foreach ($drive in @("W:", "V:", "U:", "T:", "S:")) {
            if (-not (Test-Path $drive)) {
                subst.exe $drive $localAndroidSDK
                if ($LASTEXITCODE -eq 0) {
                    $temporarySDKDrive = $drive
                    $env:ANDROID_HOME = "$drive\"
                    break
                }
            }
        }
        if (-not $temporarySDKDrive) { throw "no free drive letter for the local Android SDK" }
    } else {
        $env:ANDROID_HOME = $localAndroidSDK
    }
}
if (-not $env:ANDROID_SDK_ROOT -and $env:ANDROID_HOME) {
    $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
}

try {
    New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
    go install "golang.org/x/mobile/cmd/gomobile@$goMobileVersion"
    if ($LASTEXITCODE -ne 0) { throw "go install failed with exit code $LASTEXITCODE" }
    gomobile init
    if ($LASTEXITCODE -ne 0) { throw "gomobile init failed with exit code $LASTEXITCODE" }
    Push-Location $projectRoot
    try {
        gomobile bind `
            "-target=android/arm64,android/arm,android/amd64" `
            "-androidapi=26" `
            -ldflags="-X github.com/vernette/warpscout/mobileapi.coreVersion=$coreVersion -X github.com/vernette/warpscout/mobileapi.upstreamVersion=$upstreamVersion" `
            -o (Join-Path $outputDirectory "warpscout.aar") `
            "./mobileapi"
        if ($LASTEXITCODE -ne 0) { throw "gomobile bind failed with exit code $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
} finally {
    if ($temporarySDKDrive) {
        subst.exe $temporarySDKDrive /D
    }
}
