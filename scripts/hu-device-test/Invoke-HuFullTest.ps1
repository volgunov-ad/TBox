<#
.SYNOPSIS
  Full HU smoke test for TBox Monitor: install, permissions, fixtures, themes, automations, logs.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\scripts\hu-device-test\Invoke-HuFullTest.ps1
#>
param(
    [string]$Adb = "D:\Tools\scrcpy-win64-v3.3.4\adb.exe",
    [string]$Device = "192.168.1.128:5555",
    [string]$Apk = "C:\Users\volgu\AndroidStudioProjects\tbox_monitor-v.1.0.0-ru-test29.apk",
    [string]$RepoRoot = "",
    [switch]$SkipInstall
)

$ErrorActionPreference = "Continue"
if (-not $RepoRoot) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
}
$ScriptDir = $PSScriptRoot
$Python = Get-Command python -ErrorAction SilentlyContinue
$Stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$OutDir = Join-Path $ScriptDir "results\$Stamp"
$DeviceDir = "/storage/emulated/0/Download/hu_test"
$Pkg = "vad.dashing.tbox"
$UiJson = Join-Path $ScriptDir "ui-strings.json"
$Report = @()

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
if (-not (Test-Path $UiJson)) {
    throw "Missing ui-strings.json. Run generate_hu_test_fixtures.py first"
}
$Ui = Get-Content -Path $UiJson -Encoding UTF8 -Raw | ConvertFrom-Json

function Log([string]$Message, [string]$Level = "INFO") {
    $line = "[{0}] {1} {2}" -f (Get-Date -Format "HH:mm:ss"), $Level, $Message
    Write-Host $line
    $script:Report += $line
}

function Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$AdbArgs)
    $old = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & $Adb -s $Device @AdbArgs 2>&1 | ForEach-Object { $_.ToString() }
    $ErrorActionPreference = $old
}

function AdbShell([string]$Cmd) {
    Adb shell $Cmd
}

function Save-Report {
    $path = Join-Path $OutDir "report.txt"
    $script:Report | Set-Content -Path $path -Encoding UTF8
    return $path
}

function Connect-Device {
    Log "adb connect $Device"
    Adb connect $Device | Out-Null
    Start-Sleep -Seconds 1
    $devs = Adb devices
    $devs | ForEach-Object { Log $_ }
    if (($devs | Out-String) -notmatch [regex]::Escape($Device)) {
        throw "Device $Device is not connected"
    }
}

function Grant-AllPermissions {
    Log "Granting permissions / appops"
    $runtime = @(
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.WRITE_SECURE_SETTINGS",
        "android.permission.WRITE_SETTINGS"
    )
    foreach ($p in $runtime) {
        $out = AdbShell "pm grant $Pkg $p" 2>&1 | Out-String
        if ($out.Trim()) { Log "pm grant $p : $($out.Trim())" "WARN" } else { Log "pm grant $p ok" }
    }
    $ops = @(
        @("SYSTEM_ALERT_WINDOW", "allow"),
        @("GET_USAGE_STATS", "allow"),
        @("WRITE_SETTINGS", "allow"),
        @("MOCK_LOCATION", "allow")
    )
    foreach ($op in $ops) {
        $out = AdbShell "appops set $Pkg $($op[0]) $($op[1])" 2>&1 | Out-String
        Log "appops $($op[0])=$($op[1]) $($out.Trim())"
    }
    AdbShell "dumpsys deviceidle whitelist +$Pkg" | Out-Null
    $nl = "vad.dashing.tbox/vad.dashing.tbox.MediaControlNotificationListenerService"
    $cur = (AdbShell "settings get secure enabled_notification_listeners" | Out-String).Trim()
    if ($cur -notmatch [regex]::Escape($nl)) {
        if ($cur -and $cur -ne "null") {
            AdbShell "settings put secure enabled_notification_listeners ${cur}:$nl" | Out-Null
        } else {
            AdbShell "settings put secure enabled_notification_listeners $nl" | Out-Null
        }
        Log "enabled notification listener"
    }
    # A9 mock-location app picker
    AdbShell "settings put secure mock_location $Pkg" 2>$null | Out-Null
}

