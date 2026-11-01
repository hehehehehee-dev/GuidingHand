[CmdletBinding()]
param(
    [string]$Serial = "",
    [switch]$InstallCandidate,
    [switch]$CaptureSafeScreen,
    [string]$ApkPath = "app/build/outputs/apk/debug/app-debug.apk",
    [string]$ReportDirectory = "build/outputs/phone-smoke"
)

$ErrorActionPreference = "Stop"
$candidatePackage = "vn.guidinghand.candidate"
$candidateService = "$candidatePackage/vn.guidinghand.GuidingHandService"
$safeCapturePackages = @($candidatePackage, "com.miui.home")
$startedAt = (Get-Date).ToUniversalTime()

function Resolve-Adb {
    $command = Get-Command adb -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    $sdkAdb = Join-Path $env:LOCALAPPDATA "Android/Sdk/platform-tools/adb.exe"
    if (Test-Path -LiteralPath $sdkAdb) {
        return $sdkAdb
    }

    throw "ADB was not found on PATH or in the default Android SDK location."
}

function Invoke-Adb {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $serialArguments = @()
    if ($Serial) {
        $serialArguments = @("-s", $Serial)
    }

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $output = & $script:adbPath @serialArguments @Arguments 2>&1 | Out-String
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorActionPreference
    if ($exitCode -ne 0) {
        throw "ADB failed: adb $($Arguments -join ' ')`n$output"
    }
    return $output.Trim()
}

function Get-MatchValue {
    param(
        [string]$Text,
        [string]$Pattern
    )

    $match = [regex]::Match($Text, $Pattern, [System.Text.RegularExpressions.RegexOptions]::Multiline)
    if ($match.Success) {
        return $match.Groups[1].Value.Trim()
    }
    return $null
}

$script:adbPath = Resolve-Adb
$deviceOutput = & $script:adbPath devices -l 2>&1 | Out-String
$devices = @([regex]::Matches($deviceOutput, '(?m)^(\S+)\s+device\b') | ForEach-Object { $_.Groups[1].Value })

if (-not $Serial) {
    if ($devices.Count -ne 1) {
        throw "Expected exactly one authorized Android device, found $($devices.Count)."
    }
    $Serial = $devices[0]
} elseif ($devices -notcontains $Serial) {
    throw "Android device '$Serial' is not connected and authorized."
}

if ($InstallCandidate) {
    $resolvedApk = (Resolve-Path -LiteralPath $ApkPath).Path
    $metadataPath = Join-Path (Split-Path -Parent $resolvedApk) "output-metadata.json"
    if (-not (Test-Path -LiteralPath $metadataPath)) {
        throw "Missing output-metadata.json beside the APK; refusing an unverified install."
    }
    $metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
    if ($metadata.applicationId -ne $candidatePackage) {
        throw "Refusing to install applicationId '$($metadata.applicationId)'; expected Candidate package '$candidatePackage'. Build with -PcandidateBuild=true first."
    }
    Invoke-Adb -Arguments @("install", "-r", $resolvedApk) | Out-Null
}

$packageDump = Invoke-Adb -Arguments @("shell", "dumpsys", "package", $candidatePackage)
if (-not $packageDump) {
    throw "Candidate package '$candidatePackage' is not installed."
}

$powerDump = Invoke-Adb -Arguments @("shell", "dumpsys", "power")
$policyDump = Invoke-Adb -Arguments @("shell", "dumpsys", "window", "policy")
$activityDump = Invoke-Adb -Arguments @("shell", "dumpsys", "activity", "activities")
$accessibilityEnabled = Invoke-Adb -Arguments @("shell", "settings", "get", "secure", "enabled_accessibility_services")
$accessibilityDump = Invoke-Adb -Arguments @("shell", "dumpsys", "accessibility")
$logMarker = "GUIDINGHAND_SMOKE_START_$([Guid]::NewGuid().ToString('N'))"
Invoke-Adb -Arguments @("shell", "log", "-t", "GuidingHandSmoke", $logMarker) | Out-Null

Start-Sleep -Seconds 8

$logAfter = Invoke-Adb -Arguments @("logcat", "-d", "-v", "brief")
$markerIndex = $logAfter.LastIndexOf($logMarker, [StringComparison]::Ordinal)
if ($markerIndex -lt 0) {
    throw "The bounded Logcat marker was not found; refusing to treat historical logs as current evidence."
}
$newLog = $logAfter.Substring($markerIndex + $logMarker.Length)

