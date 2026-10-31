P=/proc/mmc_debug
D=/dev/block/mmcblk0
W=/data/local/tmp/winpatch
WP=0
wp_on() {
  [ "$WP" = 1 ] || return 0
  WP=0
  sync
  mount -o remount,ro / 2>/dev/null
  echo emmc_enable_all_wp > "$P" 2>/dev/null
}
md5() { md5sum "$1" 2>/dev/null | cut -d' ' -f1; }
put() {
  N="$1.new"
  rm -f "$N"
  cat "$2" > "$N" || { rm -f "$N"; return 1; }
  chown root:root "$N" && chmod 644 "$N" || { rm -f "$N"; return 1; }
  chcon u:object_r:system_file:s0 "$N" 2>/dev/null
  sync
  blockdev --flushbufs "$D" 2>/dev/null
  echo 3 > /proc/sys/vm/drop_caches
  [ "$(md5 "$N")" = "$3" ] || { rm -f "$N"; return 1; }
  mv -f "$N" "$1" || { rm -f "$N"; return 1; }
  sync
  [ "$(md5 "$1")" = "$3" ] || return 1
}

trap 'wp_on' EXIT
trap 'wp_on; exit 1' HUP INT TERM PIPE
WP=1
echo emmc_disable_all_wp > "$P" || exit 1
mount -o remount,rw / || exit 1
put /system/app/CarPlayView/CarPlayView.apk /data/local/tmp/CPV_p6.apk 0841e6252eb54c88c98ef561dafd2857 || exit 1
wp_on
trap - EXIT HUP INT TERM PIPE
md5sum /system/app/CarPlayView/CarPlayView.apk /system/app/AndroidAuto/AndroidAuto.apk