function Install-ApkIfNeeded {
    if ($SkipInstall) {
        Log "SkipInstall set"
        return
    }
    if (-not (Test-Path $Apk)) { throw "APK not found: $Apk" }
    Log "Installing $Apk"
    $out = Adb install -r -g $Apk 2>&1 | Out-String
    Log $out.Trim()
    if ($out -notmatch "Success") { throw "apk install failed" }
}

function New-Fixtures {
    Log "Generating fixtures"
    $gen = Join-Path $ScriptDir "generate_hu_test_fixtures.py"
    if ($Python) {
        & $Python.Source $gen
    } else {
        python $gen
    }
}

function Push-Fixtures {
    Log "Pushing fixtures to $DeviceDir"
    AdbShell "mkdir -p $DeviceDir" | Out-Null
    $fix = Join-Path $ScriptDir "fixtures"
    Get-ChildItem $fix -File | ForEach-Object {
        Adb push $_.FullName "$DeviceDir/$($_.Name)" | ForEach-Object { Log $_ }
    }
}

function Clear-Logcat {
    Adb logcat -c | Out-Null
}

function Start-App {
    Log "Starting MainActivity"
    AdbShell "am start -W -n $Pkg/.MainActivity" | ForEach-Object { Log $_ }
    Start-Sleep -Seconds 4
}

function Get-AppPid {
    $pidLine = (AdbShell "pidof $Pkg" | Out-String).Trim()
    if (-not $pidLine) { $pidLine = (AdbShell "pidof -s $Pkg" | Out-String).Trim() }
    if (-not $pidLine) { return "" }
    return $pidLine.Split(" ")[0]
}

function Dump-Ui([string]$Name) {
    $remote = "/sdcard/hu_uidump.xml"
    AdbShell "uiautomator dump $remote" | Out-Null
    $local = Join-Path $OutDir "$Name.xml"
    Adb pull $remote $local 2>$null | Out-Null
    if (Test-Path $local) {
        return [System.IO.File]::ReadAllText($local)
    }
    return ""
}

function Save-Screenshot([string]$Name) {
    $remote = "/sdcard/hu_shot.png"
    AdbShell "screencap -p $remote" | Out-Null
    $local = Join-Path $OutDir "$Name.png"
    Adb pull $remote $local 2>$null | Out-Null
    Log "screenshot $Name"
}

function Get-NodeCenter([string]$Xml, [string]$Text) {
    if (-not $Xml) { return $null }
    $esc = [regex]::Escape($Text)
    $patterns = @(
        "text=`"$esc`"[^>]*bounds=`"\[(\d+),(\d+)\]\[(\d+),(\d+)\]`"",
        "content-desc=`"$esc`"[^>]*bounds=`"\[(\d+),(\d+)\]\[(\d+),(\d+)\]`"",
        "bounds=`"\[(\d+),(\d+)\]\[(\d+),(\d+)\]`"[^>]*text=`"$esc`""
    )
    foreach ($pat in $patterns) {
        $m = [regex]::Match($Xml, $pat)
        if ($m.Success) {
            $x1 = [int]$m.Groups[1].Value
            $y1 = [int]$m.Groups[2].Value
            $x2 = [int]$m.Groups[3].Value
            $y2 = [int]$m.Groups[4].Value
            return @{ X = [int](($x1 + $x2) / 2); Y = [int](($y1 + $y2) / 2); Text = $Text }
        }
    }
    return $null
}

function Invoke-Tap([int]$X, [int]$Y) {
    AdbShell "input tap $X $Y" | Out-Null
    Start-Sleep -Milliseconds 700
}

