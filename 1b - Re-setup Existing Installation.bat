@echo off
title Flipping Friend - Re-setup Existing Installation
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\setup.ps1"
if errorlevel 1 pause
