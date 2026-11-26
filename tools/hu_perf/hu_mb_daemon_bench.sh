#!/system/bin/sh
# On-device mb_daemon semantics bench for A9 (W0 / shared root service plan §13).
# Pushed and started by hu_mb_daemon_bench.ps1. Args: <out_dir> [suite]
# suite: all | arm | delete | poll | rearm | rename | race | (default all)
#
# Does NOT reboot. Accessibility / first-arm-after-boot are measured by the
# PowerShell driver around a reboot. Cleans its own /sdcard and /data/local/tmp
# paths; leaves foreign autorun writers alone after a bounded wait.

set -eu

OUT="${1:-/data/local/tmp/hu_mbd_bench}"
SUITE="${2:-all}"
PROP="vendor.autorun.sdcard.logfile"
AUTORUN="/sdcard/autorun.sh"
FLAG="/sdcard/autorun.flag"
WORKDIR="/sdcard/hu_mbd_bench"
MARKER_DIR="/data/local/tmp/hu_mbd_bench"
OWN_MARK="/data/local/tmp/hu_mbd_bench/arm"

ms_now() {
  awk '{printf "%d", $1 * 1000}' /proc/uptime
}

log() {
  echo "$1" | tee -a "$OUT/bench.log"
}

kv() {
  echo "$1=$2" | tee -a "$OUT/results.txt"
}

section() {
  echo "" | tee -a "$OUT/results.txt"
  echo "### $1" | tee -a "$OUT/results.txt" "$OUT/bench.log"
}

ensure_dirs() {
  mkdir -p "$OUT" "$WORKDIR" "$MARKER_DIR"
  : > "$OUT/results.txt"
  : > "$OUT/bench.log"
}

# Bounded wait for a foreign pending/running autorun.sh (same idea as camd/UsbMount).
wait_foreign() {
  deadline=$(( $(ms_now) + 10000 ))
  while [ "$(ms_now)" -lt "$deadline" ]; do
    if [ ! -f "$AUTORUN" ]; then
      return 0
    fi
    if grep -qF "$OWN_MARK" "$AUTORUN" 2>/dev/null; then
      return 0
    fi
    sleep 0.2
  done
  log "WARN foreign autorun still present after 10s, proceeding"
}

channel_clear() {
  rm -f "$AUTORUN" "$FLAG"
  setprop "$PROP" "" 2>/dev/null || true
  sleep 0.3
}

# Write a short root script that stamps markers and echoes the nonce.
# $1 = nonce, $2 = optional mid-sleep seconds (default 0), $3 = optional extra mid command
write_payload() {
  nonce="$1"
  mid_sleep="${2:-0}"
  mid_cmd="${3:-}"
  cat > "$AUTORUN" <<EOF
#!/system/bin/sh
MARK='$OWN_MARK'
NONCE='$nonce'
WORKDIR='$WORKDIR'
echo "script_start \$(awk '{printf \"%d\", \$1 * 1000}' /proc/uptime)" >> "\$WORKDIR/script_trace.txt"
if [ -f '$AUTORUN' ]; then
  echo "autorun_exists_at_script_start=1" >> "\$WORKDIR/script_trace.txt"
else
  echo "autorun_exists_at_script_start=0" >> "\$WORKDIR/script_trace.txt"
fi
id -u >> "\$WORKDIR/script_trace.txt" 2>&1 || true
echo "\$NONCE start" > "\$MARK.start"
EOF
  if [ "$mid_sleep" != "0" ]; then
    cat >> "$AUTORUN" <<EOF
sleep $mid_sleep
if [ -f '$AUTORUN' ]; then
  echo "autorun_exists_mid=1" >> "\$WORKDIR/script_trace.txt"
else
  echo "autorun_exists_mid=0" >> "\$WORKDIR/script_trace.txt"
fi
EOF
  fi
  if [ -n "$mid_cmd" ]; then
    echo "$mid_cmd" >> "$AUTORUN"
  fi
  cat >> "$AUTORUN" <<EOF
echo "\$NONCE" > "\$MARK"
echo "script_done \$(awk '{printf \"%d\", \$1 * 1000}' /proc/uptime)" >> "\$WORKDIR/script_trace.txt"
EOF
  chmod 777 "$AUTORUN" 2>/dev/null || true
}