function Invoke-SwipeUp {
    AdbShell "input swipe 960 800 960 280 400" | Out-Null
    Start-Sleep -Milliseconds 600
}

function Click-Text {
    param(
        [string]$Text,
        [int]$Swipes = 4,
        [string]$DumpName = "click"
    )
    $helper = Join-Path $ScriptDir "click_from_dump.py"
    for ($i = 0; $i -le $Swipes; $i++) {
        $xml = Dump-Ui ("{0}-{1}" -f $DumpName, $i)
        $xmlPath = Join-Path $OutDir ("{0}-{1}.xml" -f $DumpName, $i)
        if (Test-Path $xmlPath) {
            $coords = python $helper $xmlPath $Text 2>$null
            if ($coords -match "^(\d+)\s+(\d+)$") {
                Log "tap '$Text' at $($Matches[1]),$($Matches[2]) via dump"
                Invoke-Tap ([int]$Matches[1]) ([int]$Matches[2])
                return $true
            }
        }
        $node = Get-NodeCenter $xml $Text
        if ($node) {
            Log "tap '$Text' at $($node.X),$($node.Y)"
            Invoke-Tap $node.X $node.Y
            return $true
        }
        if ($i -lt $Swipes) { Invoke-SwipeUp }
    }
    Log "UI text not found: $Text" "WARN"
    Save-Screenshot ("missing-" + ($Text -replace '[^\w\-]', '_'))
    return $false
}

function Dismiss-FirstRunDialogs {
    foreach ($t in @("Allow", "ALLOW", $Ui.allow, "OK", $Ui.ok_ru, $Ui.close, "While using the app", $Ui.yes, $Ui.yes_cap)) {
        $xml = Dump-Ui "dismiss"
        $node = Get-NodeCenter $xml $t
        if ($node) {
            Log "dismiss '$t'"
            Invoke-Tap $node.X $node.Y
        }
    }
}

function Import-BackupUi {
    Log "Import backup via Settings UI"
    Click-Text $Ui.settings -DumpName "menu-settings" | Out-Null
    Start-Sleep -Seconds 1
    $ok = Click-Text $Ui.import_json -Swipes 8 -DumpName "backup-import"
    if (-not $ok) { return $false }
    Start-Sleep -Seconds 1
    Click-Text $Ui.choose_file -Swipes 1 -DumpName "backup-choose" | Out-Null
    Start-Sleep -Seconds 2
    $picked = Click-Text "hu_test_backup.json" -Swipes 6 -DumpName "backup-file"
    if (-not $picked) {
        # DocumentsUI sometimes shows name without extension
        $picked = Click-Text "hu_test_backup" -Swipes 2 -DumpName "backup-file2"
    }
    Start-Sleep -Seconds 4
    Save-Screenshot "after-backup-import"
    return $picked
}

