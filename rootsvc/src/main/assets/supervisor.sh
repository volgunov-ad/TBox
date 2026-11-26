#!/system/bin/sh
# Root supervisor for vad.dashing.rootsvc. Restarted by the watchdog via mb_daemon.
DIR=/data/local/vad-rootsvc
DEX=$DIR/rootsvc.dex
LOG=$DIR/rootsvc.log
STATE=$DIR/state
PIDFILE=$DIR/supervisor.pid
HELPERS=$DIR/helpers
MAIN=vad.dashing.rootsvc.daemon.ServiceMain

mkdir -p "$DIR" "$HELPERS"
# 0711 so non-root clients (watchdog, Monitor, camd) can traverse and read state/.ver
chmod 711 "$DIR" "$HELPERS"
echo $$ > "$PIDFILE"
chmod 644 "$PIDFILE"

BACKOFFS="1 2 5 10 30 60"
FAILS=0
WINDOW_START=$(date +%s)

reset_fails() {
  FAILS=0
  WINDOW_START=$(date +%s)
}

write_state() {
  echo "$1" > "$STATE"
  chmod 644 "$STATE"
}

while true; do
  if [ ! -f "$DEX" ]; then
    write_state "missing dex"
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
    write_state "upgraded"
    continue
  fi
  if [ "$code" -eq 43 ]; then
    # admin.restart — deliberate, not a crash.
    reset_fails
    write_state "restarted"
    continue
  fi
  FAILS=$((FAILS + 1))
  if [ "$FAILS" -gt 6 ]; then
    write_state "crashloop"
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