# Arm like production clients: clear prop, 300 ms, set prop to flag; poll marker.
# Prints arm_ms and result to stdout as "arm_ms=<n> result=<ok|timeout>".
arm_and_wait() {
  nonce="$1"
  timeout_ms="${2:-8000}"
  poll_ms="${3:-50}"
  rm -f "$OWN_MARK" "$OWN_MARK.start"
  : > "$WORKDIR/script_trace.txt"
  touch "$FLAG"
  t0=$(ms_now)
  setprop "$PROP" ""
  sleep 0.3
  setprop "$PROP" "$FLAG"
  t_armed=$(ms_now)
  deadline=$(( t_armed + timeout_ms ))
  result="timeout"
  while [ "$(ms_now)" -lt "$deadline" ]; do
    if [ -f "$OWN_MARK" ] && grep -qF "$nonce" "$OWN_MARK" 2>/dev/null; then
      result="ok"
      break
    fi
    # Tiny sleep; 50 ms default for finer latency than production's 200 ms.
    sleep 0.05
  done
  t1=$(ms_now)
  arm_ms=$(( t1 - t0 ))
  from_setprop_ms=$(( t1 - t_armed ))
  echo "arm_ms=$arm_ms from_setprop_ms=$from_setprop_ms result=$result"
}

# Background watcher: when autorun.sh disappears relative to markers.
watch_autorun_gone() {
  watch_out="$1"
  : > "$watch_out"
  t0=$(ms_now)
  deadline=$(( t0 + 15000 ))
  saw=0
  while [ "$(ms_now)" -lt "$deadline" ]; do
    if [ -f "$AUTORUN" ]; then
      saw=1
    elif [ "$saw" -eq 1 ]; then
      echo "autorun_gone_ms=$(( $(ms_now) - t0 ))" >> "$watch_out"
      if [ -f "$OWN_MARK.start" ]; then
        echo "gone_after_script_start=1" >> "$watch_out"
      else
        echo "gone_after_script_start=0" >> "$watch_out"
      fi
      if [ -f "$OWN_MARK" ]; then
        echo "gone_after_marker=1" >> "$watch_out"
      else
        echo "gone_after_marker=0" >> "$watch_out"
      fi
      return 0
    fi
    sleep 0.02
  done
  echo "autorun_gone_ms=-1" >> "$watch_out"
}

run_arm_latency() {
  section "arm_latency"
  wait_foreign
  channel_clear
  i=1
  while [ "$i" -le 5 ]; do
    nonce="arm$i$(ms_now)"
    write_payload "$nonce" 0
    line=$(arm_and_wait "$nonce" 8000 50)
    kv "arm_$i" "$line"
    # Prop/flag state after success
    prop_now=$(getprop "$PROP" 2>/dev/null || true)
    kv "arm_${i}_prop_after" "$prop_now"
    kv "arm_${i}_autorun_exists_after" "$([ -f "$AUTORUN" ] && echo 1 || echo 0)"
    channel_clear
    i=$(( i + 1 ))
  done
}

run_delete_timing() {
  section "delete_timing"
  wait_foreign
  channel_clear
  nonce="del$(ms_now)"
  write_payload "$nonce" 2
  watch_autorun_gone "$OUT/delete_watch.txt" &
  watch_pid=$!
  line=$(arm_and_wait "$nonce" 10000 50)
  wait "$watch_pid" 2>/dev/null || true
  kv "delete_arm" "$line"
  if [ -f "$OUT/delete_watch.txt" ]; then
    while IFS= read -r wline; do
      [ -n "$wline" ] && echo "$wline" | tee -a "$OUT/results.txt"
    done < "$OUT/delete_watch.txt"
  fi
  if [ -f "$WORKDIR/script_trace.txt" ]; then
    cp "$WORKDIR/script_trace.txt" "$OUT/delete_script_trace.txt"
    grep -E 'autorun_exists_' "$WORKDIR/script_trace.txt" | while IFS= read -r tline; do
      [ -n "$tline" ] && echo "$tline" | tee -a "$OUT/results.txt"
    done
  fi
  channel_clear
}

