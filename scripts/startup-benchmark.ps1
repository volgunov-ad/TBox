param(
    [string]$Adb = "D:\Tools\scrcpy-win64-v3.3.4\adb.exe",
    [string]$Device = "192.168.1.128:5555",
    [string]$Package = "vad.dashing.tbox",
    [string]$Activity = "vad.dashing.tbox/.MainActivity",
    [int]$Runs = 5,
    [int]$SettleSeconds = 14,
    [string]$Label = "run",
    [string]$OutDir = "benchmark-results"
)

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
    & $Adb -s $Device @Args
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $($Args -join ' ')" }
}

Invoke-Adb connect $Device | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outFile = Join-Path $OutDir "$Label-$stamp.txt"

"Benchmark label: $Label" | Out-File -FilePath $outFile -Encoding utf8
"Runs: $Runs" | Out-File -FilePath $outFile -Append -Encoding utf8
"Device: $Device" | Out-File -FilePath $outFile -Append -Encoding utf8
"" | Out-File -FilePath $outFile -Append -Encoding utf8

for ($i = 1; $i -le $Runs; $i++) {
    "=== Run $i/$Runs ===" | Out-File -FilePath $outFile -Append -Encoding utf8
    Invoke-Adb shell am force-stop $Package | Out-Null
    Start-Sleep -Seconds 2
    Invoke-Adb logcat -c | Out-Null
    $startOut = Invoke-Adb shell am start -W -n $Activity 2>&1 | Out-String
    $startOut.Trim() | Out-File -FilePath $outFile -Append -Encoding utf8
    Start-Sleep -Seconds $SettleSeconds
    $logs = Invoke-Adb logcat -d -s TboxTimings:D 2>&1 | Out-String
    if ($logs.Trim().Length -eq 0) {
        "(no TboxTimings lines captured)" | Out-File -FilePath $outFile -Append -Encoding utf8
    } else {
        $logs.Trim() | Out-File -FilePath $outFile -Append -Encoding utf8
    }
    "" | Out-File -FilePath $outFile -Append -Encoding utf8
    Start-Sleep -Seconds 3
}

Write-Output $outFile
