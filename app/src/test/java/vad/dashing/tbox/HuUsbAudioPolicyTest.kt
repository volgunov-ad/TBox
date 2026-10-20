package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.huaudio.HuUsbAudioOutcome
import vad.dashing.tbox.huaudio.HuUsbAudioPolicyCodec

class HuUsbAudioPolicyTest {
    /** Stock A10 vendor main config: relative includes, usb module behind an include. */
    private val STOCK_MAIN = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <!-- Audio Policy Configuration -->
        <audioPolicyConfiguration version="1.0" xmlns:xi="http://www.w3.org/2001/XInclude">
            <modules>
                <module name="primary" halVersion="2.0">
                    <mixPorts>
                        <mixPort name="primary output" role="source"></mixPort>
                    </mixPorts>
                </module>
                <xi:include href="a2dp_audio_policy_configuration.xml"/>
                <xi:include href="usb_audio_policy_configuration.xml"/>
                <xi:include href="r_submix_audio_policy_configuration.xml"/>
            </modules>
            <xi:include href="audio_policy_volumes.xml"/>
            <xi:include href="default_volume_tables.xml"/>
        </audioPolicyConfiguration>
    """.trim()

    /** Stock usb file body: the whole module sits inside one comment. */
    private val STOCK_USB_COMMENTED = """
        <module name="usb" halVersion="2.0">
            <!--<mixPorts>
                <mixPort name="usb_device output" role="source"/>
            </mixPorts>-->
        </module>
    """.trim()

    @Test
    fun buildApplyConfigInlinesUsbModuleAndAbsolutizesIncludes() {
        val config = HuUsbAudioPolicyCodec.buildApplyConfig(STOCK_MAIN)!!
        // usb include replaced with the verified output-only module
        assertTrue(config.contains(HuUsbAudioPolicyCodec.USB_MODULE_XML))
        assertFalse(config.contains("href=\"usb_audio_policy_configuration.xml\""))
        assertTrue(config.contains("<mixPort name=\"usb_device output\""))
        // every other include keeps loading, but from its real /vendor/etc home
        assertTrue(config.contains("href=\"/vendor/etc/a2dp_audio_policy_configuration.xml\""))
        assertTrue(config.contains("href=\"/vendor/etc/r_submix_audio_policy_configuration.xml\""))
        assertTrue(config.contains("href=\"/vendor/etc/audio_policy_volumes.xml\""))
        assertTrue(config.contains("href=\"/vendor/etc/default_volume_tables.xml\""))
        // the primary module stays untouched
        assertTrue(config.contains("<module name=\"primary\" halVersion=\"2.0\">"))
        assertTrue(HuUsbAudioPolicyCodec.isApplied(config))
    }

    @Test
    fun buildApplyConfigIsIdempotentForAlreadyTransformedConfig() {
        val once = HuUsbAudioPolicyCodec.buildApplyConfig(STOCK_MAIN)!!
        val twice = HuUsbAudioPolicyCodec.buildApplyConfig(once)!!
        assertEquals(once, twice)
    }

    @Test
    fun buildApplyConfigRejectsUnknownLayout() {
        val noUsb = STOCK_MAIN.replace("<xi:include href=\"usb_audio_policy_configuration.xml\"/>\n", "")
        assertNull(HuUsbAudioPolicyCodec.buildApplyConfig(noUsb))
        assertNull(HuUsbAudioPolicyCodec.buildApplyConfig(""))
    }

    @Test
    fun buildRestoreConfigSwapsInlineModuleBackToVendorInclude() {
        val applied = HuUsbAudioPolicyCodec.buildApplyConfig(STOCK_MAIN)!!
        val restored = HuUsbAudioPolicyCodec.buildRestoreConfig(applied)
        assertTrue(restored.contains("<xi:include href=\"/vendor/etc/usb_audio_policy_configuration.xml\"/>"))
        assertFalse(restored.contains("<mixPort name=\"usb_device output\""))
        assertFalse(HuUsbAudioPolicyCodec.isApplied(restored))
        // a stock config without the inline module passes through unchanged
        assertEquals(
            HuUsbAudioPolicyCodec.absolutizeIncludes(STOCK_MAIN),
            HuUsbAudioPolicyCodec.buildRestoreConfig(STOCK_MAIN),
        )
    }

    @Test
    fun isAppliedIgnoresCommentedOutModuleBodies() {
        assertTrue(HuUsbAudioPolicyCodec.isApplied(HuUsbAudioPolicyCodec.USB_MODULE_XML))
        assertFalse(HuUsbAudioPolicyCodec.isApplied(STOCK_USB_COMMENTED))
        assertFalse(HuUsbAudioPolicyCodec.isApplied(""))
    }

    @Test
    fun isBindMountedDetectsOverlayOverEitherConfigPath() {
        val mounts = """
            /dev/root / ext4 rw,seclabel,relatime 0 0
            /dev/block/vendor_b /vendor ext4 rw,seclabel,relatime 0 0
            /data/local/tmp/tbox-usbaudio/audio_policy_configuration.bind.xml ${HuUsbAudioPolicyCodec.VENDOR_MAIN} ext4 rw,seclabel,relatime 0 0
        """.trimIndent()
        assertTrue(HuUsbAudioPolicyCodec.isBindMounted(mounts))
        assertTrue(
            HuUsbAudioPolicyCodec.isBindMounted(
                "src ${HuUsbAudioPolicyCodec.SYSTEM_MAIN} ext4 rw 0 0",
            ),
        )
        assertFalse(
            HuUsbAudioPolicyCodec.isBindMounted(
                "/dev/root / ext4 rw 0 0\ntmpfs /mnt tmpfs rw 0 0",
            ),
        )
        // the config paths must match the mount point column, not a substring elsewhere
        assertFalse(HuUsbAudioPolicyCodec.isBindMounted("/dev/root /vendor ext4 rw 0 0"))
    }

    @Test
    fun applyScriptBindsOverConfigAndReenumeratesCard() {
        val script = HuUsbAudioPolicyCodec.applyScript("/data/local/tmp/cfg.xml")
        assertTrue(script.contains("C='/data/local/tmp/cfg.xml'"))
        // target: vendor main when present, otherwise the permanent /system config
        assertTrue(script.contains("V=${HuUsbAudioPolicyCodec.VENDOR_MAIN}"))
        assertTrue(script.contains("S=${HuUsbAudioPolicyCodec.SYSTEM_MAIN}"))
        assertTrue(script.contains("T=\"${'$'}V\""))
        assertTrue(script.contains("[ -e \"${'$'}T\" ] || T=\"${'$'}S\""))
        // a leftover overlay is dropped first and the fresh one verified through /proc/mounts
        assertTrue(script.contains("umount \"${'$'}T\" 2>/dev/null; umount \"${'$'}T\" 2>/dev/null"))
        assertTrue(script.contains("mount --bind \"${'$'}W\" \"${'$'}T\" || fail bind"))
        assertTrue(script.contains("grep -q \" ${'$'}T \" /proc/mounts || fail bindverify"))
        assertTrue(script.contains("setprop ctl.restart audioserver"))
        // after a restart UsbHostManager never re-announces a known card: re-enumerate the hub
        assertTrue(script.contains("sleep 8"))
        assertTrue(script.contains("*/usb*/*:*"))
        assertTrue(script.contains("echo 0 > "))
        assertTrue(script.contains("echo 1 > "))
        // a hubless card goes through the root hub only when it is the bus's sole device
        assertTrue(script.contains("for D in /sys/bus/usb/devices/${'$'}{HUB#usb}-*; do"))
        assertTrue(script.contains("[ \"${'$'}N\" -eq 1 ] || HUB=$(basename \"${'$'}U\") ;;"))
        // the temporary variant never touches protected partitions
        assertFalse(script.contains("mmc_debug"))
        assertFalse(script.contains("remount"))
        assertFalse(script.contains("rm -f"))
        assertTrue(script.contains("TBOX_USB_APPLIED"))
        assertTrue(script.contains("TBOX_USB_ERR"))
    }

    @Test
    fun restoreScriptDropsOverlayAndRestartsAudioserver() {
        val script = HuUsbAudioPolicyCodec.restoreScript()
        assertTrue(script.contains("V=${HuUsbAudioPolicyCodec.VENDOR_MAIN}"))
        assertTrue(script.contains("[ -e \"${'$'}T\" ] || T=\"${'$'}S\""))
        assertTrue(script.contains("umount \"${'$'}T\" 2>/dev/null"))
        assertTrue(script.contains("if grep -q \" ${'$'}T \" /proc/mounts; then fail umount; fi"))
        assertTrue(script.contains("setprop ctl.restart audioserver"))
        assertTrue(script.contains("TBOX_USB_RESTORED"))
        assertFalse(script.contains("TBOX_USB_APPLIED"))
        assertFalse(script.contains("/authorized"))
        assertFalse(script.contains("mmc_debug"))
    }

    @Test
    fun shellCommandRunsThroughSu() {
        val command = HuUsbAudioPolicyCodec.shellCommand("/data/local/tmp/tbox.sh")
        assertTrue(command.startsWith("if command -v su >/dev/null 2>&1; then su 0 sh '/data/local/tmp/tbox.sh';"))
        assertTrue(command.contains("else echo 'TBOX_USB_ERR nosu'; fi"))
    }

    @Test
    fun parsesAppliedRestoredAndFailedMarkers() {
        assertEquals(
            HuUsbAudioOutcome.Applied,
            HuUsbAudioPolicyCodec.parseOutcome("cp: ok\nTBOX_USB_APPLIED"),
        )
        assertEquals(HuUsbAudioOutcome.Restored, HuUsbAudioPolicyCodec.parseOutcome("TBOX_USB_RESTORED"))
        assertEquals(
            HuUsbAudioOutcome.Failed("bind"),
            HuUsbAudioPolicyCodec.parseOutcome("mount: failed\nTBOX_USB_ERR bind"),
        )
        assertEquals(HuUsbAudioOutcome.Failed("nosu"), HuUsbAudioPolicyCodec.parseOutcome("TBOX_USB_ERR nosu"))
        assertEquals(HuUsbAudioOutcome.Failed("failed"), HuUsbAudioPolicyCodec.parseOutcome(""))
    }
}