# Does mb_daemon pick up a script written AFTER the property is already armed?
run_poll_while_armed() {
  section "poll_while_armed"
  wait_foreign
  channel_clear
  nonce="poll$(ms_now)"
  rm -f "$OWN_MARK" "$OWN_MARK.start"
  : > "$WORKDIR/script_trace.txt"
  touch "$FLAG"
  setprop "$PROP" ""
  sleep 0.3
  setprop "$PROP" "$FLAG"
  # No autorun.sh yet — wait, then drop the script.
  sleep 1.0
  t_write=$(ms_now)
  write_payload "$nonce" 0
  # Do NOT touch the property again.
  deadline=$(( t_write + 8000 ))
  result="timeout"
  while [ "$(ms_now)" -lt "$deadline" ]; do
    if [ -f "$OWN_MARK" ] && grep -qF "$nonce" "$OWN_MARK" 2>/dev/null; then
      result="ok"
      break
    fi
    sleep 0.05
  done
  t1=$(ms_now)
  kv "poll_armed_result" "$result"
  kv "poll_armed_from_write_ms" "$(( t1 - t_write ))"
  kv "poll_armed_autorun_exists_after" "$([ -f "$AUTORUN" ] && echo 1 || echo 0)"
  channel_clear
}

# Re-arm while property still points at the flag (skip the clear+300ms ritual).
run_rearm_same_prop() {
  section "rearm_same_prop"
  wait_foreign
  channel_clear
  nonce1="re1$(ms_now)"
  write_payload "$nonce1" 0
  line1=$(arm_and_wait "$nonce1" 8000 50)
  kv "rearm_first" "$line1"
  prop_mid=$(getprop "$PROP" 2>/dev/null || true)
  kv "rearm_prop_between" "$prop_mid"

  nonce2="re2$(ms_now)"
  rm -f "$OWN_MARK" "$OWN_MARK.start"
  : > "$WORKDIR/script_trace.txt"
  write_payload "$nonce2" 0
  touch "$FLAG"
  t0=$(ms_now)
  # Intentionally no setprop clear / no setprop re-arm.
  deadline=$(( t0 + 5000 ))
  result="timeout"
  while [ "$(ms_now)" -lt "$deadline" ]; do
    if [ -f "$OWN_MARK" ] && grep -qF "$nonce2" "$OWN_MARK" 2>/dev/null; then
      result="ok"
      break
    fi
    sleep 0.05
  done
  kv "rearm_without_setprop" "arm_ms=$(( $(ms_now) - t0 )) result=$result"

  # Now clear+setprop again without rewriting script first — then rewrite.
  # Second variant: clear prop, set again, write after (covered by poll).
  # Third: full re-arm immediately after first success (production path).
  nonce3="re3$(ms_now)"
  write_payload "$nonce3" 0
  line3=$(arm_and_wait "$nonce3" 8000 50)
  kv "rearm_full_second" "$line3"
  channel_clear
}

