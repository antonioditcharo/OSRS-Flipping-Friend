@echo off
title Flipping Friend - Status
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\status.ps1"
pause
