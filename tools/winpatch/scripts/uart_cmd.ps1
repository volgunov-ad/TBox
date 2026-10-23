param(
    [string]$Port = 'COM21',
    [int]$Baud = 921600,
    [Parameter(Mandatory = $true)][string]$Command,
    [int]$CollectMs = 4000,
    [string]$RawOut
)
$ErrorActionPreference = 'Stop'
$sp = New-Object System.IO.Ports.SerialPort $Port, $Baud, 'None', 8, 'One'
$sp.ReadTimeout = 150
$sp.WriteTimeout = 500
$sp.Open()
try {
    Start-Sleep -Milliseconds 100
    $null = $sp.ReadExisting()
    function Send-Chunks([string]$s) {
        $b = [System.Text.Encoding]::ASCII.GetBytes($s)
        for ($i = 0; $i -lt $b.Length; $i += 4) {
            $len = [Math]::Min(4, $b.Length - $i)
            $sp.Write($b, $i, $len)
            Start-Sleep -Milliseconds 20
        }
    }
    $sp.Write("`r")
    Start-Sleep -Milliseconds 250
    $null = $sp.ReadExisting()
    Send-Chunks "echo MK1S; $Command; echo MK1E`r"
    $sb = New-Object System.Text.StringBuilder
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    while ($sw.ElapsedMilliseconds -lt $CollectMs) {
        try { $chunk = $sp.ReadExisting() } catch { $chunk = $null }
        if ($chunk) { [void]$sb.Append($chunk) }
        if ($sb.ToString() -match '(?m)^MK1E\r?$') { break }
        Start-Sleep -Milliseconds 20
    }
    $raw = $sb.ToString()
    if ($RawOut) { [System.IO.File]::WriteAllText($RawOut, $raw) }
    $clean = [regex]::Replace($raw, '\[\d+\.\d{6}\]\[\d{2}-\d{2} \d{2}:\d{2}:\d{2}\] <\d>\[\w\]\(\d+\)[^\r\n]*reg_val=0x02!', '')
    $lines = $clean -split "`r?`n" | ForEach-Object { $_.TrimEnd() } | Where-Object { $_ -ne '' }
    $started = $false
    $out = New-Object System.Collections.Generic.List[string]
    foreach ($l in $lines) {
        if ($l -eq 'MK1S') { $started = $true; continue }
        if ($l -eq 'MK1E') { break }
        if ($started) { $out.Add($l) }
    }
    Write-Output ($out -join "`n")
} finally {
    $sp.Close()
}
