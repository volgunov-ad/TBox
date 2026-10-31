# One HU performance run: reboot, wait for boot, collect logs, sample CPU, run the window-switch test.
# See docs/HU_PERF_TESTS_RU.md. Run runs strictly one after another (never in parallel).
param(
    [Parameter(Mandatory)][string]$Tag,
    [Parameter(Mandatory)][string]$Pkg,
    [Parameter(Mandatory)][string]$Act,
    [string]$Device = "192.168.1.128:5555",
    [string]$OutDir = (Join-Path (Get-Location) "hu_perf_results"),
    [int]$SampleEnd = 180,
    [int]$Cycles = 10
)
$ErrorActionPreference = 'Continue'
$d = $Device
$base = Split-Path -Parent $MyInvocation.MyCommand.Path
$out = Join-Path $OutDir $Tag
New-Item -ItemType Directory -Force $out | Out-Null

adb connect $d *> $null
foreach ($f in 'hu_perf_sample.sh', 'hu_perf_switch.sh') {
    $lf = Join-Path $out $f
    [IO.File]::WriteAllText($lf, ((Get-Content -Raw (Join-Path $base $f)) -replace "`r", ""))
    adb -s $d push $lf /data/local/tmp/$f | Out-Null
}

adb -s $d shell "dumpsys package dexopt | grep -A2 '\[$Pkg\]'" | Out-File "$out\dexopt_before.txt"
adb -s $d reboot
$t0 = Get-Date
Start-Sleep 20
while ($true) {
    adb connect $d *> $null
    $bc = (adb -s $d shell getprop sys.boot_completed 2>$null) -join ''
    if ($bc.Trim() -eq '1') { break }
    if (((Get-Date) - $t0).TotalSeconds -gt 400) { Write-Host "TIMEOUT"; exit 1 }
    Start-Sleep 2
}
$hostSec = [int]((Get-Date) - $t0).TotalSeconds
$up = (adb -s $d shell cat /proc/uptime) -join ''
"host_reboot_to_adb_s=$hostSec uptime_at_adb=$up" | Tee-Object "$out\meta.txt"

adb -s $d logcat -b events -v monotonic -d | Out-File "$out\events.txt"
adb -s $d logcat -b main,system -v monotonic -d | Out-File "$out\main.txt"

adb -s $d shell sh /data/local/tmp/hu_perf_sample.sh $SampleEnd $Pkg | Out-File "$out\sample.txt"
adb -s $d shell dumpsys gfxinfo $Pkg | Out-File "$out\gfx_boot.txt"
adb -s $d logcat -b events -v monotonic -d | Out-File "$out\events2.txt"
adb -s $d logcat -b main,system -v monotonic -d | Out-File "$out\main2.txt"
adb -s $d shell "dumpsys package dexopt | grep -A2 '\[$Pkg\]'" | Out-File "$out\dexopt_after.txt"

adb -s $d shell sh /data/local/tmp/hu_perf_switch.sh $Cycles $Pkg $Act | Out-File "$out\switch.txt"
Write-Host "DONE $Tag"
