# A9 mb_daemon semantics bench (W0 / shared-root-service plan section 13).
# Run from a PC on the same LAN as the HU. Requires adb in PATH.
# See tools/hu_perf/ (RU runbook INSTRUKTSIYA_MB_DAEMON.md) and docs/HU_PERF_TESTS_RU.md section 4.
#
# Measures (on-device script): arm latency, when autorun.sh is deleted, poll-while-
# armed, re-arm without setprop, rename atomicity on /sdcard, two-writer race.
# Optional -Boot: reboot and capture Accessibility bind + first successful arm.
#
# Encoding: ASCII-only body + UTF-8 BOM so Windows PowerShell 5.1 can parse it.
param(
    [string]$Device = "192.168.1.128:5555",
    [string]$Tag = ("mbd_" + (Get-Date -Format "yyyyMMdd_HHmmss")),
    [string]$OutDir = (Join-Path (Get-Location) "hu_mbd_bench_results"),
    [ValidateSet("all", "arm", "delete", "poll", "rearm", "rename", "race")]
    [string]$Suite = "all",
    [switch]$Boot,
    [string]$A11yPkg = "",
    [int]$BootArmAttempts = 40,
    [int]$BootArmIntervalSec = 2
)
$ErrorActionPreference = "Continue"
$d = $Device
$base = Split-Path -Parent $MyInvocation.MyCommand.Path
$out = Join-Path $OutDir $Tag
New-Item -ItemType Directory -Force $out | Out-Null

function Connect-Hu {
    adb connect $d *> $null
    $st = (adb -s $d get-state 2>$null) -join ""
    if ($st.Trim() -ne "device") {
        Write-Host "WARN: adb state='$st' for $d (continuing)"
    }
}

function Push-Lf([string]$LocalName, [string]$RemotePath) {
    $src = Join-Path $base $LocalName
    $lf = Join-Path $out $LocalName
    $text = [IO.File]::ReadAllText($src) -replace "`r", ""
    [IO.File]::WriteAllText($lf, $text)
    adb -s $d push $lf $RemotePath | Out-Null
}

function Wait-BootCompleted([int]$TimeoutSec = 400) {
    $t0 = Get-Date
    while ($true) {
        Connect-Hu
        $bc = (adb -s $d shell getprop sys.boot_completed 2>$null) -join ""
        if ($bc.Trim() -eq "1") { return [int]((Get-Date) - $t0).TotalSeconds }
        if (((Get-Date) - $t0).TotalSeconds -gt $TimeoutSec) {
            throw "TIMEOUT waiting for sys.boot_completed on $d"
        }
        Start-Sleep 2
    }
}

function Write-MetaLine([string]$Line) {
    $Line | Tee-Object -FilePath (Join-Path $out "meta.txt") -Append
}

Connect-Hu
"device=$d tag=$Tag suite=$Suite boot=$($Boot.IsPresent) started=$(Get-Date -Format o)" |
    Tee-Object (Join-Path $out "meta.txt")