$foregroundPackage = Get-MatchValue -Text $activityDump -Pattern 'topResumedActivity=.*?\s([A-Za-z0-9._]+)/'
$versionName = Get-MatchValue -Text $packageDump -Pattern '^\s*versionName=(\S+)'
$versionCode = Get-MatchValue -Text $packageDump -Pattern '^\s*versionCode=(\d+)'
$lastUpdateTime = Get-MatchValue -Text $packageDump -Pattern '^\s*lastUpdateTime=(.+)$'
$awake = $powerDump -match 'mWakefulness=Awake'
$locked = -not ($policyDump -match '(?m)^\s*showing=false\s*$')
$serviceEnabled = $accessibilityEnabled -like "*$candidateService*"
$serviceCrashed = $accessibilityDump -match "Crashed services:\{[^}]*$([regex]::Escape($candidateService))"
$fatalCount = ([regex]::Matches($newLog, 'FATAL EXCEPTION')).Count
$attachedCount = ([regex]::Matches($newLog, 'GuidingHandTrace.*"event":"bubble_attached"')).Count
$detachedCount = ([regex]::Matches($newLog, 'GuidingHandTrace.*"event":"bubble_detached"')).Count
$unexpectedBubbleChurn = $awake -and -not $locked -and (($attachedCount -gt 1) -or ($detachedCount -gt 1))

New-Item -ItemType Directory -Force -Path $ReportDirectory | Out-Null
$safeTimestamp = $startedAt.ToString("yyyyMMdd-HHmmssZ")
$screenshotPath = $null

if ($CaptureSafeScreen -and $safeCapturePackages -contains $foregroundPackage) {
    $remoteScreenshot = "/sdcard/guidinghand-smoke-$safeTimestamp.png"
    $localScreenshot = Join-Path $ReportDirectory "safe-screen-$safeTimestamp.png"
    Invoke-Adb -Arguments @("shell", "screencap", "-p", $remoteScreenshot) | Out-Null
    Invoke-Adb -Arguments @("pull", $remoteScreenshot, $localScreenshot) | Out-Null
    Invoke-Adb -Arguments @("shell", "rm", $remoteScreenshot) | Out-Null
    $screenshotPath = (Resolve-Path -LiteralPath $localScreenshot).Path
}

$checks = [ordered]@{
    candidateInstalled = $true
    candidateIdentity = $versionName -like "*-candidate"
    deviceAwake = $awake
    deviceUnlocked = -not $locked
    accessibilityEnabled = $serviceEnabled
    accessibilityNotCrashed = -not $serviceCrashed
    noNewFatalException = $fatalCount -eq 0
    overlayStableWhileIdle = -not $unexpectedBubbleChurn
}

$report = [ordered]@{
    schemaVersion = 1
    collectedAtUtc = (Get-Date).ToUniversalTime().ToString("o")
    evidenceType = "real_phone_read_only"
    inputInjectionUsed = $false
    userInteractionClaimed = $false
    deviceSerialRecorded = $false
    package = $candidatePackage
    versionName = $versionName
    versionCode = $versionCode
    lastUpdateTime = $lastUpdateTime
    foregroundPackage = $foregroundPackage
    observationSeconds = 8
    newFatalExceptionCount = $fatalCount
    bubbleAttachedCount = $attachedCount
    bubbleDetachedCount = $detachedCount
    screenshot = $screenshotPath
    checks = $checks
    passed = -not ($checks.Values -contains $false)
    limitations = @(
        "Xiaomi blocks ADB input injection with INJECT_EVENTS on this device.",
        "This report does not claim that a person tapped the controls or completed a task.",
        "Speech, ring accuracy, and target-app step advancement require a user-driven run."
    )
}

$reportPath = Join-Path $ReportDirectory "phone-smoke-$safeTimestamp.json"
[System.IO.File]::WriteAllText(
    (Join-Path (Get-Location) $reportPath),
    ($report | ConvertTo-Json -Depth 6),
    [System.Text.UTF8Encoding]::new($false)
)

$report | ConvertTo-Json -Depth 6
if (-not $report.passed) {
    exit 1
}
