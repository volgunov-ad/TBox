# One boot measurement: reboot the HU, wait for sys.boot_completed, dump logs and print key markers
# in seconds from kernel start. See docs/SYSTEMUI_A9_DISABLE_RU.md §4. Runs strictly one after another.
param(
    [Parameter(Mandatory)][string]$Tag,
    [string]$Device = "192.168.1.128:5555",
    [string]$OutDir = (Join-Path (Get-Location) "hu_boot_results"),
    [string]$LauncherPkg = "ras.dashing.tbox.launcher",
    [switch]$NoReboot
)
$ErrorActionPreference = 'Continue'
$d = $Device
$out = Join-Path $OutDir $Tag
New-Item -ItemType Directory -Force $out | Out-Null

adb connect $d *> $null
if (-not $NoReboot) {
    adb -s $d reboot
    $t0 = Get-Date
    Start-Sleep 25
    while ($true) {
        adb connect $d *> $null
        $bc = (adb -s $d shell getprop sys.boot_completed 2>$null) -join ''
        if ($bc.Trim() -eq '1') { break }
        if (((Get-Date) - $t0).TotalSeconds -gt 400) { Write-Host "TIMEOUT"; exit 1 }
        Start-Sleep 2
    }
    "host_reboot_to_adb_s=$([int]((Get-Date) - $t0).TotalSeconds)" | Tee-Object "$out\meta.txt"
    Start-Sleep 30
}
adb -s $d shell "cat /proc/uptime; dumpsys package com.android.systemui | grep 'User 0:'; dumpsys wallpaper | grep mWallpaperComponent | head -1; appops get com.android.systemui SYSTEM_ALERT_WINDOW" | Out-File -Append "$out\meta.txt"
adb -s $d logcat -b events -v monotonic -d | Out-File "$out\events.txt"
adb -s $d logcat -b main,system,crash -v monotonic -d | Out-File "$out\main.txt"
adb -s $d shell "dumpsys window windows | grep -E 'Window #|ty=|isOnScreen'" | Out-File "$out\windows.txt"
adb -s $d shell screencap -p /data/local/tmp/s.png
adb -s $d pull /data/local/tmp/s.png "$out\screen.png" | Out-Null

# Pre-RTC logcat timestamps are shifted; boot_progress_start carries the true uptime in ms.
$ev = Get-Content "$out\events.txt"
$bs = $ev | Select-String 'boot_progress_start: (\d+)' | Select-Object -First 1
if (-not $bs) { Write-Host "no boot_progress_start"; exit 1 }
$offset = [double]$bs.Matches[0].Groups[1].Value / 1000 - [double](($bs.Line.Trim() -split '\s+')[0])
function T($line) { '{0,7:N2}' -f ([double](($line.Trim() -split '\s+')[0]) + $offset) }

$lp = [regex]::Escape($LauncherPkg)
$marks = [ordered]@{
    'boot_progress_ams_ready'     = 'boot_progress_ams_ready'
    'boot_progress_enable_screen' = 'boot_progress_enable_screen'
    'sf_stop_bootanim'            = 'sf_stop_bootanim'
    'wm_boot_animation_done'      = 'wm_boot_animation_done'
    'framework_boot_completed'    = 'framework_boot_completed'
    'proc systemui'               = 'am_proc_start.*com\.android\.systemui,'
    'proc launcher'               = "am_proc_start.*$lp"
    'proc vad.dashing.tbox'       = 'am_proc_start.*vad\.dashing\.tbox,'
}
$res = foreach ($k in $marks.Keys) {
    $m = $ev | Select-String $marks[$k] | Select-Object -First 1
    "{0,-30} {1}" -f $k, $(if ($m) { T $m.Line } else { '-' })
}
$mn = Get-Content "$out\main.txt"
$res += foreach ($p in 'BOOT TIMEOUT', 'Default wallpaper component not found', 'bindWallpaperComponentLocked: componentName=\S') {
    foreach ($x in ($mn | Select-String $p | Select-Object -First 1)) {
        $line = $x.Line.Trim()
        "{0,-30} {1}  {2}" -f "log:$p", (T $x.Line), $line.Substring(0, [Math]::Min(170, $line.Length))
    }
}
$res | Tee-Object "$out\summary.txt"
