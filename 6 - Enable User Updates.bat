@echo off
title Flipping Friend - Normal User Updates
cd /d "%~dp0"
echo Close RuneLite first. Run this normally; administrator approval is not required.
echo This verifies a backup and updates both JARs using the current user's companion process.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\apply-update.ps1" -Unattended -UserProcess
pause
