# Per-process CPU snapshot on the HU: start time (s from boot), cumulative CPU (s), RSS (MB), sorted by CPU.
# Writes <OutDir>\<Tag>.csv and <Tag>.raw.txt. See docs/BOOT_AUTOSTART_DEVICE_RU.md §5.
param(
    [Parameter(Mandatory)][string]$Tag,
    [string]$Device = "192.168.1.128:5555",
    [string]$OutDir = (Join-Path (Get-Location) "hu_boot_results"),
    [int]$Top = 40
)
New-Item -ItemType Directory -Force $OutDir | Out-Null
$lf = Join-Path $env:TEMP "hu_proc_cpu.sh"
[IO.File]::WriteAllText($lf, ((Get-Content -Raw (Join-Path $PSScriptRoot "hu_proc_cpu.sh")) -replace "`r", ""))
adb -s $Device push $lf /data/local/tmp/hu_proc_cpu.sh | Out-Null
$raw = adb -s $Device shell sh /data/local/tmp/hu_proc_cpu.sh
$raw | Out-File "$OutDir\$Tag.raw.txt"
$hz = 100
$up = [double](($raw | Select-String '^UPTIME (\S+)').Matches[0].Groups[1].Value)
$rows = foreach ($l in $raw) {
    if ($l -notmatch '^P (\d+) \((.*)\) (.*)\|(.*)$') { continue }
    $procId = [int]$Matches[1]; $comm = $Matches[2]; $f = $Matches[3] -split ' '; $cmd = $Matches[4].Trim()
    # $f[0] is stat field 3 (state): utime=14, stime=15, starttime=22, rss=24 (pages)
    [pscustomobject]@{
        pid     = $procId
        name    = $(if ($cmd) { $cmd } else { "[$comm]" })
        start_s = [math]::Round([double]$f[19] / $hz, 1)
        cpu_s   = [math]::Round(([double]$f[11] + [double]$f[12]) / $hz, 1)
        rss_mb  = [math]::Round([double]$f[21] * 4 / 1024, 0)
    }
}
$sorted = $rows | Sort-Object cpu_s -Descending
$sorted | Export-Csv -NoTypeInformation -Encoding UTF8 "$OutDir\$Tag.csv"
"uptime=$up s, processes=$($rows.Count), total cpu=$([math]::Round(($rows | Measure-Object cpu_s -Sum).Sum, 1)) s"
$sorted | Select-Object -First $Top | Format-Table -AutoSize pid, start_s, cpu_s, rss_mb, name | Out-String -Width 200
