param([switch]$DryRun)

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$known = 'CP210|CH340|CH341|CH9102|USB-SERIAL|FTDI|FT232|USB Serial Port|PL2303|Prolific|USB-UART|UART Bridge'
$ports = @(Get-CimInstance Win32_PnPEntity |
    Where-Object { $_.Name -match '\((COM\d+)\)' -and $_.Name -match $known } |
    ForEach-Object { [pscustomobject]@{ Port = [regex]::Match($_.Name, '\((COM\d+)\)').Groups[1].Value; Name = $_.Name } } |
    Sort-Object Port -Unique)

$port = ''
if ($ports.Count -eq 1) {
    $port = $ports[0].Port
    Write-Host "Адаптер: $($ports[0].Name)"
} elseif ($ports.Count -gt 1) {
    Write-Host 'Найдено несколько USB-UART адаптеров:'
    for ($i = 0; $i -lt $ports.Count; $i++) { Write-Host ("  {0}  {1}" -f ($i + 1), $ports[$i].Name) }
    $n = Read-Host 'Номер'
    if ($n -match '^\d+$' -and [int]$n -ge 1 -and [int]$n -le $ports.Count) { $port = $ports[[int]$n - 1].Port }
}
if (-not $port) {
    Write-Host 'USB-UART адаптер не найден автоматически. Проверьте драйвер в Диспетчере устройств («Порты (COM и LPT)»).' -ForegroundColor Yellow
    Write-Host ('Доступные порты: ' + (([System.IO.Ports.SerialPort]::GetPortNames() | Sort-Object) -join ', '))
    $port = Read-Host 'COM-порт вручную (например COM5)'
    if (-not $port) { return }
}
Write-Host "Порт: $port"
$params = @{
    Port    = $port
    DumpDir = Join-Path $here 'gpt_2026-10-20'
    LogDir  = Join-Path $here 'logs'
}
if ($DryRun) { $params['DryRun'] = $true }
& (Join-Path $here 'hu_rescue.ps1') @params
