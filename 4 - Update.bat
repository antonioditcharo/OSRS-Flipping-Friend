@echo off
title Flipping Friend - Update
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\apply-update.ps1"
pause
