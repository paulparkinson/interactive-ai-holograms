@echo off
REM Batch wrapper for PowerShell script - bypasses execution policy
REM This allows anyone to run the script without changing system settings

powershell.exe -ExecutionPolicy Bypass -File "%~dp0build_and_run_with_voice.ps1"