# --- Optional boot / Accessibility / first-arm window -------------------------
if ($Boot) {
    Write-Host "BOOT: clearing logcat, rebooting $d ..."
    adb -s $d logcat -c *> $null
    adb -s $d reboot
    $hostSec = Wait-BootCompleted
    Write-MetaLine "host_reboot_to_adb_s=$hostSec"
    $up = (adb -s $d shell cat /proc/uptime) -join ""
    Write-MetaLine "uptime_at_adb=$up"

    # Dump early logs for Accessibility bind timing.
    adb -s $d logcat -b events -v monotonic -d 2>$null |
        Out-File (Join-Path $out "boot_events.txt")
    adb -s $d logcat -b main,system -v monotonic -d 2>$null |
        Out-File (Join-Path $out "boot_main.txt")

    $a11yDump = Join-Path $out "accessibility_dump.txt"
    adb -s $d shell dumpsys accessibility 2>$null | Out-File $a11yDump

    # Parse Accessibility bind / service connected lines (any known or -A11yPkg).
    $pkgs = @(
        "vad.dashing.camd",
        "ras.dashing.tbox.launcher",
        "vad.dashing.tbox",
        "vad.dashing.usbmount",
        "vad.dashing.dclinstall"
    )
    if ($A11yPkg) { $pkgs = @($A11yPkg) + $pkgs }
    $main = Get-Content (Join-Path $out "boot_main.txt") -ErrorAction SilentlyContinue
    $a11yHits = @()
    foreach ($p in $pkgs) {
        $esc = [regex]::Escape($p)
        $patterns = @(
            "AccessibilityService.*$esc",
            "onServiceConnected.*$esc",
            "Binding service.*$esc",
            "Loaded applet.*$esc",
            "AccessibilityManagerService.*$esc"
        )
        foreach ($pat in $patterns) {
            $m = $main | Select-String -Pattern $pat | Select-Object -First 3
            foreach ($hit in $m) {
                $a11yHits += $hit.Line
            }
        }
    }
    $a11yHits | Select-Object -Unique | Out-File (Join-Path $out "a11y_hits.txt")
    Write-MetaLine "a11y_hit_lines=$((Get-Content (Join-Path $out 'a11y_hits.txt') -ErrorAction SilentlyContinue | Measure-Object -Line).Lines)"

    # First successful mb_daemon arm after boot (poll every BootArmIntervalSec).
    Push-Lf "hu_mb_daemon_bench.sh" "/data/local/tmp/hu_mb_daemon_bench.sh"
    adb -s $d shell "chmod 755 /data/local/tmp/hu_mb_daemon_bench.sh" | Out-Null
    $firstArm = Join-Path $out "first_arm_after_boot.txt"
    "attempt,host_s,uptime,result_line" | Out-File $firstArm
    $tBoot = Get-Date
    $armed = $false
    for ($i = 1; $i -le $BootArmAttempts; $i++) {
        Connect-Hu
        $hostS = [int]((Get-Date) - $tBoot).TotalSeconds
        $upNow = ((adb -s $d shell cat /proc/uptime) -join "").Trim()
        # Run only the arm suite into a per-attempt dir; stop on first ok.
        $remoteOut = "/data/local/tmp/hu_mbd_bench_boot_$i"
        adb -s $d shell "rm -rf $remoteOut; mkdir -p $remoteOut" | Out-Null
        $run = (adb -s $d shell "sh /data/local/tmp/hu_mb_daemon_bench.sh $remoteOut arm" 2>&1) -join "`n"
        $resLine = (adb -s $d shell "grep '^arm_1=' $remoteOut/results.txt 2>/dev/null") -join ""
        "$i,$hostS,$upNow,$resLine" | Out-File $firstArm -Append
        Write-Host "boot-arm attempt $i host_s=$hostS => $resLine"
        if ($resLine -match "result=ok") {
            Write-MetaLine "first_arm_ok_attempt=$i host_s=$hostS uptime=$upNow"
            $armed = $true
            adb -s $d pull "$remoteOut/results.txt" (Join-Path $out "first_arm_results.txt") | Out-Null
            break
        }
        Start-Sleep $BootArmIntervalSec
    }
    if (-not $armed) {
        Write-MetaLine "first_arm_ok_attempt=-1"
        Write-Host "WARN: no successful arm in $BootArmAttempts attempts after boot"
    }

    # Refresh Accessibility dump after the arm window.
    adb -s $d shell dumpsys accessibility 2>$null |
        Out-File (Join-Path $out "accessibility_dump_after.txt")
}

# --- Steady-state semantics suite --------------------------------------------
Write-Host "SUITE: $Suite on $d ..."
Push-Lf "hu_mb_daemon_bench.sh" "/data/local/tmp/hu_mb_daemon_bench.sh"
adb -s $d shell "chmod 755 /data/local/tmp/hu_mb_daemon_bench.sh" | Out-Null
$remote = "/data/local/tmp/hu_mbd_bench"
adb -s $d shell "rm -rf $remote; mkdir -p $remote" | Out-Null
$suiteLog = Join-Path $out "suite_run.txt"
adb -s $d shell "sh /data/local/tmp/hu_mb_daemon_bench.sh $remote $Suite" 2>&1 |
    Tee-Object $suiteLog

foreach ($f in @("results.txt", "bench.log", "delete_watch.txt", "delete_script_trace.txt",
                 "rename_torn.txt", "rename_expected.txt")) {
    adb -s $d pull "$remote/$f" (Join-Path $out $f) 2>$null | Out-Null
}

# Cleanup remote work leftovers (script already clears channel).
adb -s $d shell "rm -rf /sdcard/hu_mbd_bench $remote" 2>$null | Out-Null

Write-Host ""
Write-Host "==== results ($out/results.txt) ===="
if (Test-Path (Join-Path $out "results.txt")) {
    Get-Content (Join-Path $out "results.txt") | Write-Host
} else {
    Write-Host "MISSING results.txt - see suite_run.txt"
}
Write-Host "DONE $Tag -> $out"
