# One A/B boot run: reboot, per-process CPU snapshots at the given uptimes (hu_proc_cpu.ps1), MemAvailable,
# boot markers and the BOOT_COMPLETED receiver queue (first/last receiver, TBox receiver).
# See docs/BOOT_AUTOSTART_DEVICE_RU.md §5. Runs strictly one after another.
param(
    [Parameter(Mandatory)][string]$Tag,
    [string]$Device = "192.168.1.128:5555",
    [string]$OutDir = (Join-Path (Get-Location) "hu_boot_results"),
    [int[]]$At = @(45, 120)
)
$d = $Device
$o = $OutDir
New-Item -ItemType Directory -Force $o | Out-Null
adb connect $d *> $null
# adb reboot over TCP can hang until the socket drops, so do not wait for it.
Start-Process adb -ArgumentList "-s $d reboot" -NoNewWindow
Start-Sleep 20
$t0 = Get-Date
while ($true) {
    adb connect $d *> $null
    $bc = (adb -s $d shell getprop sys.boot_completed 2>$null) -join ''
    if ($bc.Trim() -eq '1') { break }
    if (((Get-Date) - $t0).TotalSeconds -gt 300) { 'TIMEOUT'; exit 1 }
    Start-Sleep 1
}
foreach ($a in $At) {
    while ([double](((adb -s $d shell cat /proc/uptime) -join '') -split ' ')[0] -lt $a) { Start-Sleep 1 }
    & (Join-Path $PSScriptRoot "hu_proc_cpu.ps1") -Tag "${Tag}_$a" -Device $d -OutDir $o -Top 12
}
adb -s $d shell "grep -E 'MemTotal|MemAvailable' /proc/meminfo; cat /proc/loadavg" | Out-File "$o\${Tag}_mem.txt"
Get-Content "$o\${Tag}_mem.txt"
adb -s $d logcat -b events -v monotonic -d | Out-File "$o\${Tag}_events.txt"
adb -s $d logcat -b main,system -v monotonic -d | Out-File "$o\${Tag}_main.txt"

# Pre-RTC logcat timestamps are shifted; boot_progress_start carries the true uptime in ms.
$ev = Get-Content "$o\${Tag}_events.txt"
$bs = $ev | Select-String 'boot_progress_start: (\d+)' | Select-Object -First 1
$off = [double]$bs.Matches[0].Groups[1].Value / 1000 - [double](($bs.Line.Trim() -split '\s+')[0])
function T($l) { $t = [double](($l.Trim() -split '\s+')[0]); if ($t -lt 0) { $t + $off } else { $t } }
foreach ($m in 'boot_progress_enable_screen', 'sf_stop_bootanim', 'framework_boot_completed') {
    $x = $ev | Select-String $m | Select-Object -First 1
    '{0,-28} {1,6:N2}' -f $m, (T $x.Line)
}
$ps = $ev | Select-String 'am_proc_start: \[\d+,\d+,(\d+),([^,]+),([^,]+),'
"am_proc_start count           $($ps.Count)"
$ps | ForEach-Object { '{0,6:N1} {1,-6} {2,-20} {3}' -f (T $_.Line), $_.Matches[0].Groups[1].Value, $_.Matches[0].Groups[3].Value, $_.Matches[0].Groups[2].Value } |
    Out-File "$o\${Tag}_procstart.txt"
# Stock firmware logs every manifest receiver (WTL_LoadedApkPlugs: receive Intent { act=... }).
$bc = Get-Content "$o\${Tag}_main.txt" | Select-String 'receive\s+Intent \{ act=android.intent.action.BOOT_COMPLETED'
if ($bc) {
    'BOOT_COMPLETED queue          {0,6:N2} -> {1,6:N2}  ({2} receivers)' -f (T $bc[0].Line), (T $bc[-1].Line), $bc.Count
    $tb = $bc | Where-Object { $_.Line -match 'vad\.dashing\.tbox/' } | Select-Object -First 1
    if ($tb) { 'TBox BootCompleteReceiver     {0,6:N2}' -f (T $tb.Line) }
}
