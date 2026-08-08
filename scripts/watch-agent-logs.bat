@echo off
REM Double-click this to pop open a new window live-tailing Saarthi's
REM agent step logs. See watch-agent-logs.ps1 for the actual logic.
start "Saarthi Agent Logs" powershell -NoExit -ExecutionPolicy Bypass -File "%~dp0watch-agent-logs.ps1"
