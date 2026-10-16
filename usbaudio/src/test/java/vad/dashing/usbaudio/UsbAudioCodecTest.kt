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
        assertFalse(UsbAudioCodec.effectiveApplied(true, STOCK_MAIN, applied))
        // vendor name removed -> /system is what audioserver reads
        assertTrue(UsbAudioCodec.effectiveApplied(false, null, applied))
        assertTrue(UsbAudioCodec.effectiveApplied(true, applied, null))
        assertFalse(UsbAudioCodec.effectiveApplied(false, null, STOCK_MAIN))
        assertFalse(UsbAudioCodec.effectiveApplied(true, STOCK_USB_COMMENTED, null))
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
        assertTrue(script.contains("wp_on() { echo emmc_enable_all_wp >"))
        assertTrue(script.contains("trap 'wp_on' EXIT"))
        assertTrue(script.contains("mount -o remount,rw / || fail remountsys"))
        assertTrue(script.contains("echo 3 > /proc/sys/vm/drop_caches"))
        assertTrue(script.contains("[ \"${'$'}H1\" = \"${'$'}H2\" ] || fail verify"))
        assertTrue(script.contains("rm -f \"${'$'}V\" || fail rmvendor"))
        assertTrue(script.contains("[ ! -e \"${'$'}V\" ] || fail rmvendor"))
        assertTrue(script.contains("setprop ctl.restart audioserver"))
        assertTrue(script.contains("/authorized"))
        assertTrue(script.contains(UsbAudioCodec.APPLIED_MARKER))
    }

    @Test
    fun restoreScriptTouchesOnlySystemConfig() {
        val script = UsbAudioCodec.restoreScript()
        assertTrue(script.contains("S=${UsbAudioCodec.SYSTEM_MAIN}"))
        assertTrue(script.contains("trap 'wp_on' EXIT"))
        assertTrue(script.contains(UsbAudioCodec.RESTORED_MARKER))
        assertFalse(script.contains("rm -f"))
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
