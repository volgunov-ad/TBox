set -eu
C=/data/local/tmp/AA.apk
S=/system/app/AndroidAuto/AndroidAuto.apk
B=/data/local/tmp/AndroidAuto_orig_backup.apk
P=/proc/mmc_debug
D=/dev/block/mmcblk0
wp_on() { echo emmc_enable_all_wp > "$P" 2>/dev/null; }
[ -s "$C" ] || exit 1
[ -f "$B" ] || cp "$S" "$B"
md5sum "$B"
echo emmc_disable_all_wp > "$P"
trap 'wp_on' EXIT
mount -o remount,rw /
cat "$C" > "$S"
chown root:root "$S"
chmod 644 "$S"
chcon u:object_r:system_file:s0 "$S"
sync
blockdev --flushbufs "$D"
echo 3 > /proc/sys/vm/drop_caches
H1=$(md5sum "$C" | cut -d' ' -f1)
H2=$(md5sum "$S" | cut -d' ' -f1)
[ "$H1" = "$H2" ] || exit 1
wp_on
trap - EXIT
md5sum "$S"
echo AAPATCH_OK
