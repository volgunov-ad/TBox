#!/system/bin/sh
# Move userdata to the free eMMC tail and grow it (A10 HU). Installed by the Data Resize app.
# Usage (root): sh dataresize.sh status|running|move|stage2|rollback|fixsgpt
#   status   - one line of key=value, changes nothing
#   running  - "RUNNING=1 MODE=<mode>" while a run below holds the lock, else "RUNNING=0"
#   move     - stop framework, freeze /data, copy userdata to the tail, write new GPT
#              (secondary, then primary), reboot
#   stage2   - after reboot: grow ext4 on the new userdata online
#   rollback - write old GPT back and reboot
#   fixsgpt  - the copies are from different tables (power loss between the two writes):
#              write the secondary that matches the primary, no reboot
# move/rollback must run detached (setsid): the framework and the app stop on the way.
# Journal lines: "@step text" starts a step, "!FAIL text" ends the run with an error.

umask 077
DIR=/cache/dataresize
. $DIR/params || exit 3
D=/dev/block/mmcblk0
UD=/dev/block/userdata
PS=/sys/block/mmcblk0/userdata
P=/proc/mmc_debug
LOCK=$DIR/lock

log() { echo "$(date '+%F %T') $*" >> $LOG; }
step() { log "@$*"; }
fail() { log "!FAIL $*"; exit 1; }
md5() { md5sum "$1" | cut -d' ' -f1; }
drop() { blockdev --flushbufs $D; blockdev --flushbufs $UD; echo 3 > /proc/sys/vm/drop_caches; }
rdmd5() { drop; dd if=$D bs=512 skip=$1 count=$2 2>/dev/null | md5sum | cut -d' ' -f1; }
wp_on() { echo emmc_enable_all_wp > $P; }

# The lock outlives a reboot in /cache; it counts only while its pid is still this script.
holder() {
  PID=$(cat $LOCK/pid 2>/dev/null)
  [ -n "$PID" ] && [ "$PID" != $$ ] && grep -q dataresize.sh /proc/$PID/cmdline 2>/dev/null && cat $LOCK/mode 2>/dev/null
}
unlock() { rm -rf $LOCK; }
lock() { # before the journal is truncated: a second run must not touch the first one's log
  if ! mkdir $LOCK 2>/dev/null; then
    M=$(holder) && { echo "уже выполняется: $M"; exit 4; }
    rm -rf $LOCK; mkdir $LOCK 2>/dev/null || { echo "lock занят"; exit 4; }
  fi
  echo $$ > $LOCK/pid; echo $1 > $LOCK/mode
  trap unlock EXIT
}

# mixed_old / mixed_new: copies of different tables, named after the primary (the one LK and the kernel use).
gpt_state() {
  CP=$(rdmd5 0 $PGPT_SECT); CS=$(rdmd5 $SGPT_LBA $SGPT_SECT)
  case "$CP:$CS" in
    "$MD5_OLD_P:$MD5_OLD_S") echo old ;;
    "$MD5_NEW_P:$MD5_NEW_S") echo new ;;
    "$MD5_OLD_P:$MD5_NEW_S") echo mixed_old ;;
    "$MD5_NEW_P:$MD5_OLD_S") echo mixed_new ;;
    *) echo other ;;
  esac
}

write_copy() { # $1=file $2=lba $3=sectors $4=md5
  dd if=$1 of=$D bs=512 seek=$2 count=$3 2>/dev/null; blockdev --flushbufs $D
  [ "$(rdmd5 $2 $3)" = "$4" ]
}

preflight() { # $@=allowed GPT states; sets G
  [ "$(id -u)" = 0 ] || fail "нет root"
  for t in dd md5sum blockdev resize2fs setsid; do command -v $t >/dev/null || fail "нет $t"; done
  [ "$(cat /sys/block/mmcblk0/size)" = "$DISK_SECT" ] || fail "другой размер eMMC"
  [ -w $P ] || fail "нет $P"
  for f in old_pgpt:$MD5_OLD_P new_pgpt:$MD5_NEW_P old_sgpt:$MD5_OLD_S new_sgpt:$MD5_NEW_S; do
    [ "$(md5 $DIR/${f%%:*}.bin)" = "${f##*:}" ] || fail "файл ${f%%:*}.bin повреждён"
  done
  G=$(gpt_state)
  case " $* " in *" $G "*) ;; *) fail "GPT сейчас: $G, нужно: $*" ;; esac
}

