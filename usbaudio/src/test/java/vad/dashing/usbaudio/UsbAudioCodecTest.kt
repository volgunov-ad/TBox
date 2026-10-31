package vad.dashing.usbaudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbAudioCodecTest {
    private val STOCK_MAIN = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <audioPolicyConfiguration version="1.0" xmlns:xi="http://www.w3.org/2001/XInclude">
            <modules>
                <module name="primary" halVersion="2.0">
                    <mixPort name="primary output" role="source"></mixPort>
                </module>
                <xi:include href="a2dp_audio_policy_configuration.xml"/>
                <xi:include href="usb_audio_policy_configuration.xml"/>
                <xi:include href="r_submix_audio_policy_configuration.xml"/>
            </modules>
            <xi:include href="audio_policy_volumes.xml"/>
        </audioPolicyConfiguration>
    """.trim()

    private val STOCK_USB_COMMENTED = """
        <module name="usb" halVersion="2.0">
            <!--<mixPort name="usb_device output" role="source"/>-->
        </module>
    """.trim()

    @Test
    fun buildApplyConfigInlinesUsbAndAbsolutizesIncludes() {
        val config = UsbAudioCodec.buildApplyConfig(STOCK_MAIN)!!
        assertTrue(config.contains(UsbAudioCodec.USB_MODULE_XML))
        assertTrue(config.contains("href=\"/vendor/etc/a2dp_audio_policy_configuration.xml\""))
        assertTrue(config.contains("href=\"/vendor/etc/audio_policy_volumes.xml\""))
        assertFalse(config.contains("href=\"usb_audio_policy_configuration.xml\""))
        assertTrue(UsbAudioCodec.isApplied(config))
    }

    @Test
    fun buildApplyConfigIsIdempotentAndRejectsUnknownLayout() {
        val once = UsbAudioCodec.buildApplyConfig(STOCK_MAIN)!!
        assertEquals(once, UsbAudioCodec.buildApplyConfig(once))
        assertNull(UsbAudioCodec.buildApplyConfig("<!-- nothing here -->"))
    }

    @Test
    fun buildRestoreConfigSwapsModuleBackToVendorInclude() {
        val applied = UsbAudioCodec.buildApplyConfig(STOCK_MAIN)!!
        val restored = UsbAudioCodec.buildRestoreConfig(applied)
        assertTrue(restored.contains("<xi:include href=\"/vendor/etc/usb_audio_policy_configuration.xml\"/>"))
        assertFalse(UsbAudioCodec.isApplied(restored))
    }

    @Test
    fun effectiveAppliedPrefersVendorWhenItsNameExists() {
        val applied = UsbAudioCodec.buildApplyConfig(STOCK_MAIN)!!
        // vendor present (stock) wins over /system with the usb module
        assertEquals(false, UsbAudioCodec.effectiveApplied(true, STOCK_MAIN, applied))
        // vendor name removed -> /system is what audioserver reads
        assertEquals(true, UsbAudioCodec.effectiveApplied(false, null, applied))
        assertEquals(true, UsbAudioCodec.effectiveApplied(true, applied, null))
        assertEquals(false, UsbAudioCodec.effectiveApplied(false, null, STOCK_MAIN))
        assertEquals(false, UsbAudioCodec.effectiveApplied(true, STOCK_USB_COMMENTED, null))
    }

    @Test
    fun effectiveAppliedIgnoresBindOverlayContent() {
        val applied = UsbAudioCodec.buildApplyConfig(STOCK_MAIN)!!
        val vendorBind = "/data/x.xml ${UsbAudioCodec.VENDOR_MAIN} ext4 rw 0 0"
        val systemBind = "/data/x.xml ${UsbAudioCodec.SYSTEM_MAIN} ext4 rw 0 0"
        // overlay over /vendor shows the applied file, but the permanent variant needs the vendor name gone
        assertEquals(false, UsbAudioCodec.effectiveApplied(true, applied, null, vendorBind))
        // overlay over /system hides the real file
        assertNull(UsbAudioCodec.effectiveApplied(false, null, applied, systemBind))
        assertEquals(true, UsbAudioCodec.effectiveApplied(false, null, applied, vendorBind))
    }

    @Test
    fun chooseSourcePrefersVendorUnlessSystemIsApplied() {
        val applied = UsbAudioCodec.buildApplyConfig(STOCK_MAIN)!!
        assertEquals(STOCK_MAIN, UsbAudioCodec.chooseSource(STOCK_MAIN, "<audioPolicyConfiguration/>"))
        assertEquals(applied, UsbAudioCodec.chooseSource(STOCK_MAIN, applied))
        assertEquals(applied, UsbAudioCodec.chooseSource(null, applied))
        assertEquals(STOCK_MAIN, UsbAudioCodec.chooseSource(null, STOCK_MAIN))
        assertNull(UsbAudioCodec.chooseSource(null, null))
    }

    @Test
    fun unbindCommandDropsBothOverlaysWithoutSingleQuotes() {
        val cmd = UsbAudioCodec.UNBIND_COMMAND
        assertTrue(cmd.contains(UsbAudioCodec.VENDOR_MAIN))
        assertTrue(cmd.contains(UsbAudioCodec.SYSTEM_MAIN))
        assertTrue(cmd.contains("umount \$T"))
        assertFalse(cmd.contains('\''))
    }

    @Test
    fun scriptsWriteAtomicallyAndRestoreReadOnlyOnEveryExit() {
        for (script in listOf(UsbAudioCodec.applyScript(), UsbAudioCodec.restoreScript())) {
            assertTrue(script.contains("N=\"\$1.new\""))
            assertTrue(script.contains("cat \"\$2\" > \"\$N\""))
            assertTrue(script.contains("chcon u:object_r:system_file:s0 \"\$N\""))
            assertTrue(script.contains("[ \"\$(md5 \"\$N\")\" = \"\$3\" ] || { rm -f \"\$N\"; R=verify; return 1; }"))
            assertTrue(script.contains("mv -f \"\$N\" \"\$1\""))
            assertTrue(script.contains("has_room \"\${1%/*}\" \"\$2\" || { R=nospace; return 1; }"))
            assertTrue(script.contains("df -k"))
            assertTrue(script.contains("put \"\$S\" \"\$C\" \"\$H\" || fail \"\$R\""))
            assertFalse(script.contains("cp \"\$C\" \"\$S\""))
            assertTrue(script.contains("[ \"\$WP\" = 1 ] || return 0"))
            assertTrue(script.contains("mount -o remount,ro / 2>/dev/null"))
            assertTrue(script.contains("[ \"\$VRW\" = 1 ] && mount -o remount,ro /vendor"))
            assertTrue(script.contains("trap 'wp_on' EXIT"))
            assertTrue(script.contains("trap 'wp_on; exit 1' HUP INT TERM PIPE"))
            assertTrue(script.contains("trap - EXIT HUP INT TERM PIPE"))
            val wpOn = script.indexOf("mount -o remount,ro / 2>/dev/null")
            assertTrue(wpOn < script.indexOf("echo emmc_enable_all_wp"))
            assertTrue(script.indexOf("trap 'wp_on' EXIT") < script.indexOf("echo emmc_disable_all_wp"))
        }
    }

    @Test
    fun scriptsDropBindOverlaysBeforeWriteProtectDance() {
        for (script in listOf(UsbAudioCodec.applyScript(), UsbAudioCodec.restoreScript())) {
            assertTrue(script.contains("for T in \"\$V\" \"\$S\"; do"))
            assertTrue(script.contains("fail bindactive"))
            val unbind = script.indexOf("\nunbind\n")
            assertTrue(unbind >= 0 && unbind < script.indexOf("echo emmc_disable_all_wp"))
        }
    }

    @Test
    fun isBindMountedDetectsTboxMonitorOverlay() {
        val mounts = """
            /dev/block/vendor_b /vendor ext4 rw,seclabel,relatime 0 0
            /data/local/tmp/tbox-usbaudio/audio_policy_configuration.bind.xml ${UsbAudioCodec.VENDOR_MAIN} ext4 rw,seclabel,relatime 0 0
        """.trimIndent()
        assertTrue(UsbAudioCodec.isBindMounted(mounts))
        assertTrue(
            UsbAudioCodec.isBindMounted(
                "src ${UsbAudioCodec.SYSTEM_MAIN} ext4 rw 0 0",
            ),
        )
        assertFalse(UsbAudioCodec.isBindMounted("/dev/root /vendor ext4 rw 0 0"))
        assertFalse(UsbAudioCodec.isBindMounted("tmpfs /mnt tmpfs rw 0 0"))
    }

    @Test
    fun applyScriptPerformsWriteProtectDanceAndRemovesVendorName() {
        val script = UsbAudioCodec.applyScript()
        assertTrue(script.contains("echo emmc_disable_all_wp >"))
        assertTrue(script.contains("echo emmc_enable_all_wp >"))
        assertTrue(script.contains("trap 'wp_on' EXIT"))
        assertTrue(script.contains("mount -o remount,rw / || fail remountsys"))
        assertTrue(script.contains("echo 3 > /proc/sys/vm/drop_caches"))
        assertTrue(script.contains("VRW=1\n    mount -o remount,rw /vendor"))
        assertTrue(script.contains("rm -f \"${'$'}V\" || fail rmvendor"))
        assertTrue(script.contains("[ ! -e \"${'$'}V\" ] || fail rmvendor"))
        assertTrue(script.contains("setprop ctl.restart audioserver"))
        assertTrue(script.contains("/authorized"))
        assertTrue(script.contains("for D in /sys/bus/usb/devices/${'$'}{HUB#usb}-*; do"))
        assertTrue(script.contains("[ \"${'$'}N\" -eq 1 ] || HUB=$(basename \"${'$'}U\") ;;"))
        assertTrue(script.contains(UsbAudioCodec.APPLIED_MARKER))
    }

    @Test
    fun restoreScriptTouchesOnlySystemConfig() {
        val script = UsbAudioCodec.restoreScript()
        assertTrue(script.contains("S=${UsbAudioCodec.SYSTEM_MAIN}"))
        assertTrue(script.contains("trap 'wp_on' EXIT"))
        assertTrue(script.contains(UsbAudioCodec.RESTORED_MARKER))
        assertFalse(script.contains("rm -f \"${'$'}V\""))
        assertFalse(script.contains("remount,rw /vendor"))
        assertFalse(script.contains("/authorized"))
        assertFalse(script.contains(UsbAudioCodec.APPLIED_MARKER))
    }

    @Test
    fun parsesOutcomeMarkers() {
        assertEquals(UsbAudioCodec.Outcome.Applied, UsbAudioCodec.parseOutcome("x\n${UsbAudioCodec.APPLIED_MARKER}"))
        assertEquals(UsbAudioCodec.Outcome.Restored, UsbAudioCodec.parseOutcome(UsbAudioCodec.RESTORED_MARKER))
        assertEquals(
            UsbAudioCodec.Outcome.Failed("wpoff"),
            UsbAudioCodec.parseOutcome("${UsbAudioCodec.ERR_MARKER} wpoff"),
        )
        assertEquals(UsbAudioCodec.Outcome.Failed("failed"), UsbAudioCodec.parseOutcome(""))
    }
}
