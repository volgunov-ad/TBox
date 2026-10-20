#!/system/bin/sh
# Runs on the HU. N cycles of "am start -W <activity>" -> 3 s -> BACK -> 3 s,
# with /proc/stat + process CPU before/after and gfxinfo of the package.
# usage: hu_perf_switch.sh <cycles> <package> <activity_class>
N=$1
PKG=$2
ACT=$3
pid=$(pidof $PKG | cut -d' ' -f1)
dumpsys gfxinfo $PKG reset > /dev/null
echo "S0 $(cut -d' ' -f1 /proc/uptime) $(head -1 /proc/stat) L $(cut -d' ' -f14,15 /proc/$pid/stat)"
i=0
while [ $i -lt $N ]; do
  t=$(am start -W -n $PKG/$ACT | grep -E 'TotalTime|WaitTime' | tr '\n' ' ')
  echo "OPEN $i $t"
  sleep 3
  input keyevent 4
  sleep 3
  i=$((i+1))
done
echo "S1 $(cut -d' ' -f1 /proc/uptime) $(head -1 /proc/stat) L $(cut -d' ' -f14,15 /proc/$pid/stat)"
echo "PIDEND $(pidof $PKG)"
dumpsys gfxinfo $PKG | grep -E 'Total frames|Janky|percentile|Number ' | sed 's/^/GFX /'
