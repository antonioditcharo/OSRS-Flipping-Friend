@echo off
title Flipping Friend - Learning Monitor
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\monitor.ps1"
if errorlevel 1 pause
