#Requires -Version 5.1
<#
.SYNOPSIS
    Восстановление ГУ A10 (Adayo, Autochips ac8x) через отладочный UART.

.DESCRIPTION
    Работает в Windows PowerShell 5.1 (встроенный System.IO.Ports, ничего не ставить).
    Адаптер CP210x, 921600 8N1, подключение — docs/HEAD_UNITS_DEVICE_NOTES_RU.md §2.1; уровни поломки — §6.4.

    Меню: слежение за загрузкой (этап по логу), диагностика, возврат ADB по Wi-Fi, добавление сети Wi-Fi,
    правка GPT (перенос записи userdata, восстановление одной копии по другой, таблицы из дампа),
    запись раздела с USB-флешки, перезагрузка, ручной терминал.

    GPT пишется так же, как в программе «Расширение /data»: новые таблицы строятся из текущих (меняются только
    нужные поля и CRC), на ГУ собираются из текущей копии и изменённых байтов, сверяются по MD5, затем при снятой
    защите eMMC пишется резервная копия, потом основная, каждая с чтением обратно; при ошибке возвращается прежняя.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools\hu_rescue\hu_rescue.ps1 -Port COM21

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools\hu_rescue\hu_rescue.ps1 -DryRun
    Всё, кроме записи на eMMC: таблицы собираются и сверяются на ГУ в /cache/hu_rescue.
