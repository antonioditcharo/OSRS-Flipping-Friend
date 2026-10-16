@echo off
title Flipping Friend - One-Time User Update Access
cd /d "%~dp0"
echo This grants your Windows user control of this installation's non-elevated companion task.
echo For an existing protected task, right-click this file and choose Run as administrator once.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\enable-user-updates.ps1"
pause
