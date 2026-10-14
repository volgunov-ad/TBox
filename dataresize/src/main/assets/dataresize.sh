#!/system/bin/sh
# Move userdata to the free eMMC tail and grow it (A10 HU). Installed by the Data Resize app.
# Usage (root): sh dataresize.sh status|move|stage2|rollback
#   status   - one line of key=value, changes nothing
#   move     - stop framework, freeze /data, copy userdata to the tail, write new GPT
#              (secondary, then primary), reboot
#   stage2   - after reboot: grow ext4 on the new userdata online
#   rollback - write old GPT back and reboot
# move/rollback must run detached (setsid): the framework and the app stop on the way.
# Journal lines: "@step text" starts a step, "!FAIL text" ends the run with an error.

umask 077
DIR=/cache/dataresize
. $DIR/params || exit 3
D=/dev/block/mmcblk0
UD=/dev/block/userdata
PS=/sys/block/mmcblk0/userdata
P=/proc/mmc_debug

log() { echo "$(date '+%F %T') $*" >> $LOG; }
step() { log "@$*"; }
fail() { log "!FAIL $*"; exit 1; }
md5() { md5sum "$1" | cut -d' ' -f1; }
drop() { blockdev --flushbufs $D; blockdev --flushbufs $UD; echo 3 > /proc/sys/vm/drop_caches; }
rdmd5() { drop; dd if=$D bs=512 skip=$1 count=$2 2>/dev/null | md5sum | cut -d' ' -f1; }
wp_on() { echo emmc_enable_all_wp > $P; }

gpt_state() {
  CP=$(rdmd5 0 $PGPT_SECT); CS=$(rdmd5 $SGPT_LBA $SGPT_SECT)
  if [ "$CP" = "$MD5_OLD_P" ] && [ "$CS" = "$MD5_OLD_S" ]; then echo old
  elif [ "$CP" = "$MD5_NEW_P" ] && [ "$CS" = "$MD5_NEW_S" ]; then echo new
  else echo other; fi
}

write_copy() { # $1=file $2=lba $3=sectors $4=md5
  dd if=$1 of=$D bs=512 seek=$2 count=$3 2>/dev/null; blockdev --flushbufs $D
  [ "$(rdmd5 $2 $3)" = "$4" ]
}

preflight() { # $1=expected GPT state
  [ "$(id -u)" = 0 ] || fail "нет root"
  for t in dd md5sum blockdev resize2fs setsid; do command -v $t >/dev/null || fail "нет $t"; done
  [ "$(cat /sys/block/mmcblk0/size)" = "$DISK_SECT" ] || fail "другой размер eMMC"
  [ -w $P ] || fail "нет $P"
  for f in old_pgpt:$MD5_OLD_P new_pgpt:$MD5_NEW_P old_sgpt:$MD5_OLD_S new_sgpt:$MD5_NEW_S; do
    [ "$(md5 $DIR/${f%%:*}.bin)" = "${f##*:}" ] || fail "файл ${f%%:*}.bin повреждён"
  done
  G=$(gpt_state)
  [ "$G" = "$1" ] || fail "GPT сейчас: $G, нужно: $1"
}

status() {
  echo "GPT=$(gpt_state) START=$(cat $PS/start) SIZE=$(cat $PS/size) FS_KB=$(df -k /data | tail -1 | awk '{print $2}') FREE_KB=$(df -k /data | tail -1 | awk '{print $4}')"
}

move() {
  LOG=$DIR/move.log; : > $LOG
  step check "проверка"
  preflight old
  [ "$(cat $PS/start)" = "$OLD_FIRST" ] && [ "$(cat $PS/size)" = "$OLD_SECT" ] || fail "ядро видит не старый userdata"
  step stop "остановка Android"
  stop; sleep 8; sync
  step freeze "заморозка /data"
  if ! toybox fsfreeze -f /data; then start; fail "не удалось заморозить /data, Android запущен обратно"; fi
  trap 'toybox fsfreeze -u /data' EXIT
  step copy "копирование ${COPY_MIB} МиБ"
  drop
  dd if=$UD of=$D bs=1048576 seek=$COPY_SEEK_MIB count=$COPY_MIB 2>>$LOG; blockdev --flushbufs $D
  step verify "сверка копии"
  drop
  SRC=$(dd if=$UD bs=1048576 count=$COPY_MIB 2>/dev/null | md5sum | cut -d' ' -f1)
  DST=$(dd if=$D bs=1048576 skip=$COPY_SEEK_MIB count=$COPY_MIB 2>/dev/null | md5sum | cut -d' ' -f1)
  log "md5 $SRC / $DST"
  if [ "$SRC" != "$DST" ]; then toybox fsfreeze -u /data; trap - EXIT; start; fail "копия не совпала, Android запущен обратно"; fi
  step wp_off "снятие защиты eMMC"
  if ! echo emmc_disable_all_wp > $P; then toybox fsfreeze -u /data; trap - EXIT; start; fail "защита не снимается"; fi
  trap 'wp_on; toybox fsfreeze -u /data' EXIT
  step sgpt "запись резервной GPT"
  if write_copy $DIR/new_sgpt.bin $SGPT_LBA $SGPT_SECT $MD5_NEW_S; then
    step pgpt "запись основной GPT"
    write_copy $DIR/new_pgpt.bin 0 $PGPT_SECT $MD5_NEW_P && OK=1
  fi
  if [ -z "$OK" ]; then
    log "возврат старых GPT"
    write_copy $DIR/old_sgpt.bin $SGPT_LBA $SGPT_SECT $MD5_OLD_S && write_copy $DIR/old_pgpt.bin 0 $PGPT_SECT $MD5_OLD_P \
      || log "СТАРЫЕ GPT НЕ ВОССТАНОВЛЕНЫ — не перезагружать"
    wp_on; toybox fsfreeze -u /data; trap - EXIT; start; fail "запись GPT"
  fi
  step wp_on "возврат защиты eMMC"
  wp_on
  step unfreeze "разморозка /data"
  toybox fsfreeze -u /data; trap - EXIT
  echo moved > $DIR/state
  step reboot "перезагрузка"
  sync; reboot
}

stage2() {
  LOG=$DIR/stage2.log; : > $LOG
  step check "проверка"
  preflight new
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
  trap wp_on EXIT
  step gpt "запись старых GPT"
  write_copy $DIR/old_sgpt.bin $SGPT_LBA $SGPT_SECT $MD5_OLD_S && write_copy $DIR/old_pgpt.bin 0 $PGPT_SECT $MD5_OLD_P \
    || fail "запись GPT — не перезагружать"
  step wp_on "возврат защиты eMMC"
  wp_on; trap - EXIT
  echo rolledback > $DIR/state
  step reboot "перезагрузка"
  sync; reboot
}

case "$1" in
  status) status ;;
  move) move ;;
  stage2) stage2 ;;
  rollback) rollback ;;
  *) echo "usage: $0 status|move|stage2|rollback"; exit 2 ;;
esac
