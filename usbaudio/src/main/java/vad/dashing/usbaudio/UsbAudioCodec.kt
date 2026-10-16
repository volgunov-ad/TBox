package vad.dashing.usbaudio

/**
 * Permanent USB audio enablement for the A10 head unit (companion of the Data Resize app:
 * one-shot dangerous eMMC operations live outside TBox Monitor).
 *
 * The stock audio policy keeps the whole usb module commented out, /vendor sits behind the
 * eMMC software write protect and on the known unit its ext4 is fully allocated, so the full
 * config (inline output-only usb module, other includes absolutized to /vendor/etc) is written
 * to /system/etc/audio_policy_configuration.xml and the /vendor file name is removed:
 * libaudiopolicymanagerdefault.so falls back to /system when /vendor is absent.
 * The write protect is disabled via /proc/mmc_debug for the write and re-enabled from an
 * EXIT trap. Verified on a real HU, survives reboot. See docs/USB_AUDIO_A10_RU.md.
 */
object UsbAudioCodec {
    const val VENDOR_MAIN = "/vendor/etc/audio_policy_configuration.xml"
    const val SYSTEM_MAIN = "/system/etc/audio_policy_configuration.xml"
    const val WORK_DIR = "/data/local/tmp/usbaudio"
    const val STAGED_CONFIG = "$WORK_DIR/audio_policy_configuration.tbox.xml"
    const val APPLY_SCRIPT = "$WORK_DIR/apply.sh"
    const val RESTORE_SCRIPT = "$WORK_DIR/restore.sh"

    private const val WP_PROC = "/proc/mmc_debug"
    private const val DISK = "/dev/block/mmcblk0"
    const val APPLIED_MARKER = "TBOX_USB_APPLIED"
    const val RESTORED_MARKER = "TBOX_USB_RESTORED"
    const val ERR_MARKER = "TBOX_USB_ERR"

    /** Verified on the HU: output-only policy (44100/48000, no input — the mic is iFlytek's). */
    val USB_MODULE_XML: String = """
        <module name="usb" halVersion="2.0">
            <mixPorts>
                <mixPort name="usb_accessory output" role="source">
                    <profile name="" format="AUDIO_FORMAT_PCM_16_BIT"
                             samplingRates="44100,48000" channelMasks="AUDIO_CHANNEL_OUT_STEREO"/>
                </mixPort>
                <mixPort name="usb_device output" role="source"/>
            </mixPorts>
            <devicePorts>
                <devicePort tagName="USB Host Out" type="AUDIO_DEVICE_OUT_USB_ACCESSORY" role="sink">
                    <profile name="" format="AUDIO_FORMAT_PCM_16_BIT"
                             samplingRates="44100,48000" channelMasks="AUDIO_CHANNEL_OUT_STEREO"/>
                </devicePort>
                <devicePort tagName="USB Device Out" type="AUDIO_DEVICE_OUT_USB_DEVICE" role="sink"/>
                <devicePort tagName="USB Headset Out" type="AUDIO_DEVICE_OUT_USB_HEADSET" role="sink"/>
            </devicePorts>
            <routes>
                <route type="mix" sink="USB Host Out" sources="usb_accessory output"/>
                <route type="mix" sink="USB Device Out" sources="usb_device output"/>
                <route type="mix" sink="USB Headset Out" sources="usb_device output"/>
            </routes>
        </module>
    """.trim()

    private val xmlComment = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val usbInclude =
        Regex("""<xi:include[^>]*href="(?:/vendor/etc/)?usb_audio_policy_configuration\.xml"[^>]*/>""")
    private val usbModule = Regex("""<module name="usb"[^>]*>.*?</module>""", RegexOption.DOT_MATCHES_ALL)
    private val relativeHref = Regex("""href="(?!/)([A-Za-z0-9_.-]+\.xml)""")

    /** The config lands in /system/etc, so relative includes must point to /vendor/etc absolutely. */
    fun absolutizeIncludes(source: String): String = source.replace(relativeHref) { m ->
        "href=\"/vendor/etc/${m.groupValues[1]}"
    }

    /** Full /system config with the inline usb module; null when the layout is unknown. */
    fun buildApplyConfig(source: String): String? {
        val base = absolutizeIncludes(source)
        val body = base.replace(xmlComment, "")
        return when {
            isApplied(base) -> base
            usbInclude.containsMatchIn(body) -> base.replace(usbInclude, USB_MODULE_XML)
            else -> null
        }
    }

    /** Stock-behavior config: no inline usb module, usb comes back as a vendor include. */
    fun buildRestoreConfig(source: String): String = absolutizeIncludes(source).replace(
        usbModule,
        """<xi:include href="/vendor/etc/usb_audio_policy_configuration.xml"/>""",
    )

    /** The stock usb file keeps real module tags with the whole body commented out. */
    fun isApplied(content: String): Boolean =
        content.replace(xmlComment, "").contains("<mixPort name=\"usb_device output\"")

    /** audioserver reads /vendor first; /system matters only when the vendor name is gone. */
    fun effectiveApplied(vendorMainExists: Boolean, vendorMain: String?, systemMain: String?): Boolean {
        val content = if (vendorMainExists) vendorMain else systemMain
        return content != null && isApplied(content)
    }