# Atomic rename of autorun.sh on /sdcard (sdcardfs).
run_rename_atomic() {
  section "rename_atomic"
  wait_foreign
  channel_clear
  tmp="$WORKDIR/autorun.tmp"
  body="$WORKDIR/body_rename.sh"
  nonce="ren$(ms_now)"
  cat > "$body" <<EOF
#!/system/bin/sh
echo $nonce > $OWN_MARK
EOF
  chmod 777 "$body"
  # One-line exec wrapper (as dcl_install does).
  printf '%s\n' "#!/system/bin/sh" "exec /system/bin/sh '$body'" > "$tmp"
  chmod 777 "$tmp"

  torn_log="$OUT/rename_torn.txt"
  expected_log="$OUT/rename_expected.txt"
  printf '%s\n' "#!/system/bin/sh" "exec /system/bin/sh '$body'" > "$expected_log"
  : > "$torn_log"
  # Reader: tight loop looking for a torn / partial autorun while rename happens.
  (
    i=0
    while [ "$i" -lt 2000 ]; do
      if [ -f "$AUTORUN" ]; then
        # Compare to expected full content; anything else is a candidate tear.
        if ! cmp -s "$AUTORUN" "$expected_log" 2>/dev/null; then
          # Empty / missing mid-replace is OK; non-empty mismatch is interesting.
          if [ -s "$AUTORUN" ]; then
            echo "torn_at_ms=$(ms_now)" >> "$torn_log"
            cat "$AUTORUN" >> "$torn_log" 2>/dev/null || true
            echo "---" >> "$torn_log"
          fi
        fi
      fi
      i=$(( i + 1 ))
    done
  ) &
  reader_pid=$!
  sleep 0.05
  # Atomic within one directory on sane filesystems.
  mv -f "$tmp" "$AUTORUN"
  wait "$reader_pid" 2>/dev/null || true
  torn_n=0
  if [ -f "$torn_log" ]; then
    torn_n=$(grep -c '^torn_at_ms=' "$torn_log" 2>/dev/null || true)
    [ -n "$torn_n" ] || torn_n=0
  fi
  kv "rename_torn_samples" "$torn_n"
  # Does renamed script run after a normal arm (flag+setprop)?
  rm -f "$OWN_MARK"
  touch "$FLAG"
  t0=$(ms_now)
  setprop "$PROP" ""
  sleep 0.3
  setprop "$PROP" "$FLAG"
  deadline=$(( t0 + 8000 ))
  result="timeout"
  while [ "$(ms_now)" -lt "$deadline" ]; do
    if [ -f "$OWN_MARK" ] && grep -qF "$nonce" "$OWN_MARK" 2>/dev/null; then
      result="ok"
      break
    fi
    sleep 0.05
  done
  kv "rename_then_arm" "arm_ms=$(( $(ms_now) - t0 )) result=$result"
  channel_clear

  # Second rename probe: overwrite while a long script is running (corruption risk).
  section "rename_during_run"
  wait_foreign
  channel_clear
  nonce_a="rda$(ms_now)"
  write_payload "$nonce_a" 3
  touch "$FLAG"
  setprop "$PROP" ""
  sleep 0.3
  setprop "$PROP" "$FLAG"
  # Wait until script has started.
  deadline=$(( $(ms_now) + 5000 ))
  while [ "$(ms_now)" -lt "$deadline" ]; do
    [ -f "$OWN_MARK.start" ] && break
    sleep 0.05
  done
  # Mid-run: rename a second payload over autorun.sh (simulates foreign overwrite).
  nonce_b="rdb$(ms_now)"
  body_b="$WORKDIR/body_b.sh"
  cat > "$body_b" <<EOF
#!/system/bin/sh
echo $nonce_b > $OWN_MARK
EOF
  chmod 777 "$body_b"
  printf '%s\n' "#!/system/bin/sh" "exec /system/bin/sh '$body_b'" > "$WORKDIR/autorun_b.tmp"
  mv -f "$WORKDIR/autorun_b.tmp" "$AUTORUN"
  # Wait for either nonce.
  deadline=$(( $(ms_now) + 8000 ))
  got_a=0
  got_b=0
  while [ "$(ms_now)" -lt "$deadline" ]; do
    if [ -f "$OWN_MARK" ]; then
      grep -qF "$nonce_a" "$OWN_MARK" 2>/dev/null && got_a=1
      grep -qF "$nonce_b" "$OWN_MARK" 2>/dev/null && got_b=1
    fi
    [ "$got_a$got_b" != "00" ] && break
    sleep 0.05
  done
  kv "overwrite_midrun_got_a" "$got_a"
  kv "overwrite_midrun_got_b" "$got_b"
  # Note: B would need its own setprop to run as a new job; presence of B in
  # marker means the long sleep script was replaced mid-flight or after.
  channel_clear
}