#>
[CmdletBinding()]
param(
    [string]$Port = 'COM21',
    [int]$Baud = 921600,
    [string]$DumpDir = 'D:\Dashing\Android10-VHAL\emmc_dump_2026-10-14',
    [string]$LogDir = '',
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

$Mmc = '/dev/block/mmcblk0'
$Work = '/cache/hu_rescue'
$WifiStore = '/data/misc/wifi/WifiConfigStore.xml'
# The CarPlay USB port: a dual-role Cadence controller, host by default.
$Udc = '13010000.cdncusb'
$DualRole = '/sys/class/dual_role_usb/dual-role-usb20'
$Gadget = '/config/usb_gadget/g1'
# Kernel console lines land in the middle of command output; each one ends with its own newline.
$KernelLine = [regex]'\[\s*\d+\.\d{6}\](?:\[[^\]\r\n]*\])?\s*<\d>[^\n]*\n'
$Latin1 = [Text.Encoding]::GetEncoding(28591)

#region GPT (C#)
if (-not ('HuRescue.Gpt' -as [type])) {
    Add-Type -Language CSharp -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Text;

namespace HuRescue {
    public class GptHeader {
        public int HeaderSize;
        public long CurrentLba, BackupLba, FirstUsable, LastUsable, EntriesLba;
        public int EntryCount, EntrySize;
        public uint EntriesCrc;
    }

    public class GptEntry {
        public int Index;
        public long First, Last;
        public string Name;
        public long Sectors { get { return Last - First + 1; } }
    }

    public class GptCopy {
        public GptHeader Header;
        public int HeaderOffset, ArrayOffset;
        public byte[] Array;
        public List<GptEntry> Entries;

        public GptEntry Find(string name) {
            GptEntry found = null;
            foreach (GptEntry e in Entries) {
                if (e.Name != name) continue;
                if (found != null) throw new Exception("в GPT несколько записей «" + name + "»");
                found = e;
            }
            if (found == null) throw new Exception("в GPT нет записи «" + name + "»");
            return found;
        }
    }

    public static class Gpt {
        public const int Sector = 512;
        const int OffHeaderCrc = 16, OffEntriesCrc = 88, EntryOffFirst = 32;
        static readonly uint[] Table = MakeTable();

        static uint[] MakeTable() {
            uint[] t = new uint[256];
            for (uint i = 0; i < 256; i++) {
                uint c = i;
                for (int k = 0; k < 8; k++) c = (c & 1) != 0 ? 0xEDB88320u ^ (c >> 1) : c >> 1;
                t[i] = c;
            }
            return t;
        }

        public static uint Crc32(byte[] b, int off, int len) {
            uint c = 0xFFFFFFFFu;
            for (int i = off; i < off + len; i++) c = Table[(c ^ b[i]) & 0xFF] ^ (c >> 8);
            return c ^ 0xFFFFFFFFu;
        }

        static void PutU32(byte[] b, int o, uint v) { System.Array.Copy(BitConverter.GetBytes(v), 0, b, o, 4); }
        static void PutI64(byte[] b, int o, long v) { System.Array.Copy(BitConverter.GetBytes(v), 0, b, o, 8); }

        public static uint HeaderCrc(byte[] b, int off, int size) {
            byte[] c = new byte[size];
            System.Array.Copy(b, off, c, 0, size);
            for (int i = OffHeaderCrc; i < OffHeaderCrc + 4; i++) c[i] = 0;
            return Crc32(c, 0, size);
        }

        public static GptHeader ParseHeader(byte[] b, int off) {
            if (off < 0 || off + 92 > b.Length) throw new Exception("заголовок вне образа");
            if (Encoding.ASCII.GetString(b, off, 8) != "EFI PART") throw new Exception("нет подписи EFI PART");
            GptHeader h = new GptHeader();
            h.HeaderSize = BitConverter.ToInt32(b, off + 12);
            if (h.HeaderSize < 92 || h.HeaderSize > Sector) throw new Exception("неверный размер заголовка " + h.HeaderSize);
            if (HeaderCrc(b, off, h.HeaderSize) != BitConverter.ToUInt32(b, off + OffHeaderCrc)) throw new Exception("неверная CRC заголовка");
            h.CurrentLba = BitConverter.ToInt64(b, off + 24);
            h.BackupLba = BitConverter.ToInt64(b, off + 32);
            h.FirstUsable = BitConverter.ToInt64(b, off + 40);
            h.LastUsable = BitConverter.ToInt64(b, off + 48);
            h.EntriesLba = BitConverter.ToInt64(b, off + 72);
            h.EntryCount = BitConverter.ToInt32(b, off + 80);
            h.EntrySize = BitConverter.ToInt32(b, off + 84);
            h.EntriesCrc = BitConverter.ToUInt32(b, off + OffEntriesCrc);
            if (h.EntrySize < 128 || h.EntrySize % 128 != 0 || h.EntryCount < 1 || h.EntryCount > 1024)
                throw new Exception("неверная раскладка записей " + h.EntryCount + " x " + h.EntrySize);
            return h;
        }

        public static List<GptEntry> Entries(byte[] arr, int count, int size) {
            List<GptEntry> list = new List<GptEntry>();
            for (int i = 0; i < count; i++) {
                int off = i * size;
                bool used = false;
                for (int k = 0; k < 16; k++) if (arr[off + k] != 0) { used = true; break; }
                if (!used) continue;
                GptEntry e = new GptEntry();
                e.Index = i + 1;
                e.First = BitConverter.ToInt64(arr, off + EntryOffFirst);
                e.Last = BitConverter.ToInt64(arr, off + EntryOffFirst + 8);
                e.Name = Encoding.Unicode.GetString(arr, off + 56, 72).TrimEnd('\0');
                list.Add(e);
            }
            return list;
        }

        static GptCopy Finish(GptHeader h, int hoff, long aoff, byte[] img, string what) {
            int len = h.EntryCount * h.EntrySize;
            if (aoff < 0 || aoff + len > img.Length) throw new Exception(what + ": массив записей вне образа");
            byte[] arr = new byte[len];
            System.Array.Copy(img, (int)aoff, arr, 0, len);
            if (Crc32(arr, 0, len) != h.EntriesCrc) throw new Exception(what + ": неверная CRC массива записей");
            GptCopy c = new GptCopy();
            c.Header = h;
            c.HeaderOffset = hoff;
            c.ArrayOffset = (int)aoff;
            c.Array = arr;
            c.Entries = Entries(arr, h.EntryCount, h.EntrySize);
            return c;
        }

        /// <summary>LBA 0 .. first usable - 1.</summary>
        public static GptCopy CheckPrimary(byte[] p, long disk) {
            GptHeader h;
            try { h = ParseHeader(p, Sector); } catch (Exception e) { throw new Exception("основная: " + e.Message); }
            if (h.CurrentLba != 1 || h.BackupLba != disk - 1) throw new Exception("основная: заголовок от диска другого размера");
            if (h.FirstUsable * Sector != p.Length) throw new Exception("основная: размер образа не совпадает с first usable");
            if (h.EntriesLba < 2) throw new Exception("основная: массив записей на месте заголовка");
            return Finish(h, Sector, h.EntriesLba * Sector, p, "основная");
        }

        /// <summary>Last usable + 1 .. end of disk.</summary>
        public static GptCopy CheckSecondary(byte[] s, long disk) {
            int hoff = s.Length - Sector;
            GptHeader h;
            try { h = ParseHeader(s, hoff); } catch (Exception e) { throw new Exception("резервная: " + e.Message); }
            long slba = disk - s.Length / Sector;
            if (h.CurrentLba != disk - 1 || h.BackupLba != 1) throw new Exception("резервная: заголовок от диска другого размера");
            if (h.LastUsable + 1 != slba) throw new Exception("резервная: last usable не совпадает с началом копии");
            if (h.EntriesLba < slba) throw new Exception("резервная: массив записей вне копии");
            GptCopy c = Finish(h, hoff, (h.EntriesLba - slba) * Sector, s, "резервная");
            if (c.ArrayOffset + c.Array.Length > hoff) throw new Exception("резервная: массив записей заходит на заголовок");
            return c;
        }

        public static bool Consistent(GptCopy p, GptCopy s) {
            if (p.Header.FirstUsable != s.Header.FirstUsable || p.Header.LastUsable != s.Header.LastUsable) return false;
            if (p.Header.EntryCount != s.Header.EntryCount || p.Header.EntrySize != s.Header.EntrySize) return false;
            if (p.Array.Length != s.Array.Length) return false;
            for (int i = 0; i < p.Array.Length; i++) if (p.Array[i] != s.Array[i]) return false;
            return true;
        }

        /// <summary>Both copies valid, identical entries, no overlaps, everything inside the usable range.</summary>
        public static GptCopy Validate(byte[] p, byte[] s, long disk) {
            GptCopy cp = CheckPrimary(p, disk);
            GptCopy cs = CheckSecondary(s, disk);
            if (!Consistent(cp, cs)) throw new Exception("копии различаются");
            List<GptEntry> sorted = new List<GptEntry>(cp.Entries);
            sorted.Sort(delegate(GptEntry a, GptEntry b) { return a.First.CompareTo(b.First); });
            for (int i = 0; i < sorted.Count; i++) {
                GptEntry e = sorted[i];
                if (e.Last < e.First) throw new Exception(e.Name + ": конец раньше начала");
                // The vendor preloader entry starts at LBA 0 and covers the GPT itself.
                if (e.First != 0 && (e.First < cp.Header.FirstUsable || e.Last > cp.Header.LastUsable))
                    throw new Exception(e.Name + ": вне usable-области");
                if (i > 0 && sorted[i - 1].Last >= e.First) throw new Exception(sorted[i - 1].Name + " и " + e.Name + " пересекаются");
            }
            return cp;
        }

        static void Seal(byte[] b, int hoff, int hsize, uint arrayCrc) {
            PutU32(b, hoff + OffEntriesCrc, arrayCrc);
            PutU32(b, hoff + OffHeaderCrc, HeaderCrc(b, hoff, hsize));
        }

        /// <summary>The tables with entry [name] at first..last; nothing but its LBAs and the CRCs changes.</summary>
        public static byte[][] Relocate(byte[] p, byte[] s, long disk, string name, long first, long last) {
            GptCopy cp = Validate(p, s, disk);
            GptCopy cs = CheckSecondary(s, disk);
            GptEntry part = cp.Find(name);
            if (first == part.First && last == part.Last) throw new Exception("«" + name + "» уже на этом месте");
            if (last < first) throw new Exception("конец раньше начала");
            if (!(first > part.Last || last < part.First)) throw new Exception("новое место пересекается с текущим «" + name + "»");
            int eoff = (part.Index - 1) * cp.Header.EntrySize + EntryOffFirst;
            byte[] arr = (byte[])cp.Array.Clone();
            PutI64(arr, eoff, first);
            PutI64(arr, eoff + 8, last);
            uint crc = Crc32(arr, 0, arr.Length);
            byte[] np = (byte[])p.Clone();
            System.Array.Copy(arr, 0, np, cp.ArrayOffset, arr.Length);
            Seal(np, cp.HeaderOffset, cp.Header.HeaderSize, crc);
            byte[] ns = (byte[])s.Clone();
            System.Array.Copy(arr, 0, ns, cs.ArrayOffset, arr.Length);
            Seal(ns, cs.HeaderOffset, cs.Header.HeaderSize, crc);
            CheckDiff(p, np, cp, eoff);
            CheckDiff(s, ns, cs, eoff);
            GptEntry moved = Validate(np, ns, disk).Find(name);
            if (moved.First != first || moved.Last != last) throw new Exception("перенесённая запись не сходится");
            return new byte[][] { np, ns };
        }

        static void CheckDiff(byte[] a, byte[] b, GptCopy c, int eoff) {
            for (int i = 0; i < a.Length; i++) {
                if (a[i] == b[i]) continue;
                bool entry = i >= c.ArrayOffset + eoff && i < c.ArrayOffset + eoff + 16;
                bool hcrc = i >= c.HeaderOffset + OffHeaderCrc && i < c.HeaderOffset + OffHeaderCrc + 4;
                bool acrc = i >= c.HeaderOffset + OffEntriesCrc && i < c.HeaderOffset + OffEntriesCrc + 4;
                if (!entry && !hcrc && !acrc) throw new Exception("лишнее изменение в байте " + i);
            }
        }

        /// <summary>Secondary copy rebuilt from a valid primary (array first, header in the last sector, zeros between).</summary>
        public static byte[] SecondaryFromPrimary(byte[] p, long disk, int secondarySectors) {
            GptCopy cp = CheckPrimary(p, disk);
            long slba = cp.Header.LastUsable + 1;
            if (disk - slba != secondarySectors) throw new Exception("размер резервной копии не сходится с основной");
            byte[] s = new byte[secondarySectors * Sector];
            System.Array.Copy(cp.Array, 0, s, 0, cp.Array.Length);
            int hoff = s.Length - Sector;
            System.Array.Copy(p, Sector, s, hoff, Sector);
            PutI64(s, hoff + 24, disk - 1);
            PutI64(s, hoff + 32, 1);
            PutI64(s, hoff + 72, slba);
            PutU32(s, hoff + OffHeaderCrc, HeaderCrc(s, hoff, cp.Header.HeaderSize));
            CheckSecondary(s, disk);
            return s;
        }

        /// <summary>Primary copy rebuilt from a valid secondary; [mbr] is kept when it is a protective MBR.</summary>
        public static byte[] PrimaryFromSecondary(byte[] s, long disk, byte[] mbr) {
            GptCopy cs = CheckSecondary(s, disk);
            byte[] p = new byte[cs.Header.FirstUsable * Sector];
            if (2 * Sector + cs.Array.Length > p.Length) throw new Exception("массив записей не помещается до first usable");
            if (mbr != null && mbr.Length >= Sector && mbr[510] == 0x55 && mbr[511] == 0xAA && mbr[450] == 0xEE) {
                System.Array.Copy(mbr, 0, p, 0, Sector);
            } else {
                byte[] part = { 0x00, 0x00, 0x00, 0x00, 0xEE, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0xFF };
                System.Array.Copy(part, 0, p, 446, part.Length);
                p[510] = 0x55;
                p[511] = 0xAA;
            }
            System.Array.Copy(s, cs.HeaderOffset, p, Sector, Sector);
            PutI64(p, Sector + 24, 1);
            PutI64(p, Sector + 32, disk - 1);
            PutI64(p, Sector + 72, 2);
            System.Array.Copy(cs.Array, 0, p, 2 * Sector, cs.Array.Length);
            PutU32(p, Sector + OffHeaderCrc, HeaderCrc(p, Sector, cs.Header.HeaderSize));
            CheckPrimary(p, disk);
            return p;
        }

        /// <summary>Human-readable differences between two entry arrays of the same layout.</summary>
        public static List<string> EntryDiff(GptCopy a, GptCopy b) {
            List<string> res = new List<string>();
            int size = a.Header.EntrySize;
            if (size != b.Header.EntrySize || a.Array.Length != b.Array.Length) {
                res.Add("разная раскладка записей");
                return res;
            }
            for (int i = 0; i < a.Header.EntryCount; i++) {
                int off = i * size;
                bool same = true;
                for (int k = 0; k < size; k++) if (a.Array[off + k] != b.Array[off + k]) { same = false; break; }
                if (same) continue;
                string name = Encoding.Unicode.GetString(a.Array, off + 56, 72).TrimEnd('\0');
                string nameB = Encoding.Unicode.GetString(b.Array, off + 56, 72).TrimEnd('\0');
                long fa = BitConverter.ToInt64(a.Array, off + 32), la = BitConverter.ToInt64(a.Array, off + 40);
                long fb = BitConverter.ToInt64(b.Array, off + 32), lb = BitConverter.ToInt64(b.Array, off + 40);
                ulong ta = BitConverter.ToUInt64(a.Array, off + 48), tb = BitConverter.ToUInt64(b.Array, off + 48);
                List<string> what = new List<string>();
                if (name != nameB) what.Add("имя «" + nameB + "»");
                if (fa != fb || la != lb) what.Add("LBA " + fa + "…" + la + " → " + fb + "…" + lb);
                if (ta != tb) what.Add(string.Format("атрибуты 0x{0:x16} → 0x{1:x16}", ta, tb));
                bool other = false;
                for (int k = 0; k < size; k++) {
                    if (k >= 32 && k < 56) continue;
                    if (k >= 56 && k < 128 && name != nameB) continue;
                    if (a.Array[off + k] != b.Array[off + k]) { other = true; break; }
                }
                if (other) what.Add("служебные байты");
                res.Add("[" + (i + 1) + "] " + (name.Length > 0 ? name : nameB) + ": " + string.Join(", ", what.ToArray()));
            }
            return res;
        }

        /// <summary>Runs [start, length] where a and b differ; runs closer than [gap] bytes are merged.</summary>
        public static List<int[]> DiffRuns(byte[] a, byte[] b, int gap) {
            List<int[]> runs = new List<int[]>();
            int i = 0;
            while (i < a.Length) {
                if (a[i] == b[i]) { i++; continue; }
                int start = i, end = i + 1, j = i + 1;
                while (j < a.Length && j - end < gap) {
                    if (a[j] != b[j]) end = j + 1;
                    j++;
                }
                runs.Add(new int[] { start, end - start });
                i = end;
            }
            return runs;
        }
    }
}
'@
}
#endregion

#region Serial link
$script:Sp = $null
$script:LogWriter = $null
$script:LogPath = ''
$script:Fast = $false
$script:Seq = 0
$script:PrintkSaved = ''
$script:Env = @{}

function Open-Uart {
    if (-not $LogDir) {
        if (Test-Path 'D:\Dashing\Android10-VHAL\uart_logs') { $script:LogDir = 'D:\Dashing\Android10-VHAL\uart_logs' }
        else { $script:LogDir = Join-Path $env:USERPROFILE 'hu_rescue_logs' }
    }
    if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Path $LogDir | Out-Null }
    $script:LogPath = Join-Path $LogDir ('hu_rescue_{0:yyyyMMdd_HHmmss}.log' -f (Get-Date))
    $script:LogWriter = New-Object IO.StreamWriter($script:LogPath, $true, $Latin1)
    $sp = New-Object System.IO.Ports.SerialPort $Port, $Baud, 'None', 8, 'One'
    $sp.Encoding = $Latin1
    $sp.Handshake = 'None'
    $sp.ReadBufferSize = 4MB
    $sp.Open()
    $script:Sp = $sp
}

