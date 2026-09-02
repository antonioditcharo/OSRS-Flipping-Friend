@echo off
title Flipping Friend - Background Learning
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\install-daemon.ps1"
