param(
    [string]$Adb = "D:\Tools\scrcpy-win64-v3.3.4\adb.exe",
    [string]$Device = "192.168.1.128:5555",
    [int]$Runs = 5,
    [int]$SettleSeconds = 16,
    [string]$Label = "run",
    [string]$OutDir = "benchmark-results"
)

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outFile = Join-Path $OutDir "$Label-$stamp.txt"

function Get-Sigma([string]$Line, [string]$Marker) {
    $matches = [regex]::Matches($Line, [regex]::Escape($Marker) + '.*?\(\S+ (\d+)ms\)')
    if ($matches.Count -eq 0) { return $null }
    return [int]$matches[$matches.Count - 1].Groups[1].Value
}

function Get-Segment([string]$Line, [string]$Begin, [string]$End) {
    $begin = Get-Sigma $Line $Begin
    $end = Get-Sigma $Line $End
    if ($null -eq $begin -or $null -eq $end) { return $null }
    return $end - $begin
}

function Avg([object[]]$Values) {
    $nums = $Values | Where-Object { $_ -ne $null }
    if (-not $nums) { return $null }
    return [math]::Round(($nums | Measure-Object -Average).Average, 1)
}

"Benchmark label: $Label" | Out-File -FilePath $outFile -Encoding utf8
"Runs: $Runs" | Out-File -FilePath $outFile -Append -Encoding utf8
"" | Out-File -FilePath $outFile -Append -Encoding utf8

$rows = @()
for ($i = 1; $i -le $Runs; $i++) {
    "=== Run $i/$Runs ===" | Out-File -FilePath $outFile -Append -Encoding utf8
    & $Adb -s $Device shell am force-stop vad.dashing.tbox | Out-Null
    Start-Sleep -Seconds 2
    & $Adb -s $Device logcat -c | Out-Null
    $startOut = & $Adb -s $Device shell am start -W -n vad.dashing.tbox/.MainActivity 2>&1 | Out-String
    $startOut.Trim() | Out-File -FilePath $outFile -Append -Encoding utf8
    Start-Sleep -Seconds $SettleSeconds
    $logs = (& $Adb -s $Device logcat -d 2>&1 | Select-String "TboxTimings") | ForEach-Object { $_.Line }
    if ($logs) {
        $logs | Out-File -FilePath $outFile -Append -Encoding utf8
    } else {
        "(no TboxTimings lines captured)" | Out-File -FilePath $outFile -Append -Encoding utf8
    }
    "" | Out-File -FilePath $outFile -Append -Encoding utf8

    $totalTime = if ($startOut -match 'TotalTime:\s*(\d+)') { [int]$Matches[1] } else { $null }
    $mainLine = $logs | Where-Object { $_ -match 'Timings.MainActivity:' } | Select-Object -First 1
    $startupLine = $logs | Where-Object { $_ -match 'Timings.startup:' } | Select-Object -First 1
    $startupDataLine = $logs | Where-Object { $_ -match 'Timings.startup_data:' } | Select-Object -First 1
    $wallListLine = $logs | Where-Object { $_ -match 'main_wallpaper_list_begin' } | Select-Object -First 1
    $wallDecodeLine = $logs | Where-Object { $_ -match 'main_wallpaper_decode_begin' } | Select-Object -First 1

    $rows += [pscustomobject]@{
        Run = $i
        TotalTime = $totalTime
        MainActivityMs = if ($mainLine) { Get-Sigma $mainLine 'main_first_global_layout' } else { $null }
        StartupMs = if ($startupLine) { Get-Sigma $startupLine 'startup_running' } else { $null }
        StartupDataMs = if ($startupDataLine) { Get-Sigma $startupDataLine 'trips_parse_done' } else { $null }
        WallpaperListMs = if ($wallListLine) { Get-Segment $wallListLine 'main_wallpaper_list_begin' 'main_wallpaper_list_done' } else { $null }
        WallpaperDecodeMs = if ($wallDecodeLine) { Get-Segment $wallDecodeLine 'main_wallpaper_decode_begin' 'main_wallpaper_decode_done' } else { $null }
    }
    Start-Sleep -Seconds 2
}

"" | Out-File -FilePath $outFile -Append -Encoding utf8
"=== Summary ($Label) ===" | Out-File -FilePath $outFile -Append -Encoding utf8
$rows | Format-Table -AutoSize | Out-String | Out-File -FilePath $outFile -Append -Encoding utf8

$summary = [pscustomobject]@{
    Label = $Label
    Runs = $Runs
    AvgTotalTime = Avg $rows.TotalTime
    AvgMainActivityMs = Avg $rows.MainActivityMs
    AvgStartupMs = Avg $rows.StartupMs
    AvgStartupDataMs = Avg $rows.StartupDataMs
    AvgWallpaperListMs = Avg $rows.WallpaperListMs
    AvgWallpaperDecodeMs = Avg $rows.WallpaperDecodeMs
}
$summary | Format-List | Out-File -FilePath $outFile -Append -Encoding utf8
Write-Output $outFile
Write-Output ($summary | ConvertTo-Json -Compress)