# resize2fs below grows ext4 to the whole partition; a crypto footer or length= in fstab needs the tail kept.
fstab_check() {
  F=$(awk '$2 == "/data" {print $5}' /vendor/etc/fstab.* 2>/dev/null)
  case "$F" in *footer*|*length=*) fail "в fstab для /data задан резерв в конце раздела: $F" ;; esac
}

status() {
  if holder >/dev/null; then R=1; else R=0; fi
  echo "GPT=$(gpt_state) START=$(cat $PS/start) SIZE=$(cat $PS/size) FS_KB=$(df -k /data | tail -1 | awk '{print $2}') FREE_KB=$(df -k /data | tail -1 | awk '{print $4}') RUNNING=$R"
}

move() {
  LOG=$DIR/move.log; : > $LOG
  step check "проверка"
  preflight old
  [ "$(cat $PS/start)" = "$OLD_FIRST" ] && [ "$(cat $PS/size)" = "$OLD_SECT" ] || fail "ядро видит не старый userdata"
  # dd copies forward: an overlapping target overwrites unread source and the MD5 check still passes.
  [ "$NEW_FIRST" -gt $((OLD_FIRST + OLD_SECT - 1)) ] || fail "новое место пересекается со старым userdata"
  [ $((COPY_SEEK_MIB * 2048)) = "$NEW_FIRST" ] && [ $((COPY_MIB * 2048)) = "$OLD_SECT" ] || fail "параметры копирования не сходятся"
  fstab_check
  step stop "остановка Android"
  stop; sleep 8; sync
  step freeze "заморозка /data"
  if ! toybox fsfreeze -f /data; then start; fail "не удалось заморозить /data, Android запущен обратно"; fi
  trap 'toybox fsfreeze -u /data; unlock' EXIT
  step copy "копирование ${COPY_MIB} МиБ"
  drop
  dd if=$UD of=$D bs=1048576 seek=$COPY_SEEK_MIB count=$COPY_MIB 2>>$LOG; blockdev --flushbufs $D
  step verify "сверка копии"
  drop
  SRC=$(dd if=$UD bs=1048576 count=$COPY_MIB 2>/dev/null | md5sum | cut -d' ' -f1)
  DST=$(dd if=$D bs=1048576 skip=$COPY_SEEK_MIB count=$COPY_MIB 2>/dev/null | md5sum | cut -d' ' -f1)
  log "md5 $SRC / $DST"
  if [ "$SRC" != "$DST" ]; then toybox fsfreeze -u /data; trap unlock EXIT; start; fail "копия не совпала, Android запущен обратно"; fi
  step wp_off "снятие защиты eMMC"
  if ! echo emmc_disable_all_wp > $P; then toybox fsfreeze -u /data; trap unlock EXIT; start; fail "защита не снимается"; fi
  trap 'wp_on; toybox fsfreeze -u /data; unlock' EXIT
  step sgpt "запись резервной GPT"
  if write_copy $DIR/new_sgpt.bin $SGPT_LBA $SGPT_SECT $MD5_NEW_S; then
    step pgpt "запись основной GPT"
    write_copy $DIR/new_pgpt.bin 0 $PGPT_SECT $MD5_NEW_P && OK=1
  fi
  if [ -z "$OK" ]; then
    log "возврат старых GPT"
    write_copy $DIR/old_sgpt.bin $SGPT_LBA $SGPT_SECT $MD5_OLD_S && write_copy $DIR/old_pgpt.bin 0 $PGPT_SECT $MD5_OLD_P \
      || log "СТАРЫЕ GPT НЕ ВОССТАНОВЛЕНЫ — не перезагружать"
    wp_on; toybox fsfreeze -u /data; trap unlock EXIT; start; fail "запись GPT"
  fi
  step wp_on "возврат защиты eMMC"
  wp_on
  # Unfreeze before reboot: on shutdown init writes persist.* props to /data and can hang on a frozen fs.
  # Native writes after this point go to the old location and are lost; the framework is already stopped.
  step unfreeze "разморозка /data"
  toybox fsfreeze -u /data; trap - EXIT
  echo moved > $DIR/state
  step reboot "перезагрузка"
  sync; reboot; sleep 120
  unlock; fail "ГУ не перезагрузилось — перезагрузите вручную"
}

