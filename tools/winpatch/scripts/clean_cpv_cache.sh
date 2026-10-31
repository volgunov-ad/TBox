am force-stop com.adayo.carplay.view
sleep 1
rm -f /data/dalvik-cache/arm64/system@app@CarPlayView@CarPlayView.apk@classes.dex
rm -f /data/dalvik-cache/arm64/system@app@CarPlayView@CarPlayView.apk@classes.vdex
echo CLEANED
ls /data/dalvik-cache/arm64/ | grep -i carplayview || echo NOCACHE
pidof com.adayo.carplay.view || echo NOPROC