    /** True when a bind overlay (TBox Monitor's temporary variant) sits over a config path. */
    fun isBindMounted(procMounts: String): Boolean = procMounts.lineSequence().any { line ->
        val fields = line.split(' ')
        fields.size > 2 && (fields[1] == VENDOR_MAIN || fields[1] == SYSTEM_MAIN)
    }

    fun applyScript(): String = """
        |C='$STAGED_CONFIG'
        |S=$SYSTEM_MAIN
        |V=$VENDOR_MAIN
        |P=$WP_PROC
        |D=$DISK
        |wp_on() { echo emmc_enable_all_wp > "${'$'}P" 2>/dev/null; }
        |fail() { echo "$ERR_MARKER ${'$'}1"; exit 1; }
        |[ -f "${'$'}C" ] || fail nostage
        |grep -q '<module name="usb" halVersion="2.0">' "${'$'}C" || fail stage
        |if grep -q " ${'$'}V " /proc/mounts; then umount "${'$'}V" 2>/dev/null; fi
        |echo emmc_disable_all_wp > "${'$'}P" 2>/dev/null || fail wpoff
        |trap 'wp_on' EXIT
        |mount -o remount,rw / || fail remountsys
        |cp "${'$'}C" "${'$'}S" || fail copysys
        |chown root:root "${'$'}S" && chmod 644 "${'$'}S"
        |chcon u:object_r:system_file:s0 "${'$'}S" 2>/dev/null
        |sync
        |blockdev --flushbufs "${'$'}D" 2>/dev/null
        |echo 3 > /proc/sys/vm/drop_caches
        |H1=$(md5sum "${'$'}C" | cut -d' ' -f1)
        |H2=$(md5sum "${'$'}S" | cut -d' ' -f1)
        |[ "${'$'}H1" = "${'$'}H2" ] || fail verify
        |if [ -e "${'$'}V" ]; then
        |    mount -o remount,rw /vendor 2>/dev/null
        |    rm -f "${'$'}V" || fail rmvendor
        |    sync
        |    blockdev --flushbufs "${'$'}D" 2>/dev/null
        |    echo 3 > /proc/sys/vm/drop_caches
        |    [ ! -e "${'$'}V" ] || fail rmvendor
        |fi
        |wp_on
        |trap - EXIT
        |setprop ctl.restart audioserver || fail restart
        |sleep 8
        |CD=""
        |for SD in /sys/class/sound/card*/device; do
        |    [ -L "${'$'}SD" ] || continue
        |    T=$(readlink -f "${'$'}SD" 2>/dev/null)
        |    case "${'$'}T" in */usb*/*:*) CD="${'$'}T"; break ;; esac
        |done
        |if [ -n "${'$'}CD" ]; then
        |    U=$(dirname "${'$'}CD")
        |    HUB=$(basename "$(dirname "${'$'}U")")
        |    case "${'$'}HUB" in usb*) HUB=$(basename "${'$'}U") ;; esac
        |    A="/sys/bus/usb/devices/${'$'}HUB/authorized"
        |    if [ -w "${'$'}A" ]; then
        |        echo 0 > "${'$'}A" 2>/dev/null
        |        sleep 3
        |        echo 1 > "${'$'}A" 2>/dev/null
        |        sleep 4
        |    fi
        |fi
        |echo "$APPLIED_MARKER"
        |
    """.trimMargin()

    fun restoreScript(): String = """
        |C='$STAGED_CONFIG'
        |S=$SYSTEM_MAIN
        |P=$WP_PROC
        |D=$DISK
        |wp_on() { echo emmc_enable_all_wp > "${'$'}P" 2>/dev/null; }
        |fail() { echo "$ERR_MARKER ${'$'}1"; exit 1; }
        |[ -f "${'$'}C" ] || fail nostage
        |grep -q 'audioPolicyConfiguration' "${'$'}C" || fail stage
        |echo emmc_disable_all_wp > "${'$'}P" 2>/dev/null || fail wpoff
        |trap 'wp_on' EXIT
        |mount -o remount,rw / || fail remountsys
        |cp "${'$'}C" "${'$'}S" || fail copysys
        |chown root:root "${'$'}S" && chmod 644 "${'$'}S"
        |chcon u:object_r:system_file:s0 "${'$'}S" 2>/dev/null
        |sync
        |blockdev --flushbufs "${'$'}D" 2>/dev/null
        |echo 3 > /proc/sys/vm/drop_caches
        |H1=$(md5sum "${'$'}C" | cut -d' ' -f1)
        |H2=$(md5sum "${'$'}S" | cut -d' ' -f1)
        |[ "${'$'}H1" = "${'$'}H2" ] || fail verify
        |wp_on
        |trap - EXIT
        |setprop ctl.restart audioserver || fail restart
        |echo "$RESTORED_MARKER"
        |
    """.trimMargin()

    sealed interface Outcome {
        data object Applied : Outcome
        data object Restored : Outcome
        data class Failed(val code: String) : Outcome
    }

    fun parseOutcome(output: String): Outcome {
        val lines = output.lineSequence().map { it.trim() }.toList()
        if (APPLIED_MARKER in lines) return Outcome.Applied
        if (RESTORED_MARKER in lines) return Outcome.Restored
        val code = lines.firstOrNull { it.startsWith(ERR_MARKER) }
            ?.removePrefix(ERR_MARKER)?.trim()?.ifEmpty { null }
        return Outcome.Failed(code ?: "failed")
    }
}