function Close-Uart {
    if ($script:Sp) { $script:Sp.Close() }
    if ($script:LogWriter) { $script:LogWriter.Close() }
}

function Read-Uart {
    $s = $script:Sp.ReadExisting()
    if ($s) {
        $script:LogWriter.Write($s)
        $script:LogWriter.Flush()
    }
    return $s
}

function Send-Uart([string]$Text) {
    # The console drops input while the kernel floods it; once printk is quiet, full speed is reliable.
    if ($script:Fast) { $chunk = 256; $delay = 2 } else { $chunk = 8; $delay = 15 }
    for ($i = 0; $i -lt $Text.Length; $i += $chunk) {
        $script:Sp.Write($Text.Substring($i, [Math]::Min($chunk, $Text.Length - $i)))
        Start-Sleep -Milliseconds $delay
    }
}

function Remove-KernelLog([string]$Text) {
    return $KernelLine.Replace($Text, '').Replace("`r", '')
}

function Invoke-Hu {
    param([string]$Command, [int]$TimeoutSec = 20, [switch]$AllowFail)
    $script:Seq++
    $n = $script:Seq
    $null = Read-Uart
    $script:LogWriter.Write("`n#>>> $Command`n")
    # Markers split by '' so the echoed input never matches the output.
    Send-Uart ("echo Q''S$n; " + $Command + "; echo Q''E${n}:`$?`r")
    $re = [regex]("(?s)QS$n\n(.*?)QE${n}:(\d+)")
    $acc = New-Object Text.StringBuilder
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        $s = Read-Uart
        if (-not $s) { Start-Sleep -Milliseconds 15; continue }
        [void]$acc.Append($s)
        $m = $re.Match((Remove-KernelLog $acc.ToString()))
        if ($m.Success) {
            $text = $m.Groups[1].Value
            $rc = [int]$m.Groups[2].Value
            [string[]]$lines = @($text.TrimEnd("`n").Split("`n"))
            if ($text.Length -eq 0) { $lines = @() }
            if ($rc -ne 0 -and -not $AllowFail) { throw "ГУ: код $rc у команды: $Command`n$text" }
            return [pscustomobject]@{ Rc = $rc; Text = $text; Lines = $lines }
        }
    }
    Send-Uart ([string][char]3)
    Start-Sleep -Milliseconds 200
    Send-Uart "`r"
    throw "ГУ не ответило за $TimeoutSec с: $Command"
}

function Get-Fields($Lines) {
    $h = @{}
    foreach ($l in $Lines) {
        $i = $l.IndexOf('=')
        if ($i -gt 0) { $h[$l.Substring(0, $i).Trim()] = $l.Substring($i + 1).Trim() }
    }
    return $h
}

function Initialize-HuConsole {
    $script:Fast = $false
    Send-Uart ([string][char]3)
    Start-Sleep -Milliseconds 300
    Send-Uart "`r"
    Start-Sleep -Milliseconds 600
    $null = Read-Uart
    $probe = 'echo "uid=$(id -u)"; echo "printk=$(cat /proc/sys/kernel/printk)"'
    try { $f = Get-Fields (Invoke-Hu $probe -TimeoutSec 8 -AllowFail).Lines }
    catch { throw 'Консоль не отвечает. Посмотрите этап загрузки (пункт 1) и проводку RX/TX (§2.1).' }
    if ($f['uid'] -ne '0') {
        Send-Uart "su`r"
        Start-Sleep -Milliseconds 1500
        $null = Read-Uart
        $f = Get-Fields (Invoke-Hu $probe -TimeoutSec 8 -AllowFail).Lines
        if ($f['uid'] -ne '0') { throw "su не дал root (uid=$($f['uid']))" }
    }
    $pk = (($f['printk'] -split '\s+') | Where-Object { $_ }) -join ' '
    if ($pk -like '1 *') {
        $script:Fast = $true
    } else {
        if (-not $script:PrintkSaved) { $script:PrintkSaved = $pk }
        $r = Invoke-Hu 'echo 1 4 1 7 > /proc/sys/kernel/printk' -AllowFail
        $script:Fast = ($r.Rc -eq 0)
    }
    $e = Invoke-Hu ('echo "slot=$(getprop ro.boot.slot_suffix)"; if [ -e /sbin/recovery ]; then echo mode=recovery; else echo mode=android; fi; ' +
        'echo "disk=$(cat /sys/block/mmcblk0/size)"; command -v base64 >/dev/null && echo b64=1; command -v printf >/dev/null && echo printf=1; ' +
        'echo "booted=$(getprop sys.boot_completed)"; [ -w /proc/mmc_debug ] && echo wp=1; echo "fp=$(getprop ro.build.fingerprint)"')
    $script:Env = Get-Fields $e.Lines
}

function Restore-Printk {
    if (-not $script:PrintkSaved) { return }
    try { $null = Invoke-Hu ("echo $($script:PrintkSaved) > /proc/sys/kernel/printk") -TimeoutSec 5 -AllowFail } catch { }
}
#endregion

#region Transfers
function Get-Slice([byte[]]$Data, [int]$Offset, [int]$Length) {
    $r = New-Object byte[] $Length
    [Array]::Copy($Data, $Offset, $r, 0, $Length)
    return , $r
}

function Get-ErrorText($Err) {
    $e = $Err.Exception
    while ($e.InnerException) { $e = $e.InnerException }
    return $e.Message
}

function Get-Md5([byte[]]$Data) {
    $md5 = [Security.Cryptography.MD5]::Create()
    return ([BitConverter]::ToString($md5.ComputeHash($Data))).Replace('-', '').ToLower()
}

function Get-HuBytes([string]$Producer, [int]$TimeoutSec = 30) {
    for ($try = 1; $try -le 5; $try++) {
        if ($script:Env['b64'] -eq '1') { $enc = 'base64' } else { $enc = 'od -An -v -tx1' }
        $r = Invoke-Hu "$Producer | $enc; $Producer | md5sum" -TimeoutSec $TimeoutSec -AllowFail
        $lines = @($r.Lines)
        $sum = ''
        $data = New-Object Text.StringBuilder
        foreach ($l in $lines) {
            if ($l -match '^([0-9a-f]{32})\s+-$') { $sum = $Matches[1] } else { [void]$data.Append($l) }
        }
        try {
            if ($enc -eq 'base64') { [byte[]]$b = [Convert]::FromBase64String(($data.ToString() -replace '\s', '')) }
            else {
                $hex = $data.ToString() -replace '\s', ''
                [byte[]]$b = New-Object byte[] ([int]($hex.Length / 2))
                for ($i = 0; $i -lt $b.Length; $i++) { $b[$i] = [Convert]::ToByte($hex.Substring($i * 2, 2), 16) }
            }
        } catch { continue }
        if ($sum -and (Get-Md5 $b) -eq $sum) { return , $b }
        Write-Host "  повтор чтения ($try): MD5 не сошёлся" -ForegroundColor DarkYellow
    }
    throw "не удалось прочитать без ошибок: $Producer"
}

