#!/system/bin/sh
# Runs on the HU. Dumps every process: raw /proc/<pid>/stat and argv0. Parsed on the PC by hu_proc_cpu.ps1.
echo "UPTIME $(cut -d' ' -f1 /proc/uptime)"
echo "STAT $(head -1 /proc/stat)"
for p in /proc/[0-9]*; do
  s=$(cat $p/stat 2>/dev/null) || continue
  c=$(tr '\0' ' ' < $p/cmdline 2>/dev/null | cut -d' ' -f1)
  echo "P $s|$c"
done
