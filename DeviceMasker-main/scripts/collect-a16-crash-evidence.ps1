param(
    [string]$Device = "",
    [string]$TargetPackage = "flar2.devcheck",
    [string]$OutputDir = "logs/device"
)

$ErrorActionPreference = "Continue"
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -Scope Global -ErrorAction SilentlyContinue) {
    $global:PSNativeCommandUseErrorActionPreference = $false
}
New-Item -ItemType Directory -Force $OutputDir | Out-Null

$stamp = Get-Date -Format "yyyy-MM-dd-HHmmss"
$safePackage = $TargetPackage -replace '[^a-zA-Z0-9._-]', '_'
$prefix = Join-Path $OutputDir "$stamp-a16-$safePackage"
$adbArgs = @()
if ($Device.Trim().Length -gt 0) {
    $adbArgs += @("-s", $Device)
}

function Invoke-AdbCapture {
    param(
        [Parameter(Mandatory = $true)][string[]]$Args,
        [Parameter(Mandatory = $true)][string]$OutputPath
    )

    $output = & adb @adbArgs @Args 2>&1
    $exitCode = $LASTEXITCODE
    $output | Set-Content $OutputPath
    "exitCode=$exitCode" | Add-Content $OutputPath
}

Invoke-AdbCapture -Args @("shell", "getprop") -OutputPath "$prefix-getprop.txt"
Invoke-AdbCapture -Args @("shell", "getconf", "PAGE_SIZE") -OutputPath "$prefix-page-size.txt"
Invoke-AdbCapture -Args @("shell", "dumpsys", "package", $TargetPackage) -OutputPath "$prefix-package.txt"
Invoke-AdbCapture -Args @("shell", "pm", "path", $TargetPackage) -OutputPath "$prefix-apk-path.txt"
& adb @adbArgs logcat -c
Invoke-AdbCapture -Args @("shell", "am", "force-stop", $TargetPackage) -OutputPath "$prefix-force-stop.txt"
Invoke-AdbCapture -Args @("shell", "monkey", "-p", $TargetPackage, "-c", "android.intent.category.LAUNCHER", "1") -OutputPath "$prefix-launch.txt"
Start-Sleep -Seconds 8
Invoke-AdbCapture -Args @("shell", "pidof", $TargetPackage) -OutputPath "$prefix-pid.txt"
Invoke-AdbCapture -Args @("logcat", "-d", "-b", "main,system,crash,events", "-v", "threadtime") -OutputPath "$prefix-logcat.txt"
Invoke-AdbCapture -Args @("shell", "ls", "-lt", "/data/tombstones") -OutputPath "$prefix-tombstones-list.txt"

Select-String "$prefix-logcat.txt" `
    -Pattern "Accessing hidden|non-SDK|NoSuchMethodError|NoSuchFieldError|ClassNotFoundException|VerifyError|UnsatisfiedLinkError|FATAL EXCEPTION|AndroidRuntime|signal [0-9]+|Runtime.exit|System.exit" `
    | Set-Content "$prefix-crash-signatures.txt"

Write-Host "Wrote evidence files with prefix: $prefix"
