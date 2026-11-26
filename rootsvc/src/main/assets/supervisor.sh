#!/system/bin/sh
# Root supervisor for vad.dashing.rootsvc. Restarted by the watchdog via mb_daemon.
DIR=/data/local/vad-rootsvc
DEX=$DIR/rootsvc.dex
LOG=$DIR/rootsvc.log
STATE=$DIR/state
PIDFILE=$DIR/supervisor.pid
MAIN=vad.dashing.rootsvc.daemon.ServiceMain

mkdir -p "$DIR"
chmod 700 "$DIR"
echo $$ > "$PIDFILE"
chmod 644 "$PIDFILE"

BACKOFFS="1 2 5 10 30 60"
FAILS=0
WINDOW_START=$(date +%s)

reset_fails() {
  FAILS=0
  WINDOW_START=$(date +%s)
}

while true; do
  if [ ! -f "$DEX" ]; then
    echo "missing dex" > "$STATE"
    exit 1
  fi
  export CLASSPATH="$DEX"
  /system/bin/app_process /system/bin "$MAIN" >> "$LOG" 2>&1
  code=$?
  now=$(date +%s)
  age=$((now - WINDOW_START))
  if [ "$age" -gt 600 ]; then
    reset_fails
  fi
  if [ "$code" -eq 42 ]; then
    # Dex refreshed — restart immediately.
    reset_fails
    echo "upgraded" > "$STATE"
    continue
  fi
  FAILS=$((FAILS + 1))
  if [ "$FAILS" -gt 6 ]; then
    echo "crashloop" > "$STATE"
    exit 2
  fi
  # Pick backoff by fail count.
  i=0
  delay=1
  for d in $BACKOFFS; do
    i=$((i + 1))
    delay=$d
    [ "$i" -ge "$FAILS" ] && break
  done
  echo "restart code=$code fail=$FAILS sleep=$delay" >> "$LOG"
  sleep "$delay"
done
