# Starts RuneLite with sideloaded plugins enabled.
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

# injected-client carries the game itself and must come before the rest; the remaining order is
# irrelevant because the jars do not overlap.
$jars = Get-ChildItem (Join-Path $repository '*.jar') | Sort-Object {
    if ($_.Name -like 'injected-client*') { 0 }
    elseif ($_.Name -like 'client-*') { 1 }
    else { 2 }
}
$classpath = ($jars | ForEach-Object { $_.FullName }) -join ';'

$root = Split-Path -Parent $PSScriptRoot
$installed = $null
$clientJar = $jars | Where-Object { $_.Name -match '^client-(\d[\d.]*)\.jar$' } | Select-Object -First 1
if ($clientJar -and $clientJar.Name -match '^client-(\d[\d.]*)\.jar$') {
    $installed = $Matches[1]

    # Warn when RuneLite has updated past the version the plugin was compiled against. It will
    # usually still load, but an API change is exactly the kind of thing that surfaces as a
    # confusing crash rather than a clear error.
    $buildFile = Join-Path $root 'build.gradle'
    $pinned = (Select-String -Path $buildFile -Pattern "runeLiteVersion\s*=\s*'([\d.]+)'").Matches.Groups[1].Value
    if ($installed -ne $pinned) {
        Write-Host "RuneLite is now $installed but the plugin was built against $pinned." -ForegroundColor Yellow
        Write-Host "Update ext.runeLiteVersion in build.gradle and re-run tools\install.ps1." -ForegroundColor Yellow
    }
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

$sideloaded = Join-Path $env:USERPROFILE '.runelite\sideloaded-plugins'
if (-not (Test-Path (Join-Path $sideloaded 'osrs-flipping-friend.jar'))) {
    Write-Host 'The plugin is not installed yet. Run tools\install.ps1 first.' -ForegroundColor Yellow
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
