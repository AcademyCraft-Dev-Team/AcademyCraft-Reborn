# close-startup-dialogs.ps1
#
# Reusable module: waits for the isolated Minecraft client window, dismisses the
# startup "future/deprecated method" dialog (keyboard first, then mouse clicks),
# and stops as soon as latest.log proves world creation/loading continued.
#
# Usage (dot-source for the function, or run it directly):
#   . validation/structure_stress/close-startup-dialogs.ps1
#   Invoke-StartupDialogDismissal -ClientDir <dir> -DriverLog <log> -JoinWaitSeconds 420
#
#   powershell -ExecutionPolicy Bypass -File validation/structure_stress/close-startup-dialogs.ps1 `
#       -ClientDir run/sable-compat/stress-client-r1/client -DriverLog build/stress-client-r1-driver.log
#
# Evidence is appended to the driver log:
#   DIALOG_SCAN     one line per poll (window handle/title + observed log marker)
#   DIALOG_ACTION   one line per dismissal attempt (keys/clicks that were posted)
#   DIALOG_RESULT   final "dialog dismissed" verdict plus the latest.log evidence line
#   DIALOG_RESULT   dialog_dismissed=false when the deadline expires first
#
# The dialog has no OS child controls (the client renders it inside its GLFW
# window), so locating a literal close button is done by posting clicks at
# size-relative candidate points (center, lower center, top-right) instead of
# enumerating window controls. Keyboard Escape/Enter probes run first.

param(
    [string]$ClientDir,
    [string]$DriverLog,
    [int]$JoinWaitSeconds = 420,
    [int]$PollMilliseconds = 2500,
    [switch]$NoDialogAutomation
)

$ErrorActionPreference = 'Continue'

if (-not ('AcademyDialogWin32' -as [type])) {
    Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class AcademyDialogWin32 {
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X, Y; }
    [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);
    [DllImport("user32.dll")] public static extern uint MapVirtualKey(uint uCode, uint uMapType);
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr hWnd, out RECT rect);
    [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr hWnd, ref POINT point);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int X, int Y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint dwFlags, uint dx, uint dy, uint dwData, UIntPtr dwExtraInfo);
}
"@
}

$script:Win32 = [AcademyDialogWin32]
$script:WM_KEYDOWN = 0x100
$script:WM_KEYUP = 0x101
$script:WM_LBUTTONDOWN = 0x201
$script:WM_LBUTTONUP = 0x202
$script:VK_ESCAPE = 0x1B
$script:VK_RETURN = 0x0D
$script:WORLD_READY_PATTERN = 'Preparing spawn area|logged in with entity id|joined the game|Saving chunks for level'

function Write-DialogLog {
    param([string]$LogPath, [string]$Text)
    $stamp = (Get-Date).ToString('HH:mm:ss.fff')
    Add-Content -LiteralPath $LogPath -Value "[$stamp] $Text"
}

function Find-GameWindow {
    return Get-Process -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowHandle -ne 0 -and $_.MainWindowTitle -match '(?i)minecraft' } |
        Select-Object -First 1
}

function Get-ClientSize {
    param([IntPtr]$Handle)
    $rect = New-Object AcademyDialogWin32+RECT
    if (-not $script:Win32::GetClientRect($Handle, [ref]$rect)) { return $null }
    return @{ Width = $rect.Right - $rect.Left; Height = $rect.Bottom - $rect.Top }
}

function Post-DialogKey {
    param([IntPtr]$Handle, [int]$VirtualKey)
    $scan = $script:Win32::MapVirtualKey([uint32]$VirtualKey, 0)
    $down = [IntPtr](1 -bor ($scan -shl 16))
    $up = [IntPtr](1 -bor ($scan -shl 16) -bor 0x40000000 -bor [int64]0x80000000)
    $script:Win32::PostMessage($Handle, $script:WM_KEYDOWN, [IntPtr]$VirtualKey, $down) | Out-Null
    Start-Sleep -Milliseconds 60
    $script:Win32::PostMessage($Handle, $script:WM_KEYUP, [IntPtr]$VirtualKey, $up) | Out-Null
    Start-Sleep -Milliseconds 60
}

function Post-DialogClick {
    param([IntPtr]$Handle, [int]$X, [int]$Y)
    $packed = [IntPtr](($X -band 0xFFFF) -bor (($Y -band 0xFFFF) -shl 16))
    $script:Win32::PostMessage($Handle, $script:WM_LBUTTONDOWN, [IntPtr]1, $packed) | Out-Null
    Start-Sleep -Milliseconds 60
    $script:Win32::PostMessage($Handle, $script:WM_LBUTTONUP, [IntPtr]0, $packed) | Out-Null
    Start-Sleep -Milliseconds 60
}

