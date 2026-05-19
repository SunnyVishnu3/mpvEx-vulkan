param(
    [Parameter(Mandatory = $true)][string]$ApkPath
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $ApkPath)) {
    throw "APK not found: $ApkPath"
}

$zipalign = Get-Command "zipalign" -ErrorAction SilentlyContinue
if ($null -eq $zipalign) {
    $sdkRoot = $env:ANDROID_HOME
    if ([string]::IsNullOrWhiteSpace($sdkRoot)) {
        $sdkRoot = $env:ANDROID_SDK_ROOT
    }
    if ([string]::IsNullOrWhiteSpace($sdkRoot)) {
        $defaultSdkRoot = Join-Path $env:LOCALAPPDATA "Android/Sdk"
        if (Test-Path -LiteralPath $defaultSdkRoot) {
            $sdkRoot = $defaultSdkRoot
        }
    }
    if (-not [string]::IsNullOrWhiteSpace($sdkRoot)) {
        $zipalign = Get-ChildItem -LiteralPath (Join-Path $sdkRoot "build-tools") -Filter "zipalign.exe" -Recurse -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending |
            Select-Object -First 1
    }
}

if ($null -eq $zipalign) {
    throw "zipalign not found. Install Android SDK build-tools or set ANDROID_HOME."
}

$zipalignPath = if ($zipalign.Source) { $zipalign.Source } else { $zipalign.FullName }
& $zipalignPath -c -P 16 -v 4 $ApkPath
if ($LASTEXITCODE -ne 0) {
    throw "zipalign 16 KB verification failed for $ApkPath"
}

$workDir = Join-Path "logs/tmp/16kb-check" ([IO.Path]::GetFileNameWithoutExtension($ApkPath))
if (Test-Path -LiteralPath $workDir) {
    $resolvedWorkDir = (Resolve-Path -LiteralPath $workDir).Path
    $resolvedRoot = (Resolve-Path -LiteralPath "logs/tmp/16kb-check").Path
    if (-not $resolvedWorkDir.StartsWith($resolvedRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to clean unexpected path: $resolvedWorkDir"
    }
    Remove-Item -LiteralPath $workDir -Recurse -Force
}
New-Item -ItemType Directory -Force $workDir | Out-Null

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $ApkPath))
try {
    $entries = $archive.Entries | Where-Object { $_.FullName -like "lib/*.so" -or $_.FullName -like "lib/*/*.so" }
    foreach ($entry in $entries) {
        $outputPath = Join-Path $workDir ($entry.FullName -replace '/', [IO.Path]::DirectorySeparatorChar)
        New-Item -ItemType Directory -Force ([IO.Path]::GetDirectoryName($outputPath)) | Out-Null
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $outputPath, $true)
    }
} finally {
    $archive.Dispose()
}

$readelf = Get-Command "llvm-readelf" -ErrorAction SilentlyContinue
if ($null -eq $readelf) {
    $readelf = Get-Command "readelf" -ErrorAction SilentlyContinue
}

if ($null -eq $readelf) {
    Write-Warning "readelf/llvm-readelf not found; zipalign passed, ELF LOAD alignment not inspected."
    exit 0
}

$badFiles = @()
Get-ChildItem -LiteralPath $workDir -Filter "*.so" -Recurse | ForEach-Object {
    $output = & $readelf.Source -l $_.FullName
    $loadLines = $output | Select-String -Pattern "LOAD"
    if ($loadLines -match "0x1000") {
        $badFiles += $_.FullName
    }
}

if ($badFiles.Count -gt 0) {
    $badFiles | ForEach-Object { Write-Error "4 KB LOAD alignment found: $_" }
    throw "ELF 16 KB verification failed."
}

Write-Host "16 KB page-size verification passed for $ApkPath"
