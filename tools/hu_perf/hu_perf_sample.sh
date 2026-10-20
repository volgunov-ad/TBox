#!/system/bin/sh
# Runs on the HU. Samples /proc/stat and the package process CPU every 5 s until uptime reaches END.
# usage: hu_perf_sample.sh <end_uptime_s> <package>
END=$1
PKG=$2
pid=$(pidof $PKG | cut -d' ' -f1)
echo "PID $pid START $(cut -d' ' -f22 /proc/$pid/stat)"
top -b -n 1 -m 12 | sed 's/^/TOP0 /'
while :; do
  up=$(cut -d' ' -f1 /proc/uptime)
  l=$(cut -d' ' -f14,15 /proc/$pid/stat 2>/dev/null)
  echo "S $up $(head -1 /proc/stat) L $l LA $(cut -d' ' -f1 /proc/loadavg)"
  [ ${up%.*} -ge $END ] && break
  sleep 5
done
top -b -n 1 -m 12 | sed 's/^/TOP1 /'
