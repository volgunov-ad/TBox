C=/data/local/tmp/CPV.apk
S=/system/app/CarPlayView/CarPlayView.apk
P=/proc/mmc_debug
D=/dev/block/mmcblk0
B=/data/local/tmp/CarPlayView_orig_backup.apk
wp_on() { echo emmc_enable_all_wp > "$P" 2>/dev/null; }
fail() { echo "CPVPATCH_ERR $1"; wp_on; exit 1; }
[ -f "$C" ] || fail nostage
[ -f "$B" ] || cp "$S" "$B" || fail backup
md5sum "$B"
echo emmc_disable_all_wp > "$P" 2>/dev/null || fail wpoff
trap 'wp_on' EXIT
mount -o remount,rw / || fail remountsys
cat "$C" > "$S" || fail copysys
chown root:root "$S" && chmod 644 "$S"
chcon u:object_r:system_file:s0 "$S" 2>/dev/null
sync
blockdev --flushbufs "$D" 2>/dev/null
echo 3 > /proc/sys/vm/drop_caches
H1=$(md5sum "$C" | cut -d' ' -f1)
H2=$(md5sum "$S" | cut -d' ' -f1)
[ "$H1" = "$H2" ] || fail verify
wp_on
trap - EXIT
md5sum "$S"
echo "CPVPATCH_OK"
