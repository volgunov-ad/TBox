S=/system/etc/adayo/carplay/CarPlayManagerConfig.json
B=/data/local/tmp/CarPlayManagerConfig.json.orig
P=/proc/mmc_debug
wp_on() { echo emmc_enable_all_wp > "$P"; }
set -e
[ -s "$B" ]
grep -q '"heightPixels":1080' "$B"
echo emmc_disable_all_wp > "$P"
trap wp_on EXIT
mount -o remount,rw /
cat "$B" > "$S"
chown root:root "$S"
chmod 644 "$S"
chcon u:object_r:system_file:s0 "$S"
sync
blockdev --flushbufs /dev/block/mmcblk0
echo 3 > /proc/sys/vm/drop_caches
[ "$(md5sum "$S" | cut -d' ' -f1)" = "$(md5sum "$B" | cut -d' ' -f1)" ]
wp_on
trap - EXIT
echo CPMGR_RESTORED
