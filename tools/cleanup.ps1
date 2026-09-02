# Removes the Jagex login tokens that were written to disk for development.
#
# credentials.properties can be used to log into the account without the password, so it should not
# outlive the reason it was created. Run this when you are finished, and revoke the session itself
# with "End sessions" under account settings on runescape.com if you want to be thorough.

$ErrorActionPreference = 'Stop'

$credentials = Join-Path $env:USERPROFILE '.runelite\credentials.properties'

if (Test-Path $credentials) {
    Remove-Item $credentials -Force
    Write-Host "Deleted $credentials" -ForegroundColor Green
    Write-Host 'RuneLite will go back to using the Jagex Launcher normally.'
} else {
    Write-Host 'No credentials.properties found; nothing to clean up.'
}

Write-Host ''
Write-Host 'To also remove the plugin itself:'
Write-Host "  Remove-Item `"$env:USERPROFILE\.runelite\sideloaded-plugins\osrs-flipping-friend.jar`""
