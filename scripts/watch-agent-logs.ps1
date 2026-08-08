# Live-tails Saarthi's agent step logs (SaarthiAgent + SaarthiDebugAction
# tags) from a connected device or emulator. Run directly to tail in the
# current terminal, or double-click watch-agent-logs.bat to pop this open
# in its own window.
#
# Usage:
#   powershell -File scripts\watch-agent-logs.ps1
#   powershell -File scripts\watch-agent-logs.ps1 -DeviceSerial emulator-5554

param(
    [string]$DeviceSerial
)

$ErrorActionPreference = "Stop"

function Resolve-Adb {
    # Prefer the Android SDK's own platform-tools adb.exe over whatever
    # "adb" resolves to first on PATH — this machine has at least two
    # other adb.exe installs (a Chocolatey shim and a scrcpy-bundled
    # copy), and a client from a different install than whichever adb.exe
    # started the currently-running server can hang on "waiting for
    # device" instead of cleanly restarting it.
    $sdkDir = $env:ANDROID_HOME
    if (-not $sdkDir) { $sdkDir = $env:LOCALAPPDATA + "\Android\Sdk" }
    $candidate = Join-Path $sdkDir "platform-tools\adb.exe"
    if (Test-Path $candidate) { return $candidate }

    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }

    throw "Could not find adb. Set ANDROID_HOME or add platform-tools to PATH."
}

$adb = Resolve-Adb

if (-not $DeviceSerial) {
    # @(...) forces an array even when exactly one device is connected —
    # without it, a single-result pipeline collapses to a plain string,
    # and $serials[0] below would index into that STRING's characters
    # ("emulator-5554"[0] is "e") instead of the array's one element.
    $lines = @(& $adb devices | Select-Object -Skip 1 | Where-Object { $_.Trim() -ne "" })
    $serials = @($lines | ForEach-Object { ($_ -split "\s+")[0] })
    if ($serials.Count -eq 0) {
        Write-Host "No devices/emulators connected." -ForegroundColor Red
        exit 1
    } elseif ($serials.Count -eq 1) {
        $DeviceSerial = $serials[0]
    } else {
        Write-Host "Multiple devices connected - pass -DeviceSerial:" -ForegroundColor Yellow
        $serials | ForEach-Object { Write-Host "  $_" }
        exit 1
    }
}

$Host.UI.RawUI.WindowTitle = "Saarthi Agent - Live Logs ($DeviceSerial)"
Write-Host "Watching $DeviceSerial for Saarthi agent tasks... (open the app and send a task)" -ForegroundColor Cyan

& $adb -s $DeviceSerial logcat -c
& $adb -s $DeviceSerial logcat -v time -s SaarthiAgent:* SaarthiDebugAction:*