function Get-HuSectors([long]$Lba, [int]$Count) {
    $null = Invoke-Hu "blockdev --flushbufs $Mmc; echo 3 > /proc/sys/vm/drop_caches" -AllowFail
    $ms = New-Object IO.MemoryStream
    for ($done = 0; $done -lt $Count; $done += 16) {
        $c = [Math]::Min(16, $Count - $done)
        [byte[]]$b = Get-HuBytes "dd if=$Mmc bs=512 skip=$($Lba + $done) count=$c 2>/dev/null"
        if ($b.Length -ne $c * 512) { throw "прочитано $($b.Length) байт вместо $($c * 512) (LBA $($Lba + $done))" }
        $ms.Write($b, 0, $b.Length)
    }
    return , $ms.ToArray()
}

function Send-HuFile([byte[]]$Data, [string]$Remote) {
    if ($script:Env['b64'] -ne '1') { throw 'на ГУ нет base64' }
    $b64 = [Convert]::ToBase64String($Data)
    if ($script:Fast) { $step = 2000 } else { $step = 64 }
    $null = Invoke-Hu ": > '$Remote.b64'"
    for ($i = 0; $i -lt $b64.Length; $i += $step) {
        $null = Invoke-Hu ("echo '" + $b64.Substring($i, [Math]::Min($step, $b64.Length - $i)) + "' >> '$Remote.b64'")
        Write-Progress -Activity "Передача $Remote" -PercentComplete ([int](100 * $i / [Math]::Max(1, $b64.Length)))
    }
    Write-Progress -Activity "Передача $Remote" -Completed
    $r = Invoke-Hu "base64 -d '$Remote.b64' > '$Remote' && rm '$Remote.b64' && md5sum '$Remote'"
    if ($r.Text -notmatch (Get-Md5 $Data)) { throw "$Remote передан с ошибкой" }
}