# Two near-simultaneous writers (TOCTOU / race).
run_race() {
  section "race_two_writers"
  wait_foreign
  channel_clear
  nonce_x="rx$(ms_now)"
  nonce_y="ry$(ms_now)"
  rm -f "$OWN_MARK" "$MARKER_DIR/arm_x" "$MARKER_DIR/arm_y"
  : > "$WORKDIR/script_trace.txt"

  # Writer X
  (
    cat > "$AUTORUN" <<EOF
#!/system/bin/sh
echo $nonce_x > $MARKER_DIR/arm_x
echo $nonce_x > $OWN_MARK
EOF
    chmod 777 "$AUTORUN" 2>/dev/null || true
    touch "$FLAG"
    setprop "$PROP" ""
    sleep 0.3
    setprop "$PROP" "$FLAG"
  ) &
  pid_x=$!
  # Writer Y slightly overlapping
  sleep 0.05
  (
    cat > "$AUTORUN" <<EOF
#!/system/bin/sh
echo $nonce_y > $MARKER_DIR/arm_y
echo $nonce_y > $OWN_MARK
EOF
    chmod 777 "$AUTORUN" 2>/dev/null || true
    touch "$FLAG"
    setprop "$PROP" ""
    sleep 0.3
    setprop "$PROP" "$FLAG"
  ) &
  pid_y=$!
  wait "$pid_x" 2>/dev/null || true
  wait "$pid_y" 2>/dev/null || true
  sleep 2
  got_x=0
  got_y=0
  [ -f "$MARKER_DIR/arm_x" ] && grep -qF "$nonce_x" "$MARKER_DIR/arm_x" 2>/dev/null && got_x=1
  [ -f "$MARKER_DIR/arm_y" ] && grep -qF "$nonce_y" "$MARKER_DIR/arm_y" 2>/dev/null && got_y=1
  mark=""
  [ -f "$OWN_MARK" ] && mark=$(cat "$OWN_MARK" 2>/dev/null || true)
  kv "race_got_x" "$got_x"
  kv "race_got_y" "$got_y"
  kv "race_final_marker" "$mark"
  kv "race_both_ran" "$([ "$got_x" -eq 1 ] && [ "$got_y" -eq 1 ] && echo 1 || echo 0)"
  channel_clear
}

cleanup_bench_files() {
  channel_clear
  rm -f "$OWN_MARK" "$OWN_MARK.start" "$MARKER_DIR/arm_x" "$MARKER_DIR/arm_y"
  # Keep OUT for pull; remove work scripts on sdcard.
  rm -rf "$WORKDIR"
}

meta() {
  section "meta"
  kv "uptime_ms" "$(ms_now)"
  kv "boot_completed" "$(getprop sys.boot_completed 2>/dev/null || true)"
  kv "release" "$(getprop ro.build.version.release 2>/dev/null || true)"
  kv "incremental" "$(getprop ro.build.version.incremental 2>/dev/null || true)"
  kv "solution" "$(getprop ro.wt.solution.provider 2>/dev/null || true)"
  kv "selinux" "$(getenforce 2>/dev/null || true)"
}

ensure_dirs
meta
case "$SUITE" in
  all)
    run_arm_latency
    run_delete_timing
    run_poll_while_armed
    run_rearm_same_prop
    run_rename_atomic
    run_race
    ;;
  arm) run_arm_latency ;;
  delete) run_delete_timing ;;
  poll) run_poll_while_armed ;;
  rearm) run_rearm_same_prop ;;
  rename) run_rename_atomic ;;
  race) run_race ;;
  *)
    log "unknown suite: $SUITE"
    exit 2
    ;;
esac
cleanup_bench_files
section "done"
kv "finished_uptime_ms" "$(ms_now)"
log "DONE suite=$SUITE out=$OUT"
