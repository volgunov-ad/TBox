P=/proc/mmc_debug
D=/dev/block/mmcblk0
WP=0
AA_MD5=7de94e8e0a80bd17af4ce097913fc9eb
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

[ "$(md5 /data/local/tmp/AA_p6.apk)" = "$AA_MD5" ] || { echo "BAD_STAGE_AA"; exit 1; }

trap 'wp_on' EXIT
trap 'wp_on; exit 1' HUP INT TERM PIPE
WP=1
echo emmc_disable_all_wp > "$P" || { echo "ERR_WPOFF"; exit 1; }
mount -o remount,rw / || { echo "ERR_REMOUNT"; exit 1; }
put /system/app/AndroidAuto/AndroidAuto.apk /data/local/tmp/AA_p6.apk "$AA_MD5" || { echo "ERR_PUT_AA"; exit 1; }
wp_on
trap - EXIT HUP INT TERM PIPE

rm -f /data/dalvik-cache/*/system@app@AndroidAuto@AndroidAuto.apk@classes.dex
rm -f /data/dalvik-cache/*/system@app@AndroidAuto@AndroidAuto.apk@classes.vdex
echo "OK $(md5 /system/app/AndroidAuto/AndroidAuto.apk)"
