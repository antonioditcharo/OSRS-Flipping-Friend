@echo off
title Flipping Friend - ML Forecaster
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\install-ml-forecaster.ps1"
