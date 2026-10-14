# Starts RuneLite with sideloaded plugins enabled. CheckOnly validates without launch or downloads.
#
# RuneLite deliberately refuses developer mode when it is started by RuneLite.exe: the check is
# `options.has("developer-mode") && RuneLiteProperties.getLauncherVersion() == null`, and the
# launcher always sets that property. So the client has to be started directly, which is all this
# script does — it reuses the exact client and libraries the launcher already downloaded, so there
# is nothing extra to install and no second copy of RuneLite to keep updated.
#
# Jagex accounts: launching outside the Jagex Launcher means the usual login tokens are not in the
# environment. RuneLite's documented answer is credentials.properties, written once by passing
# --insecure-write-credentials to the launcher. See README.md, and delete it with
# tools\cleanup.ps1 when you are done.

param([switch] $CheckOnly)
$ErrorActionPreference = 'Stop'

$repository = Join-Path $env:USERPROFILE '.runelite\repository2'
if (-not (Test-Path $repository)) {
    Write-Host "Could not find $repository." -ForegroundColor Red
    Write-Host 'Run RuneLite once through its normal launcher first, so it downloads the client.'
    exit 1
}

$java = Join-Path $env:LOCALAPPDATA 'RuneLite\jre\bin\java.exe'
if (-not (Test-Path $java)) {
    $java = 'java'
    Write-Host 'Using java from PATH (RuneLite bundled runtime not found).' -ForegroundColor Yellow
}

