param(
    [Parameter(Mandatory=$true)][string]$Action,
    [string]$Text = "",
    [int]$X = 0,
    [int]$Y = 0,
    [int]$Button = 0,
    [int]$Delay = 250
)

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Win32In {
    [StructLayout(LayoutKind.Sequential)]
    public struct RECT { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
    [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int X, int Y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint dwFlags, int dx, int dy, uint dwData, UIntPtr dwExtraInfo);
    [DllImport("user32.dll")] public static extern void keybd_event(byte bVk, byte bScan, uint dwFlags, UIntPtr dwExtraInfo);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern bool BringWindowToTop(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out int pid);
    [DllImport("user32.dll")] public static extern bool AttachThreadInput(uint idAttach, uint idAttachTo, bool attach);
    [DllImport("kernel32.dll")] public static extern uint GetCurrentThreadId();
}
"@

function Focus-Window([IntPtr]$hwnd) {
    $fg = [Win32In]::GetForegroundWindow()
    $fgPid = 0
    $fgThread = [Win32In]::GetWindowThreadProcessId($fg, [ref]$fgPid)
    $curThread = [Win32In]::GetCurrentThreadId()
    [Win32In]::AttachThreadInput($curThread, $fgThread, $true) | Out-Null
    [Win32In]::SetForegroundWindow($hwnd) | Out-Null
    [Win32In]::BringWindowToTop($hwnd) | Out-Null
    [Win32In]::AttachThreadInput($curThread, $fgThread, $false) | Out-Null
}

function Get-GameWindow {
    $proc = Get-Process | Where-Object { $_.MainWindowHandle -ne 0 -and $_.MainWindowTitle -like "*Minecraft*" } | Select-Object -First 1
    if (-not $proc) { throw "Minecraft window not found" }
    return $proc
}

$proc = Get-GameWindow
$hwnd = $proc.MainWindowHandle
if ([Win32In]::IsIconic($hwnd)) { [Win32In]::ShowWindow($hwnd, 9) | Out-Null }
Focus-Window $hwnd
Start-Sleep -Milliseconds $Delay

function SendVk([int]$vk) {
    [Win32In]::keybd_event([byte]$vk, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 40
    [Win32In]::keybd_event([byte]$vk, 0, 2, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 40
}

function SendKeyName([string]$name) {
    $map = @{
        'enter' = 0x0D; 'escape' = 0x1B; 'tab' = 0x09; 'space' = 0x20;
        't' = 0x54; 'slash' = 0xBF; 'f1' = 0x70; 'f3' = 0x72; 'f11' = 0x7A;
        'ctrl' = 0x11; 'v' = 0x56; 'e' = 0x45; 'q' = 0x51; 'w' = 0x57;
    }
    if ($map.ContainsKey($name.ToLower())) { SendVk $map[$name.ToLower()]; return }
    if ($name.Length -eq 1) {
        $ch = $name.ToUpper()
        if ($ch -match '[A-Z]') { SendVk ([int][char]$ch); return }
        if ($ch -match '[0-9]') { SendVk ([int][char]$ch); return }
    }
    throw "Unknown key: $name"
}

switch ($Action.ToLower()) {
    'key' {
        SendKeyName $Text
        Write-Output "sent key $Text"
    }
    'rawkey' {
        SendVk ([int]$Text)
        Write-Output "sent vk $Text"
    }
    'chat' {
        # Open chat with T, paste the text through the clipboard, press Enter.
        Set-Clipboard -Value $Text
        Start-Sleep -Milliseconds 120
        SendKeyName 't'
        Start-Sleep -Milliseconds 500
        [Win32In]::keybd_event(0x11, 0, 0, [UIntPtr]::Zero)
        Start-Sleep -Milliseconds 60
        SendKeyName 'v'
        [Win32In]::keybd_event(0x11, 0, 2, [UIntPtr]::Zero)
        Start-Sleep -Milliseconds 350
        SendKeyName 'enter'
        Start-Sleep -Milliseconds 350
        Write-Output "sent chat: $Text"
    }
    'command' {
        Set-Clipboard -Value $Text
        Start-Sleep -Milliseconds 120
        SendKeyName 'slash'
        Start-Sleep -Milliseconds 500
        [Win32In]::keybd_event(0x11, 0, 0, [UIntPtr]::Zero)
        Start-Sleep -Milliseconds 60
        SendKeyName 'v'
        [Win32In]::keybd_event(0x11, 0, 2, [UIntPtr]::Zero)
        Start-Sleep -Milliseconds 350
        SendKeyName 'enter'
        Start-Sleep -Milliseconds 500
        Write-Output "sent command: $Text"
    }
    'click' {
        $rect = New-Object Win32In+RECT
        [Win32In]::GetWindowRect($hwnd, [ref]$rect) | Out-Null
        # X/Y are client-area coordinates; add the title bar offset.
        $screenX = $rect.Left + $X
        $screenY = $rect.Top + $Y
        [Win32In]::SetCursorPos($screenX, $screenY) | Out-Null
        Start-Sleep -Milliseconds 120
        $down = if ($Button -eq 1) { 0x0002 } else { 0x0008 }
        $up = if ($Button -eq 1) { 0x0004 } else { 0x0010 }
        [Win32In]::mouse_event($down, 0, 0, 0, [UIntPtr]::Zero)
        Start-Sleep -Milliseconds 80
        [Win32In]::mouse_event($up, 0, 0, 0, [UIntPtr]::Zero)
        Start-Sleep -Milliseconds 300
        Write-Output "clicked $X,$Y button=$Button at screen $screenX,$screenY"
    }
    'move' {
        $rect = New-Object Win32In+RECT
        [Win32In]::GetWindowRect($hwnd, [ref]$rect) | Out-Null
        [Win32In]::SetCursorPos($rect.Left + $X, $rect.Top + $Y) | Out-Null
        Write-Output "moved to $($rect.Left + $X),$($rect.Top + $Y)"
    }
    default { throw "Unknown action: $Action" }
}
