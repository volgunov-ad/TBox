package vad.dashing.tbox.huaudio

internal sealed interface HuUsbAudioOutcome {
    data object Applied : HuUsbAudioOutcome
    data object Restored : HuUsbAudioOutcome
    data class Failed(val code: String) : HuUsbAudioOutcome
}

/**
 * Temporary USB audio enablement for the A10 HU (TBox Monitor side).
 *
 * The stock audio policy keeps the whole usb module commented out. This variant does not touch
 * protected partitions at all: a transformed copy of the main audio policy (inline output-only
 * usb module, other includes absolutized) is bind-mounted over the config audioserver reads —
 * `/vendor/etc/audio_policy_configuration.xml`, or `/system/etc/...` when the vendor name was
 * removed by the permanent one-shot app (vad.dashing.usbaudio). audioserver is restarted and
 * the USB card re-enumerated through its parent hub (`authorized`), because UsbHostManager
 * does not re-announce an already known device after a restart.
 *
 * The overlay lives until the HU reboots; enable the auto-apply switch to re-apply it at app
 * startup. The permanent write (eMMC write protect dance, /system config, vendor name removal)
 * lives in the separate USB Audio app, like Data Resize.
 */
internal object HuUsbAudioPolicyCodec {
    const val VENDOR_MAIN = "/vendor/etc/audio_policy_configuration.xml"
    const val SYSTEM_MAIN = "/system/etc/audio_policy_configuration.xml"
    const val WORK_DIR = "usbaudio"
    const val STAGED_CONFIG = "audio_policy_configuration.tbox.xml"
    const val APPLY_SCRIPT_NAME = "apply_usb_audio_bind.sh"
    const val RESTORE_SCRIPT_NAME = "restore_usb_audio_bind.sh"

    private const val BIND_DIR = "/data/local/tmp/tbox-usbaudio"
    private const val BIND_SOURCE = "$BIND_DIR/audio_policy_configuration.bind.xml"
    private const val APPLIED_MARKER = "TBOX_USB_APPLIED"
    private const val RESTORED_MARKER = "TBOX_USB_RESTORED"
    private const val ERR_MARKER = "TBOX_USB_ERR"

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

    /** The overlay source lives on /data, so includes must point to /vendor/etc absolutely. */
    fun absolutizeIncludes(source: String): String = source.replace(relativeHref) { m ->
        "href=\"/vendor/etc/${m.groupValues[1]}"
    }

    /** Overlay config with the inline usb module; null when the layout is unknown. */
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

    /** True when a bind overlay sits over one of the config paths (a bind shows as a mount). */
    fun isBindMounted(procMounts: String): Boolean = procMounts.lineSequence().any { line ->
        val fields = line.split(' ')
        fields.size > 2 && (fields[1] == VENDOR_MAIN || fields[1] == SYSTEM_MAIN)
    }

    fun applyScript(configPath: String): String = """
        |C='$configPath'
        |V=$VENDOR_MAIN
        |S=$SYSTEM_MAIN
        |W=$BIND_SOURCE
        |fail() { echo "$ERR_MARKER ${'$'}1"; exit 1; }
        |[ -f "${'$'}C" ] || fail nostage
        |grep -q '<module name="usb" halVersion="2.0">' "${'$'}C" || fail stage
        |T="${'$'}V"
        |[ -e "${'$'}T" ] || T="${'$'}S"
        |[ -e "${'$'}T" ] || fail notarget
        |if grep -q " ${'$'}T " /proc/mounts; then umount "${'$'}T" 2>/dev/null; umount "${'$'}T" 2>/dev/null; fi
        |if grep -q " ${'$'}T " /proc/mounts; then fail umount; fi
        |mkdir -p $BIND_DIR
        |cp "${'$'}C" "${'$'}W" || fail stage
        |chown root:root "${'$'}W" && chmod 644 "${'$'}W"
        |mount --bind "${'$'}W" "${'$'}T" || fail bind
        |grep -q " ${'$'}T " /proc/mounts || fail bindverify
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
        |V=$VENDOR_MAIN
        |S=$SYSTEM_MAIN
        |fail() { echo "$ERR_MARKER ${'$'}1"; exit 1; }
        |T="${'$'}V"
        |[ -e "${'$'}T" ] || T="${'$'}S"
        |[ -e "${'$'}T" ] || fail notarget
        |umount "${'$'}T" 2>/dev/null
        |umount "${'$'}T" 2>/dev/null
        |if grep -q " ${'$'}T " /proc/mounts; then fail umount; fi
        |setprop ctl.restart audioserver || fail restart
        |echo "$RESTORED_MARKER"
        |
    """.trimMargin()

    fun shellCommand(scriptPath: String): String =
        "if command -v su >/dev/null 2>&1; then su 0 sh '$scriptPath'; " +
            "else echo '$ERR_MARKER nosu'; fi"

    fun parseOutcome(output: String): HuUsbAudioOutcome {
        val lines = output.lineSequence().map { it.trim() }.toList()
        if (APPLIED_MARKER in lines) return HuUsbAudioOutcome.Applied
        if (RESTORED_MARKER in lines) return HuUsbAudioOutcome.Restored
        val code = lines.firstOrNull { it.startsWith(ERR_MARKER) }
            ?.removePrefix(ERR_MARKER)?.trim()?.ifEmpty { null }
        return HuUsbAudioOutcome.Failed(code ?: "failed")
    }
}