# Builds [Remote] on the HU from [CurRemote] (whose bytes are [Cur]) plus the bytes that differ in [New].
function Build-HuFile([string]$CurRemote, [byte[]]$Cur, [byte[]]$New, [string]$Remote) {
    if ($Cur.Length -ne $New.Length) { throw 'размеры не совпадают' }
    $cmds = New-Object Collections.Generic.List[string]
    $cmds.Add(": > $Remote")
    $pos = 0
    $runs = [HuRescue.Gpt]::DiffRuns($Cur, $New, 16)
    foreach ($run in $runs) {
        $start = $run[0]; $len = $run[1]
        if ($start -gt $pos) { $cmds.Add("dd if=$CurRemote bs=1 skip=$pos count=$($start - $pos) 2>/dev/null >> $Remote") }
        for ($o = $start; $o -lt $start + $len; $o += 64) {
            $n = [Math]::Min(64, $start + $len - $o)
            if ($script:Env['printf'] -eq '1') {
                $esc = -join (0..($n - 1) | ForEach-Object { '\' + [Convert]::ToString($New[$o + $_], 8).PadLeft(3, '0') })
                $cmds.Add("printf '$esc' >> $Remote")
            } elseif ($script:Env['b64'] -eq '1') {
                $cmds.Add("echo '" + [Convert]::ToBase64String($New, $o, $n) + "' | base64 -d >> $Remote")
            } else { throw 'на ГУ нет ни printf, ни base64' }
        }
        $pos = $start + $len
    }
    if ($pos -lt $New.Length) { $cmds.Add("dd if=$CurRemote bs=1 skip=$pos count=$($New.Length - $pos) 2>/dev/null >> $Remote") }
    $batch = ''
    foreach ($c in $cmds) {
        if ($batch -and ($batch.Length + $c.Length -gt 1500 -or -not $script:Fast)) { $null = Invoke-Hu $batch; $batch = '' }
        if ($batch) { $batch += " && $c" } else { $batch = $c }
    }
    if ($batch) { $null = Invoke-Hu $batch }
    $r = Invoke-Hu "md5sum $Remote"
    if ($r.Text -notmatch (Get-Md5 $New)) { throw "$Remote собран с ошибкой" }
}
#endregion

#region GPT on the HU
function Save-Local([byte[]]$Data, [string]$Name) {
    $path = Join-Path $LogDir ('{0:yyyyMMdd_HHmmss}_{1}' -f (Get-Date), $Name)
    [IO.File]::WriteAllBytes($path, $Data)
    return $path
}

function Read-HuGpt {
    $sectors = [long]$script:Env['disk']
    if ($sectors -le 0) { throw 'не удалось узнать размер eMMC' }
    Write-Host 'Чтение обеих копий GPT...'
    [byte[]]$head = Get-HuSectors 0 2
    $pSect = 0; $sSect = 0
    foreach ($src in @('primary', 'secondary', 'dump')) {
        try {
            if ($src -eq 'primary') { $h = [HuRescue.Gpt]::ParseHeader($head, 512) }
            elseif ($src -eq 'secondary') { $h = [HuRescue.Gpt]::ParseHeader((Get-HuSectors ($sectors - 1) 1), 0) }
            else {
                $f = Join-Path $DumpDir 'gpt_head_1MiB.img'
                if (-not (Test-Path $f)) { continue }
                $h = [HuRescue.Gpt]::ParseHeader([IO.File]::ReadAllBytes($f), 512)
                Write-Host '  обе копии без заголовка — размеры взяты из дампа' -ForegroundColor Yellow
            }
            $pSect = [int]$h.FirstUsable
            $sSect = [int]($sectors - $h.LastUsable - 1)
            break
        } catch { }
    }
    if ($pSect -lt 34 -or $pSect -gt 2048 -or $sSect -lt 33 -or $sSect -gt 2048) {
        throw "не удалось определить размеры копий GPT (first usable $pSect, хвост $sSect)"
    }
    [byte[]]$p = Get-HuSectors 0 $pSect
    [byte[]]$s = Get-HuSectors ($sectors - $sSect) $sSect
    $null = Save-Local $p 'gpt_primary.bin'
    $null = Save-Local $s 'gpt_secondary.bin'
    return [pscustomobject]@{ Disk = $sectors; P = $p; S = $s; PSect = $pSect; SSect = $sSect; SLba = $sectors - $sSect }
}

function Get-GptCopies($G) {
    $res = [pscustomobject]@{ P = $null; S = $null; PErr = ''; SErr = ''; Same = $false }
    try { $res.P = [HuRescue.Gpt]::CheckPrimary($G.P, $G.Disk) } catch { $res.PErr = Get-ErrorText $_ }
    try { $res.S = [HuRescue.Gpt]::CheckSecondary($G.S, $G.Disk) } catch { $res.SErr = Get-ErrorText $_ }
    if ($res.P -and $res.S) { $res.Same = [HuRescue.Gpt]::Consistent($res.P, $res.S) }
    return $res
}

function Get-MoveRanges($DiskSectors) {
    $r = Invoke-Hu 'cat /cache/dataresize/params 2>/dev/null; grep -E "^(OLD|NEW)_(FIRST|SECT)=" /cache/gptfix/gptfix.sh 2>/dev/null; true' -AllowFail
    $f = Get-Fields $r.Lines
    $src = 'параметры переноса в /cache'
    if (-not $f['OLD_FIRST'] -and $DiskSectors -eq 124993536) {
        $f = @{ OLD_FIRST = '30445568'; OLD_SECT = '4194304'; NEW_FIRST = '60407808'; NEW_SECT = '64585677' }
        $src = 'разметка стенда (eMMC того же размера)'
    }
    if (-not $f['OLD_FIRST']) { return $null }
    return [pscustomobject]@{
        OldFirst = [long]$f['OLD_FIRST']; OldLast = [long]$f['OLD_FIRST'] + [long]$f['OLD_SECT'] - 1
        NewFirst = [long]$f['NEW_FIRST']; NewLast = [long]$f['NEW_FIRST'] + [long]$f['NEW_SECT'] - 1
        Source = $src
    }
}

function Format-Gib([long]$Sectors) { return ('{0:N1} ГиБ' -f ($Sectors * 512 / 1GB)) }

function Show-GptReport($G) {
    $c = Get-GptCopies $G
    foreach ($k in @('P', 'S')) {
        if ($k -eq 'P') { $title = 'Основная GPT' } else { $title = 'Резервная GPT' }
        $copy = $c.$k
        if ($copy) {
            $ud = $copy.Entries | Where-Object { $_.Name -eq 'userdata' } | Select-Object -First 1
            if ($ud) { $where = "userdata LBA $($ud.First)…$($ud.Last) ($(Format-Gib $ud.Sectors))" } else { $where = 'нет userdata' }
            Write-Host ("  {0}: в порядке, {1} записей, {2}" -f $title, $copy.Entries.Count, $where) -ForegroundColor Green
        } else {
            Write-Host ("  {0}: ПОВРЕЖДЕНА — {1}" -f $title, $c."${k}Err") -ForegroundColor Red
        }
    }
    if ($c.P -and $c.S) {
        if ($c.Same) { Write-Host '  Копии совпадают.' -ForegroundColor Green }
        else {
            Write-Host '  Копии РАЗЛИЧАЮТСЯ (основная → резервная):' -ForegroundColor Yellow
            [HuRescue.Gpt]::EntryDiff($c.P, $c.S) | ForEach-Object { Write-Host "    $_" }
        }
    }
    $ranges = Get-MoveRanges $G.Disk
    $valid = $c.P
    if (-not $valid) { $valid = $c.S }
    if ($ranges -and $valid) {
        $ud = $valid.Entries | Where-Object { $_.Name -eq 'userdata' } | Select-Object -First 1
        if ($ud -and $ud.First -eq $ranges.OldFirst) { Write-Host '  userdata на штатном месте (до переноса).' }
        elseif ($ud -and $ud.First -eq $ranges.NewFirst) { Write-Host '  userdata перенесён в хвост eMMC.' }
    }
    $dumpHead = Join-Path $DumpDir 'gpt_head_1MiB.img'
    if ($valid -and (Test-Path $dumpHead)) {
        try {
            $dh = [IO.File]::ReadAllBytes($dumpHead)
            $dc = [HuRescue.Gpt]::CheckPrimary((Get-Slice $dh 0 ($G.PSect * 512)), $G.Disk)
            $d = [HuRescue.Gpt]::EntryDiff($dc, $valid)
            Write-Host "  Отличия от дампа ($(Split-Path $DumpDir -Leaf)): $($d.Count) записей"
            $d | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkGray }
        } catch { Write-Host "  Дамп GPT не разобран: $(Get-ErrorText $_)" -ForegroundColor DarkYellow }
    }
    return $c
}

function Confirm-Write([string]$What) {
    Write-Host ''
    Write-Host $What -ForegroundColor Yellow
    if ($DryRun) { Write-Host '  (режим -DryRun: на eMMC ничего не запишется)' -ForegroundColor Cyan }
    $a = Read-Host 'Выполнить? Введите «да»'
    return ($a.Trim().ToLower() -in @('да', 'yes', 'y'))
}

function Write-HuRegion([string]$File, [long]$Lba, [int]$Count, [string]$Md5) {
    $r = Invoke-Hu ("dd if=$File of=$Mmc bs=512 seek=$Lba count=$Count 2>/dev/null; blockdev --flushbufs $Mmc; " +
        "echo 3 > /proc/sys/vm/drop_caches; dd if=$Mmc bs=512 skip=$Lba count=$Count 2>/dev/null | md5sum") -TimeoutSec 60 -AllowFail
    return ($r.Text -match $Md5)
}

function Write-HuGpt($G, [byte[]]$NewP, [byte[]]$NewS) {
    $null = [HuRescue.Gpt]::Validate($NewP, $NewS, $G.Disk)
    $pChanged = (Get-Md5 $NewP) -ne (Get-Md5 $G.P)
    $sChanged = (Get-Md5 $NewS) -ne (Get-Md5 $G.S)
    if (-not $pChanged -and -not $sChanged) { Write-Host 'Таблицы уже такие, записывать нечего.' -ForegroundColor Green; return $false }
    if ($script:Env['wp'] -ne '1') { throw 'нет /proc/mmc_debug — защиту eMMC не снять' }
    $null = Save-Local $NewP 'gpt_primary_new.bin'
    $null = Save-Local $NewS 'gpt_secondary_new.bin'
    Write-Host 'Подготовка файлов на ГУ...'
    $null = Invoke-Hu "mkdir -p $Work"
    $r = Invoke-Hu ("dd if=$Mmc bs=512 skip=0 count=$($G.PSect) of=$Work/cur_p.bin 2>/dev/null; " +
        "dd if=$Mmc bs=512 skip=$($G.SLba) count=$($G.SSect) of=$Work/cur_s.bin 2>/dev/null; md5sum $Work/cur_p.bin $Work/cur_s.bin")
    if ($r.Text -notmatch (Get-Md5 $G.P) -or $r.Text -notmatch (Get-Md5 $G.S)) { throw 'GPT на ГУ изменилась после чтения — повторите' }
    Build-HuFile "$Work/cur_p.bin" $G.P $NewP "$Work/new_p.bin"
    Build-HuFile "$Work/cur_s.bin" $G.S $NewS "$Work/new_s.bin"
    Write-Host '  новые таблицы собраны на ГУ, MD5 сходится' -ForegroundColor Green
    if ($DryRun) { Write-Host '  -DryRun: запись на eMMC пропущена' -ForegroundColor Cyan; return $false }
    $null = Invoke-Hu 'echo emmc_disable_all_wp > /proc/mmc_debug'
    try {
        if ($sChanged) {
            Write-Host '  запись резервной GPT...'
            if (-not (Write-HuRegion "$Work/new_s.bin" $G.SLba $G.SSect (Get-Md5 $NewS))) {
                $back = Write-HuRegion "$Work/cur_s.bin" $G.SLba $G.SSect (Get-Md5 $G.S)
                throw "резервная GPT не записалась; прежняя возвращена: $back"
            }
        }
        if ($pChanged) {
            Write-Host '  запись основной GPT...'
            if (-not (Write-HuRegion "$Work/new_p.bin" 0 $G.PSect (Get-Md5 $NewP))) {
                $backP = Write-HuRegion "$Work/cur_p.bin" 0 $G.PSect (Get-Md5 $G.P)
                $backS = $true
                if ($sChanged) { $backS = Write-HuRegion "$Work/cur_s.bin" $G.SLba $G.SSect (Get-Md5 $G.S) }
                throw "основная GPT не записалась; прежние возвращены: основная $backP, резервная $backS. Не перезагружайте, пока обе не вернутся."
            }
        }
    } finally {
        $null = Invoke-Hu 'echo emmc_enable_all_wp > /proc/mmc_debug' -AllowFail
    }
    Write-Host 'GPT записана и проверена чтением.' -ForegroundColor Green
    return $true
}
#endregion

#region Actions
function Show-Header {
    Write-Host ''
    Write-Host "=== Восстановление ГУ A10 по UART ($Port, $Baud) ===" -ForegroundColor Cyan
    Write-Host "Лог: $script:LogPath"
    if ($DryRun) { Write-Host 'Режим -DryRun: на eMMC ничего не пишется.' -ForegroundColor Cyan }
}

$Stages = @(
    @{ Re = 'HSM Loader Start|preloader build time'; Stage = 'preloader' }
    @{ Re = 'welcome to lk'; Stage = 'LK' }
    @{ Re = '###Fastboot Boot###'; Stage = 'fastboot LK' }
    @{ Re = 'Booting Linux on physical CPU'; Stage = 'ядро Linux' }
    @{ Re = 'console:/ [$#]'; Stage = 'консоль' }
)
$Notable = 'Parsing (Primary|Secondary) GPT|GPT_LK|atc\.upd|load LK finished|get slot|slot invalid|recovery mode|start jump to kernel|' +
    'pbcb->command:\S|Recovery Boot|enter_fastboot|enter to fastboot|' +
    'Kernel panic|Internal error|Unable to handle|reboot: Restarting|Rebooting|fs_mgr|init: .*(reboot|panic|failed to mount)|userdata|' +
    'error.*gpt'

function Watch-Boot {
    Write-Host 'Слежу за логом UART: этапы загрузки и важные строки. Выход — Esc.' -ForegroundColor Cyan
    $stage = ''
    $since = Get-Date
    $hinted = $false
    $pending = ''
    while ($true) {
        if ([Console]::KeyAvailable -and [Console]::ReadKey($true).Key -eq 'Escape') { break }
        $s = Read-Uart
        if (-not $s) {
            Start-Sleep -Milliseconds 40
            $idle = ((Get-Date) - $since).TotalSeconds
            if (-not $hinted -and $stage -eq 'preloader' -and $idle -gt 20) {
                Write-Host 'preloader не передал управление LK. Если лог стоит — испорчены обе копии GPT или lk/metazone: через UART не восстанавливается (§6.4).' -ForegroundColor Red; $hinted = $true
            } elseif (-not $hinted -and $stage -eq 'LK' -and $idle -gt 30) {
                Write-Host 'LK не перешёл к ядру. Смотрите строки GPT_LK/slot выше; LK может сам уйти в recovery или второй слот.' -ForegroundColor Yellow; $hinted = $true
            } elseif (-not $hinted -and $stage -eq 'ядро Linux' -and $idle -gt 120) {
                Write-Host 'Ядро стартовало, консоли нет. Нажмите Esc и попробуйте пункт 2 (Enter в консоль).' -ForegroundColor Yellow; $hinted = $true
            }
            continue
        }
        $pending += $s.Replace("`r", '')
        $parts = $pending.Split("`n")
        $pending = $parts[-1]
        $lines = @()
        if ($parts.Length -gt 1) { $lines = @($parts[0..($parts.Length - 2)]) }
        if ($pending -match 'console:/ [$#]') { $lines += $pending; $pending = '' }
        foreach ($l in $lines) {
            if (-not $l) { continue }
            foreach ($st in $Stages) {
                if ($l -match $st.Re -and $stage -ne $st.Stage) {
                    $stage = $st.Stage
                    $since = Get-Date
                    $hinted = $false
                    Write-Host ("[{0:HH:mm:ss}] == этап: {1} ==" -f (Get-Date), $stage) -ForegroundColor Cyan
                    if ($stage -eq 'консоль') { Write-Host '  Консоль доступна: выйдите (Esc) и выберите нужный пункт.' -ForegroundColor Green }
                    if ($stage -eq 'fastboot LK') {
                        Write-Host '  ГУ в fastboot: на ПК «fastboot devices» (VID_0BB4&PID_0C01). Примерно через 4 минуты ГУ само уйдёт в обычную загрузку.' -ForegroundColor Green
                    }
                }
            }
            if ($l -match $Notable) {
                $since = Get-Date
                $color = 'Gray'
                if ($l -match 'panic|Internal error|Unable to handle|invalid|fail|error|Secondary GPT') { $color = 'Red' }
                elseif ($l -match 'is valid|consistent|Success') { $color = 'Green' }
                $t = $l
                if ($t.Length -gt 200) { $t = $t.Substring(0, 200) }
                Write-Host "  $t" -ForegroundColor $color
            }
        }
    }
}

function Show-Diagnostics {
    Initialize-HuConsole
    $f = Get-Fields (Invoke-Hu ('echo "uptime=$(cut -d. -f1 /proc/uptime)"; echo "ud_start=$(cat /sys/block/mmcblk0/userdata/start 2>/dev/null)"; ' +
        'echo "ud_size=$(cat /sys/block/mmcblk0/userdata/size 2>/dev/null)"; echo "ip=$(ip -4 addr show wlan0 2>/dev/null | grep -o "inet [0-9.]*")"; ' +
        'echo "adb_port=$(getprop persist.adb.tcp.port)"; echo "adbd=$(getprop init.svc.adbd)"; echo "dataresize=$(cat /cache/dataresize/state 2>/dev/null)"') -AllowFail).Lines
    $df = Invoke-Hu 'df -h /data /cache 2>/dev/null' -AllowFail
    Write-Host ''
    Write-Host ("Режим: {0}, слот {1}, загрузка завершена: {2}, работает {3} с" -f $script:Env['mode'], $script:Env['slot'], $script:Env['booted'], $f['uptime'])
    Write-Host "Прошивка: $($script:Env['fp'])"
    Write-Host ("userdata в ядре: начало {0}, секторов {1}" -f $f['ud_start'], $f['ud_size'])
    $df.Lines | ForEach-Object { Write-Host "  $_" }
    Write-Host ("Wi-Fi: {0}; ADB: порт {1}, adbd {2}; «Расширение /data»: {3}" -f $f['ip'], $f['adb_port'], $f['adbd'], $f['dataresize'])
    $g = Read-HuGpt
    $null = Show-GptReport $g
}

function Enable-Adb {
    Initialize-HuConsole
    $r = Invoke-Hu 'svc wifi enable 2>/dev/null; setprop persist.adb.tcp.port 5555; setprop ctl.restart adbd; sleep 3; ip -4 addr show wlan0 2>/dev/null | grep inet; echo "adbd=$(getprop init.svc.adbd)"' -TimeoutSec 30 -AllowFail
    $r.Lines | ForEach-Object { Write-Host "  $_" }
    $ip = [regex]::Match($r.Text, 'inet (\d+\.\d+\.\d+\.\d+)').Groups[1].Value
    if ($ip) { Write-Host "ADB: adb connect ${ip}:5555" -ForegroundColor Green }
    else { Write-Host 'У wlan0 нет адреса: ГУ не в сети. Добавьте сеть (пункт 4) или проверьте роутер.' -ForegroundColor Yellow }
}

function ConvertTo-XmlText([string]$s) {
    return $s.Replace('&', '&amp;').Replace('<', '&lt;').Replace('>', '&gt;').Replace('"', '&quot;').Replace("'", '&apos;')
}

function Add-WifiNetwork {
    Initialize-HuConsole
    if ($script:Env['mode'] -ne 'android') { throw 'сеть добавляется только в Android (в recovery /data не смонтирован)' }
    $exists = Invoke-Hu "[ -f $WifiStore ]" -AllowFail
    if ($exists.Rc -ne 0) { throw "нет $WifiStore" }
    $ssid = Read-Host 'SSID сети'
    $pass = Read-Host 'Пароль (WPA2-PSK)'
    if (-not $ssid -or $pass.Length -lt 8) { throw 'нужны SSID и пароль не короче 8 символов' }
    $null = Invoke-Hu 'svc wifi enable' -AllowFail
    [byte[]]$orig = Get-HuBytes "cat $WifiStore"
    $null = Save-Local $orig 'WifiConfigStore.xml'
    $xml = [Text.Encoding]::UTF8.GetString($orig)
    $blocks = [regex]::Matches($xml, '(?s)<Network>.*?</Network>')
    if ($blocks.Count -eq 0) { throw 'в файле нет ни одной сохранённой сети — образец блока взять неоткуда' }
    $s = ConvertTo-XmlText $ssid
    $template = $blocks[0].Value
    $replace = $null
    foreach ($b in $blocks) { if ($b.Value -match ('<string name="SSID">&quot;' + [regex]::Escape($s) + '&quot;</string>')) { $replace = $b.Value; $template = $b.Value } }
    $block = $template
    $block = [regex]::Replace($block, '<string name="ConfigKey">.*?</string>', { param($m) '<string name="ConfigKey">&quot;' + $s + '&quot;WPA_PSK</string>' })
    $block = [regex]::Replace($block, '<string name="SSID">.*?</string>', { param($m) '<string name="SSID">&quot;' + $s + '&quot;</string>' })
    $block = [regex]::Replace($block, '<null name="PreSharedKey"\s*/>|<string name="PreSharedKey">.*?</string>', { param($m) '<string name="PreSharedKey">&quot;' + (ConvertTo-XmlText $pass) + '&quot;</string>' })
    $block = [regex]::Replace($block, '<string name="BSSID">.*?</string>', '<null name="BSSID" />')
    $block = [regex]::Replace($block, '<byte-array name="AllowedKeyMgmt" num="\d+">[0-9a-fA-F]*</byte-array>', '<byte-array name="AllowedKeyMgmt" num="1">02</byte-array>')
    $block = [regex]::Replace($block, '<int name="Status" value="\d+"\s*/>', '<int name="Status" value="2" />')
    $block = [regex]::Replace($block, '<string name="SelectionStatus">.*?</string>', '<string name="SelectionStatus">NETWORK_SELECTION_ENABLED</string>')
    $block = [regex]::Replace($block, '<string name="DisableReason">.*?</string>', '<string name="DisableReason">NETWORK_SELECTION_ENABLE</string>')
    $block = [regex]::Replace($block, '<string name="IpAssignment">.*?</string>', '<string name="IpAssignment">DHCP</string>')
    if ($block -notmatch 'PreSharedKey">&quot;') { throw 'в образце нет поля PreSharedKey' }
    if ($replace) { $xml = $xml.Replace($replace, $block); $what = 'заменить' }
    elseif ($xml -match '</NetworkList>') { $xml = $xml.Replace('</NetworkList>', $block + '</NetworkList>'); $what = 'добавить' }
    else { throw 'в файле нет </NetworkList>' }
    if (-not (Confirm-Write "Сеть «$ssid»: $what в $WifiStore и перезапустить фреймворк (stop; start).")) { return }
    $null = Invoke-Hu "mkdir -p $Work; cp $WifiStore $Work/WifiConfigStore.xml.bak"
    Send-HuFile ([Text.Encoding]::UTF8.GetBytes($xml)) "$Work/wifi.xml"
    if ($DryRun) { Write-Host '-DryRun: файл собран, но не установлен' -ForegroundColor Cyan; return }
    # cat > keeps the owner, mode and SELinux context of the original file.
    $null = Invoke-Hu "cat $Work/wifi.xml > $WifiStore && stop && start" -TimeoutSec 60
    Write-Host 'Фреймворк перезапущен. Через 1–2 мин проверьте сеть (пункт 3 покажет адрес и включит ADB).' -ForegroundColor Green
}

function Move-Userdata {
    Initialize-HuConsole
    $g = Read-HuGpt
    $c = Show-GptReport $g
    if (-not $c.P -or -not $c.S -or -not $c.Same) { throw 'копии GPT повреждены или различаются — сначала пункт 6' }
    $ud = $c.P.Find('userdata')
    $ranges = Get-MoveRanges $g.Disk
    $first = 0; $last = 0
    if ($ranges -and $ud.First -eq $ranges.NewFirst) {
        $first = $ranges.OldFirst; $last = $ranges.OldLast
        $what = "Вернуть userdata на штатное место LBA $first…$last ($(Format-Gib ($last - $first + 1))) — откат переноса. /data вернётся к состоянию на момент переноса."
    } elseif ($ranges -and $ud.First -eq $ranges.OldFirst) {
        $first = $ranges.NewFirst; $last = $ranges.NewLast
        $what = "Вернуть userdata в хвост LBA $first…$last ($(Format-Gib ($last - $first + 1))). /data будет таким, каким был в хвосте перед откатом."
    } else {
        Write-Host 'Текущее место userdata не совпадает с известными; можно задать вручную.' -ForegroundColor Yellow
        $first = [long](Read-Host 'Первый LBA')
        $last = [long](Read-Host 'Последний LBA')
        $what = "Перенести userdata на LBA $first…$last."
    }
    if ($ranges) { Write-Host "Места взяты из: $($ranges.Source)" -ForegroundColor DarkGray }
    $new = [HuRescue.Gpt]::Relocate($g.P, $g.S, $g.Disk, 'userdata', $first, $last)
    [HuRescue.Gpt]::EntryDiff($c.P, [HuRescue.Gpt]::CheckPrimary($new[0], $g.Disk)) | ForEach-Object { Write-Host "  $_" }
    if (-not (Confirm-Write $what)) { return }
    if (Write-HuGpt $g $new[0] $new[1]) {
        if ($first -eq $ranges.OldFirst) { $null = Invoke-Hu '[ -d /cache/dataresize ] && echo rolledback > /cache/dataresize/state; true' -AllowFail }
        Restart-Hu
    }
}

function Repair-GptCopy {
    Initialize-HuConsole
    $g = Read-HuGpt
    $c = Show-GptReport $g
    if ($c.P -and $c.S -and $c.Same) { Write-Host 'Обе копии в порядке и совпадают.' -ForegroundColor Green; return }
    if (-not $c.P -and -not $c.S) { throw 'обе копии повреждены — только таблицы из дампа (пункт 7)' }
    $keep = 'P'
    if (-not $c.P) { $keep = 'S' }
    elseif ($c.S) {
        Write-Host 'Обе копии целы, но различаются. LK и ядро грузятся по основной.'
        $a = Read-Host 'Какую оставить: [1] основную (по умолчанию), [2] резервную'
        if ($a -eq '2') { $keep = 'S' }
    }
    if ($keep -eq 'P') {
        $newP = $g.P
        $newS = [HuRescue.Gpt]::SecondaryFromPrimary($g.P, $g.Disk, $g.SSect)
        $what = 'Переписать резервную GPT по основной.'
    } else {
        $newS = $g.S
        $newP = [HuRescue.Gpt]::PrimaryFromSecondary($g.S, $g.Disk, (Get-Slice $g.P 0 512))
        $what = 'Переписать основную GPT по резервной (защитный MBR сохраняется, если цел).'
    }
    if (-not (Confirm-Write $what)) { return }
    $null = Write-HuGpt $g $newP $newS
}

function Restore-GptFromDump {
    Initialize-HuConsole
    $headF = Join-Path $DumpDir 'gpt_head_1MiB.img'
    $tailF = Join-Path $DumpDir 'gpt_tail_1MiB.img'
    $alt = Read-Host "Папка с gpt_head_1MiB.img и gpt_tail_1MiB.img [$DumpDir]"
    if ($alt) { $headF = Join-Path $alt 'gpt_head_1MiB.img'; $tailF = Join-Path $alt 'gpt_tail_1MiB.img' }
    if (-not (Test-Path $headF) -or -not (Test-Path $tailF)) { throw 'нет файлов gpt_head_1MiB.img / gpt_tail_1MiB.img' }
    $g = Read-HuGpt
    $c = Show-GptReport $g
    $head = [IO.File]::ReadAllBytes($headF)
    $tail = [IO.File]::ReadAllBytes($tailF)
    [byte[]]$newP = Get-Slice $head 0 ($g.PSect * 512)
    [byte[]]$newS = Get-Slice $tail ($tail.Length - $g.SSect * 512) ($g.SSect * 512)
    $dc = [HuRescue.Gpt]::Validate($newP, $newS, $g.Disk)
    $cur = $c.P
    if (-not $cur) { $cur = $c.S }
    if ($cur) {
        Write-Host 'Изменения относительно текущей таблицы:'
        [HuRescue.Gpt]::EntryDiff($cur, $dc) | ForEach-Object { Write-Host "  $_" }
    }
    $what = 'Записать обе копии GPT из дампа. Вернутся разметка и служебные поля на момент дампа (userdata — где был тогда).'
    if (-not (Confirm-Write $what)) { return }
    if (Write-HuGpt $g $newP $newS) { Restart-Hu }
}

function Write-PartitionFromUsb {
    Initialize-HuConsole
    $name = (Read-Host 'Имя раздела (например recovery_a, boot-раздел kernel_b, misc)').Trim()
    if (-not $name -or $name -notmatch '^[A-Za-z0-9_]+$') { throw 'неверное имя раздела' }
    if ($name -in @('preloader', 'userdata')) { throw "«$name» так не пишется: preloader накрывает GPT, userdata — данные" }
    $defLocal = Join-Path $DumpDir "$name.img"
    $local = Read-Host "Тот же файл на ПК для сверки MD5 [$defLocal]"
    if (-not $local) { $local = $defLocal }
    if (-not (Test-Path $local)) { throw "нет файла $local" }
    $size = (Get-Item $local).Length
    if ($size % 512 -ne 0) { throw 'размер образа не кратен 512' }
    Write-Host 'MD5 файла на ПК...'
    $md5 = (Get-FileHash $local -Algorithm MD5).Hash.ToLower()
    $fname = Split-Path $local -Leaf
    # Partition dirs in sysfs and nodes in /dev/block are named after the partition (DEVNAME), not mmcblk0pN.
    $part = Invoke-Hu ('for u in /sys/block/mmcblk0/*/uevent; do grep -q "^PARTNAME=' + $name + '$" $u && echo "${u%/uevent} $(grep ^DEVNAME= $u | cut -d= -f2)"; done; true')
    $sys = @($part.Lines | Where-Object { $_ })
    if ($sys.Count -ne 1) { throw "раздел «$name» не найден (или найден не один)" }
    $sysDir, $devName = $sys[0].Split(' ')
    $sys = @($sysDir)
    $dev = '/dev/block/' + $devName
    $info = Get-Fields (Invoke-Hu ('echo "size=$(cat ' + $sys[0] + '/size)"; [ -b ' + $dev + ' ] && echo blk=1; for d in /udisk* /mnt/media_rw/* /storage/*; do [ -f "$d/' + $fname + '" ] && echo "file=$d/' + $fname + '"; done; true')).Lines
    if ($info['blk'] -ne '1') { throw "нет блочного устройства $dev" }
    $partBytes = [long]$info['size'] * 512
    if ($size -gt $partBytes) { throw "образ ($size Б) больше раздела ($partBytes Б)" }
    if (-not $info['file']) { throw "на флешке нет $fname (искал в /udisk*, /mnt/media_rw/*, /storage/*)" }
    $usb = $info['file']
    Write-Host "Файл на флешке: $usb; сверка MD5 на ГУ..."
    $t = [int]($size / 10MB) + 60
    $r = Invoke-Hu "md5sum '$usb'" -TimeoutSec $t
    if ($r.Text -notmatch $md5) { throw 'файл на флешке отличается от файла на ПК' }
    if ($name.EndsWith($script:Env['slot']) -and $script:Env['mode'] -eq 'android' -and $name -match '^(system|vendor|product)_') {
        Write-Host "Внимание: $name — раздел текущего слота, он смонтирован в работающем Android." -ForegroundColor Red
    }
    if (-not (Confirm-Write "Записать $fname ($([Math]::Round($size / 1MB, 1)) МиБ) в $name ($dev).")) { return }
    if ($DryRun) { Write-Host '-DryRun: запись пропущена' -ForegroundColor Cyan; return }
    [long]$bs = 1048576
    while ($size % $bs -ne 0) { $bs = [long]($bs / 2) }
    [long]$count = $size / $bs
    $null = Invoke-Hu 'echo emmc_disable_all_wp > /proc/mmc_debug'
    try {
        Write-Host 'Запись...'
        $null = Invoke-Hu "dd if='$usb' of=$dev bs=$bs count=$count 2>/dev/null; blockdev --flushbufs $dev; echo 3 > /proc/sys/vm/drop_caches" -TimeoutSec ($t * 3)
        $r = Invoke-Hu "dd if=$dev bs=$bs count=$count 2>/dev/null | md5sum" -TimeoutSec $t
    } finally {
        $null = Invoke-Hu 'echo emmc_enable_all_wp > /proc/mmc_debug' -AllowFail
    }
    if ($r.Text -match $md5) { Write-Host "$name записан и проверен чтением." -ForegroundColor Green }
    else { Write-Host "${name}: чтение обратно НЕ совпало с образом. Не перезагружайте, повторите запись." -ForegroundColor Red }
}

function Find-PcTool([string]$Name) {
    foreach ($dir in @($PSScriptRoot, (Join-Path $PSScriptRoot 'platform-tools'), 'D:\Tools\platform-tools')) {
        $p = Join-Path $dir "$Name.exe"
        if (Test-Path $p) { return $p }
    }
    $c = Get-Command $Name -ErrorAction SilentlyContinue
    if ($c) { return $c.Source }
    return ''
}

function Enable-UsbAdb {
    $a = Read-Host 'USB-порт ГУ (тот, что для CarPlay): [1] режим устройства — ADB по USB, [2] вернуть режим хоста, [Enter] отмена'
    if ($a -ne '1' -and $a -ne '2') { return }
    Initialize-HuConsole
    if ($script:Env['mode'] -ne 'android') { throw 'только в Android: recovery через 61 с сбрасывает микроконтроллер (§2.2)' }
    if ($a -eq '2') {
        $r = Invoke-Hu "echo host > $DualRole/data_role; sleep 1; cat $DualRole/mode" -AllowFail
        Write-Host "  режим порта: $($r.Text.Trim())"
        return
    }
    $r = Invoke-Hu ("echo device > $DualRole/data_role; sleep 1; [ -n `"`$(cat $Gadget/UDC)`" ] || echo $Udc > $Gadget/UDC; sleep 1; " +
        "echo `"mode=`$(cat $DualRole/mode)`"; echo `"state=`$(cat /sys/class/udc/$Udc/state)`"; echo `"usb=`$(getprop sys.usb.state)`"") -AllowFail
    $f = Get-Fields $r.Lines
    Write-Host ("  режим порта: {0}, контроллер: {1}, функции: {2}" -f $f['mode'], $f['state'], $f['usb'])
    if ($f['mode'] -ne 'ufp') { throw 'порт не перешёл в режим устройства' }
    $adb = Find-PcTool 'adb'
    if (-not $adb) { Write-Host 'adb.exe на ПК не найден: проверьте вручную «adb devices» (серийный номер 0x123456789EF).' -ForegroundColor Yellow; return }
    Write-Host 'Жду ГУ в «adb devices» (до 30 с; кабель ПК — USB-порт ГУ для CarPlay)...'
    $deadline = (Get-Date).AddSeconds(30)
    while ((Get-Date) -lt $deadline) {
        $l = @(& $adb devices 2>$null | Where-Object { $_ -match '^0x123456789EF\s+device' })
        if ($l) { Write-Host "ADB по USB: `"$adb`" -s 0x123456789EF shell (su работает)" -ForegroundColor Green; return }
        Start-Sleep 2
    }
    Write-Host 'ГУ не появилось. Если state=configured, проверьте драйвер в Диспетчере устройств; если not attached — кабель или порт.' -ForegroundColor Yellow
}

function Set-FastbootFlag {
    $null = Invoke-Hu '[ -b /dev/block/misc ]'
    if ($script:Env['wp'] -ne '1') { throw 'нет /proc/mmc_debug — защиту eMMC не снять' }
    if ($DryRun) { Write-Host '  -DryRun: запись boot-fastboot в misc пропущена' -ForegroundColor Cyan; return $false }
    $null = Invoke-Hu 'echo emmc_disable_all_wp > /proc/mmc_debug'
    try {
        # Only the 2048-byte boot message at offset 0; A/B slot metadata at 0x1000 stays untouched.
        $r = Invoke-Hu ("dd if=/dev/zero of=/dev/block/misc bs=2048 count=1 2>/dev/null && printf 'boot-fastboot' | dd of=/dev/block/misc bs=1 2>/dev/null; sync; " +
            'blockdev --flushbufs /dev/block/misc; echo 3 > /proc/sys/vm/drop_caches; echo "cmd=$(dd if=/dev/block/misc bs=13 count=1 2>/dev/null)"') -AllowFail
    } finally {
        $null = Invoke-Hu 'echo emmc_enable_all_wp > /proc/mmc_debug' -AllowFail
    }
    if ((Get-Fields $r.Lines)['cmd'] -ne 'boot-fastboot') { throw 'boot-fastboot в misc не записался' }
    return $true
}

function Restart-Hu {
    $a = Read-Host 'Перезагрузить ГУ: [1] обычно, [2] в fastboot (USB), [Enter] не сейчас'
    if ($a -ne '1' -and $a -ne '2') { return }
    Initialize-HuConsole
    if ($a -eq '2') {
        if (-not (Set-FastbootFlag)) { return }
        Write-Host 'В misc записано boot-fastboot. LK сотрёт его сам при входе, поэтому сброс питания вернёт обычную загрузку.' -ForegroundColor Green
    }
    Restore-Printk
    $script:PrintkSaved = ''
    Send-Uart "sync; reboot`r"
    $script:Fast = $false
    Watch-Boot
}

function Start-Terminal {
    Write-Host 'Терминал: ввод уходит в ГУ как есть. F9 — показать/скрыть лог ядра, F10 — выход.' -ForegroundColor Cyan
    $hide = $true
    $hold = ''
    $saved = [Console]::TreatControlCAsInput
    [Console]::TreatControlCAsInput = $true
    $script:Fast = $false
    try {
        Send-Uart "`r"
        while ($true) {
            while ([Console]::KeyAvailable) {
                $k = [Console]::ReadKey($true)
                if ($k.Key -eq 'F10') { return }
                if ($k.Key -eq 'F9') { $hide = -not $hide; Write-Host "`n[лог ядра: $(if ($hide) { 'скрыт' } else { 'показан' })]" -ForegroundColor DarkGray; continue }
                switch ($k.Key) {
                    'Enter' { $script:Sp.Write("`r") }
                    'UpArrow' { $script:Sp.Write("$([char]27)[A") }
                    'DownArrow' { $script:Sp.Write("$([char]27)[B") }
                    'RightArrow' { $script:Sp.Write("$([char]27)[C") }
                    'LeftArrow' { $script:Sp.Write("$([char]27)[D") }
                    'Backspace' { $script:Sp.Write([string][char]127) }
                    default { if ($k.KeyChar -ne [char]0) { $script:Sp.Write([string]$k.KeyChar) } }
                }
            }
            $s = Read-Uart
            if (-not $s) { Start-Sleep -Milliseconds 15; continue }
            if ($hide) {
                $hold = $KernelLine.Replace($hold + $s, '')
                $m = [regex]::Match($hold, '\[\s*\d+\.\d*[^\n]*$')
                if ($m.Success -and $m.Length -lt 400) { $out = $hold.Substring(0, $m.Index); $hold = $hold.Substring($m.Index) }
                else { $out = $hold; $hold = '' }
            } else { $out = $s }
            [Console]::Write([Text.Encoding]::UTF8.GetString($Latin1.GetBytes($out)))
        }
    } finally {
        [Console]::TreatControlCAsInput = $saved
        Write-Host ''
    }
}

function Get-PassiveState {
    $null = Read-Uart
    Start-Sleep -Milliseconds 1500
    $s = Read-Uart
    Send-Uart "`r"
    Start-Sleep -Milliseconds 800
    $s += Read-Uart
    if ($s -match 'console:/ [$#]') { return 'консоль отвечает' }
    if ($s -match 'welcome to lk|GPT_LK') { return 'идёт LK' }
    if ($s -match 'HSM Loader|PartitionTableParser') { return 'идёт preloader' }
    if ($s -match '\[\s*\d+\.\d{6}\]') { return 'идёт лог ядра, консоль не ответила' }
    if ($s) { return 'есть данные, этап не распознан' }
    return 'тишина (ГУ выключено или нет связи по RX/TX)'
}
#endregion

Open-Uart
try {
    Show-Header
    Write-Host "Состояние: $(Get-PassiveState)"
    while ($true) {
        Write-Host ''
        Write-Host ' 1  Следить за загрузкой (этап по логу UART)'
        Write-Host ' 2  Диагностика: система, /data, сеть, обе копии GPT'
        Write-Host ' 3  Включить ADB по Wi-Fi и показать адрес'
        Write-Host ' 4  Добавить сеть Wi-Fi (WifiConfigStore.xml)'
        Write-Host ' 5  GPT: перенести userdata (откат переноса или снова в хвост)'
        Write-Host ' 6  GPT: восстановить одну копию по другой'
        Write-Host ' 7  GPT: записать таблицы из дампа'
        Write-Host ' 8  Записать раздел из файла на USB-флешке'
        Write-Host ' 9  Перезагрузить ГУ (обычно / в fastboot по USB)'
        Write-Host ' U  ADB по USB (порт ГУ для CarPlay в режим устройства)'
        Write-Host ' T  Терминал (ручной ввод)'
        Write-Host ' Q  Выход'
        $choice = (Read-Host 'Пункт').Trim().ToUpper()
        try {
            switch ($choice) {
                '1' { Watch-Boot }
                '2' { Show-Diagnostics }
                '3' { Enable-Adb }
                '4' { Add-WifiNetwork }
                '5' { Move-Userdata }
                '6' { Repair-GptCopy }
                '7' { Restore-GptFromDump }
                '8' { Write-PartitionFromUsb }
                '9' { Restart-Hu }
                'U' { Enable-UsbAdb }
                'T' { Start-Terminal }
                'Q' { Restore-Printk; return }
                default { }
            }
        } catch {
            Write-Host "Ошибка: $(Get-ErrorText $_)" -ForegroundColor Red
        }
    }
} finally {
    Close-Uart
}