# RuneLite's repository retains old versions. Enumerating *.jar mixes incompatible API classes.
# Use the official bootstrap's exact Windows artifact set; never delete or repair that cache.
try {
    $bootstrap = (Invoke-WebRequest -Uri 'https://static.runelite.net/bootstrap.json' -UseBasicParsing -TimeoutSec 20).Content | ConvertFrom-Json
} catch { throw "Could not verify RuneLite bootstrap: $($_.Exception.Message). No client was launched." }
$installed = [string]$bootstrap.version
if ($installed -notmatch '^\d+(\.\d+)+$' -or -not $bootstrap.artifacts) { throw 'RuneLite bootstrap is incomplete. No client was launched.' }
$root = Split-Path -Parent $PSScriptRoot
$buildFile = Join-Path $root 'build.gradle'
$pinned = (Select-String -LiteralPath $buildFile -Pattern "runeLiteVersion\s*=\s*'([\d.]+)'").Matches.Groups[1].Value
if ($installed -ne $pinned) {
    throw "RuneLite is $installed but this source targets $pinned. A compatible project update is required. No client was launched."
}
$sideloaded = Join-Path $env:USERPROFILE '.runelite\sideloaded-plugins'
$pluginJar = Join-Path $sideloaded 'osrs-flipping-friend.jar'
if (-not (Test-Path -LiteralPath $pluginJar -PathType Leaf)) { throw 'The installed plugin is missing. Use the numbered setup wrapper.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($pluginJar)
try {
    $manifestEntry = $archive.GetEntry('META-INF/MANIFEST.MF')
    if (-not $manifestEntry) { throw 'Installed plugin has no API version metadata. Close RuneLite and use 4 - Update.' }
    $reader = [IO.StreamReader]::new($manifestEntry.Open())
    try { $pluginManifest = $reader.ReadToEnd() } finally { $reader.Dispose() }
} finally { $archive.Dispose() }
$apiMetadata = [regex]::Match($pluginManifest, '(?m)^RuneLite-Api-Version:\s*(\d[\d.]*)\s*$')
if (-not $apiMetadata.Success) { throw 'Installed plugin has no API version metadata. Close RuneLite and use 4 - Update.' }
if ($apiMetadata.Groups[1].Value -ne $installed) {
    throw "RuneLite is $installed but the installed plugin targets $($apiMetadata.Groups[1].Value). Close RuneLite and use 4 - Update. No client was launched."
}
$architecture = $env:PROCESSOR_ARCHITEW6432
if (-not $architecture) { $architecture = $env:PROCESSOR_ARCHITECTURE }
if (-not $architecture) { $architecture = if ([IntPtr]::Size -eq 8) { 'AMD64' } else { 'x86' } }
$arch = switch ($architecture.ToUpperInvariant()) { 'AMD64' { 'amd64' } 'X86' { 'x86' } 'ARM64' { 'aarch64' } default { throw "Unsupported Windows architecture: $architecture" } }
$selected = @()
foreach ($artifact in $bootstrap.artifacts) {
    if ($artifact.platform -and -not @($artifact.platform | Where-Object {
        $_.name -eq 'win' -and (-not $_.arch -or $_.arch -eq $arch)
    }).Count) { continue }
    if ($artifact.name -notmatch '^[A-Za-z0-9_-][A-Za-z0-9._-]*\.jar$') { throw 'Invalid RuneLite artifact name; no cache file was accessed.' }
    if ($artifact.hash -notmatch '^[0-9a-fA-F]{64}$' -or $artifact.size -le 0) { throw "Invalid RuneLite artifact metadata: $($artifact.name)" }
    if ($selected.name -contains $artifact.name) { throw "Duplicate RuneLite artifact name: $($artifact.name)" }
    $selected += $artifact
}
foreach ($required in @("client-$installed.jar", "injected-client-$installed.jar", "runelite-api-$installed-runtime.jar")) {
    if ($selected.name -notcontains $required) { throw "Current RuneLite artifact is missing from bootstrap: $required" }
}
$jars = @()
foreach ($artifact in $selected) {
    $path = Join-Path $repository $artifact.name
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Current RuneLite artifact is missing: $($artifact.name). Refresh through the normal RuneLite launcher; this script changes no cached JARs." }
    $stream = [IO.File]::OpenRead($path)
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $hash = ([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-', '').ToLowerInvariant() }
    finally { $stream.Dispose(); $sha.Dispose() }
    if ((Get-Item -LiteralPath $path).Length -ne $artifact.size -or $hash -ne $artifact.hash.ToLowerInvariant()) {
        throw "Current RuneLite artifact does not match official bootstrap: $($artifact.name). No cache repair or client launch was attempted."
    }
    $jars += $path
}
$classpath = $jars -join ';'
Write-Host "RuneLite $installed and the installed plugin API match; cached artifacts verified."
if ($CheckOnly) {
    Write-Host 'Compatibility check passed. No client started and no files changed.'
    return
}

# The launcher ships a stripped "-runtime" API jar with the gameval classes removed, because their
# contents are compile-time constants that javac inlines. That is fine for plugins, but RuneLite's
# own Developer Tools plugin loads some of those classes by name at runtime and dies without them.
# Appending the full API jar fixes that. It is appended rather than prepended so the launcher's own
# jars still win for everything they both define.
if ($installed) {
    $extras = Join-Path $root 'build\runtime-extras'
    $apiJar = Join-Path $extras "runelite-api-$installed.jar"

    if (-not (Test-Path $apiJar)) {
        New-Item -ItemType Directory -Force -Path $extras | Out-Null
        $url = "https://repo.runelite.net/net/runelite/runelite-api/$installed/runelite-api-$installed.jar"
        Write-Host "Fetching the full API jar for developer tools ($installed)..." -ForegroundColor Cyan
        try {
            Invoke-WebRequest -Uri $url -OutFile $apiJar -UseBasicParsing
        } catch {
            Write-Host "Could not download it; RuneLite's Developer Tools plugin may fail to start." -ForegroundColor Yellow
            Write-Host "This does not affect Flipping Friend." -ForegroundColor Yellow
        }
    }

    if (Test-Path $apiJar) {
        $classpath = "$classpath;$apiJar"
    }
}

$credentials = Join-Path $env:USERPROFILE '.runelite\credentials.properties'
if (-not (Test-Path $credentials)) {
    Write-Host ''
    Write-Host 'No credentials.properties found.' -ForegroundColor Yellow
    Write-Host 'If you use a Jagex account you will only see the old login screen. See README.md.'
    Write-Host ''
}

Write-Host 'Starting RuneLite in developer mode...' -ForegroundColor Cyan

# -ea is required: RuneLite refuses to enable developer mode without assertions on.
& $java -ea -Xmx1g -Xss2m -cp $classpath net.runelite.client.RuneLite --developer-mode