# GLFW ignores posted mouse messages for this modal screen; the button only reacts to
# real system input. Moves the shared cursor, clicks once and restores nothing.
function Invoke-RealDialogClick {
    param([IntPtr]$Handle, [int]$X, [int]$Y)
    $script:Win32::SetForegroundWindow($Handle) | Out-Null
    Start-Sleep -Milliseconds 250
    $point = New-Object AcademyDialogWin32+POINT
    $point.X = $X
    $point.Y = $Y
    if (-not $script:Win32::ClientToScreen($Handle, [ref]$point)) { return $false }
    $script:Win32::SetCursorPos($point.X, $point.Y) | Out-Null
    Start-Sleep -Milliseconds 250
    $script:Win32::mouse_event(0x0002, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 120
    $script:Win32::mouse_event(0x0004, 0, 0, 0, [UIntPtr]::Zero)
    return $true
}

function Get-LatestLogEvidence {
    param([string]$ClientDir, [int]$Tail = 400, [DateTime]$NotBefore = [DateTime]::MinValue)
    $latest = Join-Path $ClientDir 'logs\latest.log'
    if (-not (Test-Path -LiteralPath $latest)) { return $null }
    try {
        # A leftover log from an earlier run must never count as this run's evidence.
        if ((Get-Item -LiteralPath $latest).LastWriteTime -lt $NotBefore) { return $null }
        $tailLines = Get-Content -LiteralPath $latest -Tail $Tail -ErrorAction Stop
    } catch {
        return $null
    }
    foreach ($line in $tailLines) {
        if ($line -match $script:WORLD_READY_PATTERN) {
            return $line.Trim()
        }
    }
    return $null
}

# Waits for world creation/loading to continue, dismissing the startup dialog.
# Returns $true when a world-ready evidence line was observed.
function Invoke-StartupDialogDismissal {
    param(
        [Parameter(Mandatory = $true)][string]$ClientDir,
        [Parameter(Mandatory = $true)][string]$DriverLog,
        [int]$JoinWaitSeconds = 420,
        [int]$PollMilliseconds = 2500,
        [switch]$NoDialogAutomation
    )
    Write-DialogLog $DriverLog 'DIALOG_WATCH start'
    $watchStartedAt = Get-Date
    $deadline = (Get-Date).AddSeconds($JoinWaitSeconds)
    $attempt = 0
    $lastWindowTitle = ''
    while ((Get-Date) -lt $deadline) {
        $evidence = Get-LatestLogEvidence $ClientDir -NotBefore $watchStartedAt
        if ($evidence) {
            Write-DialogLog $DriverLog ("DIALOG_RESULT dialog dismissed=true attempts={0} evidence='{1}'" -f $attempt, $evidence)
            return $true
        }
        $game = Find-GameWindow
        if ($null -ne $game) {
            if ($game.MainWindowTitle -ne $lastWindowTitle) {
                $lastWindowTitle = $game.MainWindowTitle
                Write-DialogLog $DriverLog ("DIALOG_SCAN window pid={0} title='{1}'" -f $game.Id, $game.MainWindowTitle)
            }
            if (-not $NoDialogAutomation) {
                $attempt++
                $handle = $game.MainWindowHandle
                # Keyboard probes first: Escape then Enter (posted; kept for parity with
                # the original driver and harmless while the modal screen has focus).
                Post-DialogKey $handle $script:VK_ESCAPE
                Post-DialogKey $handle $script:VK_RETURN
                # The "Proceed to main menu" button sits left of center on the second
                # button row (NeoForge LoadingErrorScreen). Its pixel center for GUI scale
                # s is (windowWidth/4 + 22.5*s, windowHeight - 14*s); the right half is
                # "Quit" and is never clicked. Clicks must be real system input.
                $size = Get-ClientSize $handle
                $clicks = @()
                if ($null -ne $size -and $size.Width -gt 40 -and $size.Height -gt 40) {
                    $points = @(
                        @([int]($size.Width / 4 + 45), [int]($size.Height - 28)),
                        @([int]($size.Width / 4 + 23), [int]($size.Height - 14)),
                        @([int]($size.Width / 4 + 68), [int]($size.Height - 42)),
                        @([int]($size.Width / 4 + 90), [int]($size.Height - 56)),
                        @([int]($size.Width / 2), [int]($size.Height * 0.62))
                    )
                    $point = $points[($attempt - 1) % $points.Count]
                    $real = Invoke-RealDialogClick $handle $point[0] $point[1]
                    $clicks = @("$($point[0])x$($point[1])" + $(if ($real) { '/real' } else { '/failed' }))
                }
                Write-DialogLog $DriverLog ("DIALOG_ACTION attempt={0} escape=1 enter=1 clicks={1} window={2}x{3}" -f `
                        $attempt, ($clicks -join ','), $(if ($size) { $size.Width } else { 0 }), $(if ($size) { $size.Height } else { 0 }))
            }
        }
        Start-Sleep -Milliseconds $PollMilliseconds
    }
    Write-DialogLog $DriverLog ("DIALOG_RESULT dialog dismissed=false attempts={0} timeout={1}s" -f $attempt, $JoinWaitSeconds)
    return $false
}

if ($ClientDir -and $DriverLog) {
    $result = Invoke-StartupDialogDismissal -ClientDir $ClientDir -DriverLog $DriverLog `
        -JoinWaitSeconds $JoinWaitSeconds -PollMilliseconds $PollMilliseconds -NoDialogAutomation:$NoDialogAutomation
    if ($result) { exit 0 } else { exit 4 }
}