function Apply-ThemeFile([string]$FileName) {
    Log "VIEW theme $FileName"
    $uri = "file://$DeviceDir/$FileName"
    AdbShell "am start -a android.intent.action.VIEW -d `"$uri`" -n $Pkg/.MainActivity -t application/octet-stream" | Out-Null
    Start-Sleep -Seconds 2
    Save-Screenshot ("theme-dialog-" + ($FileName -replace '\.', '_'))
    $ok = Click-Text $Ui.yes -Swipes 0 -DumpName "theme-yes"
    if (-not $ok) { $ok = Click-Text $Ui.yes_cap -Swipes 0 -DumpName "theme-yes2" }
    Start-Sleep -Seconds 5
    Save-Screenshot ("theme-applied-" + ($FileName -replace '\.', '_'))
    return $ok
}

function Assign-DriveModeThemes {
    Log "Assign drive-mode themes on Themes tab"
    Click-Text $Ui.themes -DumpName "menu-themes" | Out-Null
    Start-Sleep -Seconds 1
    Click-Text $Ui.drive_section -Swipes 8 -DumpName "drive-section" | Out-Null
    $modes = @(
        @{ Label = "ECO"; File = "hu_test_eco.tboxtheme" },
        @{ Label = "NOR"; File = "hu_test_nor.tboxtheme" },
        @{ Label = "SPT"; File = "hu_test_spt.tboxtheme" }
    )
    foreach ($m in $modes) {
        $xml = Dump-Ui ("drive-" + $m.Label)
        $label = Get-NodeCenter $xml $m.Label
        $pick = Get-NodeCenter $xml $Ui.choose_file
        if ($label -and $pick) {
            $pickPat = 'text="' + [regex]::Escape($Ui.choose_file) + '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
            $picks = [regex]::Matches($xml, $pickPat)
            $best = $null
            $bestDy = 99999
            foreach ($mt in $picks) {
                $y = [int](( [int]$mt.Groups[2].Value + [int]$mt.Groups[4].Value ) / 2)
                $dy = $y - $label.Y
                if ($dy -ge 0 -and $dy -lt $bestDy) {
                    $bestDy = $dy
                    $best = @{
                        X = [int](( [int]$mt.Groups[1].Value + [int]$mt.Groups[3].Value ) / 2)
                        Y = $y
                    }
                }
            }
            if ($best) {
                Log "pick file for $($m.Label) at $($best.X),$($best.Y)"
                Invoke-Tap $best.X $best.Y
                Start-Sleep -Seconds 2
                Click-Text $m.File -Swipes 6 -DumpName ("pick-" + $m.Label) | Out-Null
                Start-Sleep -Seconds 2
                Click-Text $Ui.yes -Swipes 0 -DumpName ("pick-yes-" + $m.Label) | Out-Null
                Start-Sleep -Seconds 4
            }
        } else {
            Log "Could not locate drive-mode row $($m.Label)" "WARN"
        }
        Save-Screenshot ("drive-assigned-" + $m.Label)
    }
}

function Switch-DriveModes {
    Log "Tapping ECO/NOR/SPT widgets"
    AdbShell "am start -n $Pkg/.MainActivity" | Out-Null
    Start-Sleep -Seconds 2
    foreach ($label in @("ECO", "NOR", "SPT", "ECO")) {
        $hit = Click-Text $label -Swipes 2 -DumpName ("mode-" + $label)
        Log "drive widget $label hit=$hit"
        Start-Sleep -Seconds 3
        Save-Screenshot ("mode-" + $label)
    }
}

function Test-Automations {
    Log "Foreground-app automations: Settings then back to Monitor"
    Save-Screenshot "before-settings"
    AdbShell "am start -a android.settings.SETTINGS" | Out-Null
    Start-Sleep -Seconds 4
    Save-Screenshot "on-settings"
    AdbShell "am start -n $Pkg/.MainActivity" | Out-Null
    Start-Sleep -Seconds 4
    Save-Screenshot "back-monitor"
    $navi = (AdbShell "pm path ru.yandex.yandexnavi" | Out-String)
    if ($navi -match "package:") {
        Log "Launching Yandex Navi"
        AdbShell "monkey -p ru.yandex.yandexnavi -c android.intent.category.LAUNCHER 1" | Out-Null
        Start-Sleep -Seconds 4
        Save-Screenshot "on-navi"
        AdbShell "am start -n $Pkg/.MainActivity" | Out-Null
        Start-Sleep -Seconds 4
        Save-Screenshot "after-navi"
    }
}

function Collect-Logs {
    Log "Collecting logcat / dumpsys"
    $appPid = Get-AppPid
    Log "pid=$appPid"
    $logFile = Join-Path $OutDir "logcat.txt"
    if ($appPid) {
        Adb logcat -d --pid $appPid | Out-File -FilePath $logFile -Encoding utf8
    } else {
        Adb logcat -d | Out-File -FilePath $logFile -Encoding utf8
    }
    AdbShell "dumpsys window windows" | Out-File -FilePath (Join-Path $OutDir "windows.txt") -Encoding utf8
    AdbShell "dumpsys meminfo $Pkg" | Out-File -FilePath (Join-Path $OutDir "meminfo.txt") -Encoding utf8
    AdbShell "dumpsys activity services $Pkg" | Out-File -FilePath (Join-Path $OutDir "services.txt") -Encoding utf8
    AdbShell "appops get $Pkg" | Out-File -FilePath (Join-Path $OutDir "appops.txt") -Encoding utf8
    return $logFile
}

function Analyze-Logs([string]$LogFile) {
    if (-not (Test-Path $LogFile)) {
        Log "No logcat file" "ERROR"
        return
    }
    $text = Get-Content $LogFile -Raw -ErrorAction SilentlyContinue
    if (-not $text) { $text = "" }
    $checks = @(
        @{ Name = "FATAL EXCEPTION"; Bad = $true },
        @{ Name = "AndroidRuntime"; Bad = $true },
        @{ Name = "OutOfMemoryError"; Bad = $true },
        @{ Name = "HUTEST"; Bad = $false },
        @{ Name = "Automation"; Bad = $false },
        @{ Name = "Theme Service"; Bad = $false },
        @{ Name = "Floating Dashboard"; Bad = $false },
        @{ Name = "MbCanEngineFacade"; Bad = $false },
        @{ Name = "Background Service"; Bad = $false },
        @{ Name = "toast_theme_apply"; Bad = $false }
    )
    $summary = Join-Path $OutDir "analysis.txt"
    $lines = @()
    $lines += "pid log size=$($text.Length)"
    foreach ($c in $checks) {
        $count = ([regex]::Matches($text, [regex]::Escape($c.Name))).Count
        $lines += ("{0}: {1}" -f $c.Name, $count)
        if ($c.Bad -and $count -gt 0) {
            Log "$($c.Name) count=$count" "ERROR"
        } else {
            Log "$($c.Name) count=$count"
        }
    }
    $oom = ([regex]::Matches($text, "OutOfMemoryError|GC_FOR_ALLOC|WaitForGcToComplete")).Count
    $lines += "gc/oom-ish=$oom"
    $fatal = Select-String -Path $LogFile -Pattern "FATAL EXCEPTION|OutOfMemoryError" -Context 0, 8
    if ($fatal) {
        $lines += ""
        $lines += "---- fatals ----"
        $fatal | ForEach-Object { $lines += $_.Line }
    }
    $hutes = Select-String -Path $LogFile -Pattern "HUTEST|Automation" | Select-Object -First 80
    if ($hutes) {
        $lines += ""
        $lines += "---- automation / HUTEST ----"
        $hutes | ForEach-Object { $lines += $_.Line }
    }
    $lines | Set-Content -Path $summary -Encoding UTF8
    Log "analysis written $summary"
}

try {
    Connect-Device
    Install-ApkIfNeeded
    Grant-AllPermissions
    New-Fixtures
    Push-Fixtures
    Clear-Logcat
    Start-App
    Dismiss-FirstRunDialogs
    Save-Screenshot "01-first-launch"
    $imported = Import-BackupUi
    Log "backup imported=$imported"
    Start-Sleep -Seconds 2
    Start-App
    Dismiss-FirstRunDialogs
    foreach ($theme in @("hu_test_eco.tboxtheme", "hu_test_nor.tboxtheme", "hu_test_spt.tboxtheme")) {
        Apply-ThemeFile $theme | Out-Null
    }
    Assign-DriveModeThemes
    Switch-DriveModes
    Test-Automations
    $logFile = Collect-Logs
    Analyze-Logs $logFile
    Save-Screenshot "99-final"
    Log "DONE results=$OutDir"
} catch {
    Log $_ "ERROR"
    Save-Screenshot "error"
    throw
} finally {
    $reportPath = Save-Report
    Write-Host "REPORT $reportPath"
}