stage2() {
  LOG=$DIR/stage2.log; : > $LOG
  step check "проверка"
  preflight new
  fstab_check
  step kernel "новый раздел в ядре"
  [ "$(cat $PS/start)" = "$NEW_FIRST" ] && [ "$(cat $PS/size)" = "$NEW_SECT" ] || fail "ядро видит старый userdata, нужна перезагрузка"
  step resize "расширение файловой системы"
  resize2fs $UD >>$LOG 2>&1 || fail "resize2fs"
  echo done > $DIR/state
  step done "$(df -h /data | tail -1)"
}

rollback() {
  LOG=$DIR/rollback.log; : > $LOG
  step check "проверка"
  preflight new
  step wp_off "снятие защиты eMMC"
  echo emmc_disable_all_wp > $P || fail "защита не снимается"
  trap 'wp_on; unlock' EXIT
  step gpt "запись старых GPT"
  if ! { write_copy $DIR/old_sgpt.bin $SGPT_LBA $SGPT_SECT $MD5_OLD_S && write_copy $DIR/old_pgpt.bin 0 $PGPT_SECT $MD5_OLD_P; }; then
    log "возврат новых GPT"
    if write_copy $DIR/new_sgpt.bin $SGPT_LBA $SGPT_SECT $MD5_NEW_S && write_copy $DIR/new_pgpt.bin 0 $PGPT_SECT $MD5_NEW_P; then
      fail "запись старых GPT, новые GPT возвращены"
    fi
    fail "запись GPT, копии могут различаться — не перезагружать"
  fi
  step wp_on "возврат защиты eMMC"
  wp_on; trap - EXIT
  echo rolledback > $DIR/state
  step reboot "перезагрузка"
  sync; reboot; sleep 120
  unlock; fail "ГУ не перезагрузилось — перезагрузите вручную"
}

fixsgpt() {
  LOG=$DIR/fixsgpt.log; : > $LOG
  step check "проверка"
  preflight mixed_old mixed_new
  if [ "$G" = mixed_old ]; then F=old; O=new; M=$MD5_OLD_S; OM=$MD5_NEW_S; T=старой
  else F=new; O=old; M=$MD5_NEW_S; OM=$MD5_OLD_S; T=новой; fi
  step wp_off "снятие защиты eMMC"
  echo emmc_disable_all_wp > $P || fail "защита не снимается"
  trap 'wp_on; unlock' EXIT
  step sgpt "запись резервной GPT по $T таблице"
  if ! write_copy $DIR/${F}_sgpt.bin $SGPT_LBA $SGPT_SECT $M; then
    write_copy $DIR/${O}_sgpt.bin $SGPT_LBA $SGPT_SECT $OM || log "прежняя резервная GPT не восстановлена"
    fail "запись резервной GPT"
  fi
  step wp_on "возврат защиты eMMC"
  wp_on; trap unlock EXIT
  step done "обе копии GPT: $(gpt_state)"
}

case "$1" in
  status) status ;;
  running) if M=$(holder); then echo "RUNNING=1 MODE=$M"; else echo "RUNNING=0"; fi ;;
  move|stage2|rollback|fixsgpt) lock $1; $1 ;;
  *) echo "usage: $0 status|running|move|stage2|rollback|fixsgpt"; exit 2 ;;
esac
