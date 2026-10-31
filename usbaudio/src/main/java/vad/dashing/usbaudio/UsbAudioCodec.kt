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

    /**
     * audioserver reads /vendor first; /system matters only when the vendor name is gone.
     * A bind overlay shows its own content: over /vendor the permanent variant cannot be
     * active (the vendor name still exists), over /system the real file is unknown (null).
     */
    fun effectiveApplied(
        vendorMainExists: Boolean,
        vendorMain: String?,
        systemMain: String?,
        procMounts: String = "",
    ): Boolean? {
        val bound = boundConfigPaths(procMounts)
        if (vendorMainExists) return VENDOR_MAIN !in bound && vendorMain != null && isApplied(vendorMain)
        if (SYSTEM_MAIN in bound) return null
        return systemMain != null && isApplied(systemMain)
    }

    /** Config paths with a bind overlay (TBox Monitor's temporary variant) on top. */
    fun boundConfigPaths(procMounts: String): Set<String> = procMounts.lineSequence().mapNotNull { line ->
        line.split(' ').takeIf { it.size > 2 }?.get(1)?.takeIf { it == VENDOR_MAIN || it == SYSTEM_MAIN }
    }.toSet()

    fun isBindMounted(procMounts: String): Boolean = boundConfigPaths(procMounts).isNotEmpty()

    /** Stock /vendor is the source unless /system already carries the applied config. */
    fun chooseSource(vendorMain: String?, systemMain: String?): String? = when {
        systemMain != null && isApplied(systemMain) -> systemMain
        vendorMain != null -> vendorMain
        else -> systemMain
    }

    /** Drops TBox Monitor's overlays (possibly stacked) so the real files are read and written. */
    const val UNBIND_COMMAND =
        "for T in $VENDOR_MAIN $SYSTEM_MAIN; do umount \$T 2>/dev/null; umount \$T 2>/dev/null; done; true"

    /**
     * The new file is written next to the target as `.new`, verified on disk and renamed over it,
     * so a short write never leaves a truncated config. wp_on is idempotent and also remounts ro.
     */
    private fun scriptPrologue(): String = """
        |C='$STAGED_CONFIG'
        |S=$SYSTEM_MAIN
        |V=$VENDOR_MAIN
        |P=$WP_PROC
        |D=$DISK
        |WP=0
        |VRW=0
        |wp_on() {
        |    [ "${'$'}WP" = 1 ] || return 0
        |    WP=0
        |    sync
        |    [ "${'$'}VRW" = 1 ] && mount -o remount,ro /vendor 2>/dev/null
        |    mount -o remount,ro / 2>/dev/null
        |    echo emmc_enable_all_wp > "${'$'}P" 2>/dev/null
        |}
        |fail() { echo "$ERR_MARKER ${'$'}1"; exit 1; }
        |md5() { md5sum "${'$'}1" 2>/dev/null | cut -d' ' -f1; }
        |free_kb() { set -- ${'$'}(df -k "${'$'}1" 2>/dev/null | tail -n 1); echo "${'$'}4"; }
        |has_room() {
        |    F=${'$'}(free_kb "${'$'}1"); Z=${'$'}(stat -c %s "${'$'}2" 2>/dev/null)
        |    case "${'$'}F" in ''|*[!0-9]*) return 0 ;; esac
        |    case "${'$'}Z" in ''|*[!0-9]*) return 0 ;; esac
        |    [ "${'$'}F" -ge ${'$'}((Z / 1024 + 256)) ]
        |}
        |put() {
        |    N="${'$'}1.new"
        |    rm -f "${'$'}N"
        |    has_room "${'$'}{1%/*}" "${'$'}2" || { R=nospace; return 1; }
        |    cat "${'$'}2" > "${'$'}N" || { rm -f "${'$'}N"; R=write; return 1; }
        |    chown root:root "${'$'}N" && chmod 644 "${'$'}N" || { rm -f "${'$'}N"; R=perms; return 1; }
        |    chcon u:object_r:system_file:s0 "${'$'}N" 2>/dev/null
        |    sync
        |    blockdev --flushbufs "${'$'}D" 2>/dev/null
        |    echo 3 > /proc/sys/vm/drop_caches
        |    [ "${'$'}(md5 "${'$'}N")" = "${'$'}3" ] || { rm -f "${'$'}N"; R=verify; return 1; }
        |    mv -f "${'$'}N" "${'$'}1" || { rm -f "${'$'}N"; R=rename; return 1; }
        |    sync
        |    [ "${'$'}(md5 "${'$'}1")" = "${'$'}3" ] || { R=verify; return 1; }
        |}
        |unbind() {
        |    for T in "${'$'}V" "${'$'}S"; do
        |        if grep -q " ${'$'}T " /proc/mounts; then umount "${'$'}T" 2>/dev/null; umount "${'$'}T" 2>/dev/null; fi
        |        if grep -q " ${'$'}T " /proc/mounts; then fail bindactive; fi
        |    done
        |}
        |
    """.trimMargin()

    private fun wpOff(): String = """
        |trap 'wp_on' EXIT
        |trap 'wp_on; exit 1' HUP INT TERM PIPE
        |WP=1
        |echo emmc_disable_all_wp > "${'$'}P" 2>/dev/null || fail wpoff
        |mount -o remount,rw / || fail remountsys
        |
    """.trimMargin()

    fun applyScript(): String = scriptPrologue() + """
        |[ -f "${'$'}C" ] || fail nostage
        |grep -q '<module name="usb" halVersion="2.0">' "${'$'}C" || fail stage
        |H=${'$'}(md5 "${'$'}C")
        |[ -n "${'$'}H" ] || fail stage
        |unbind
        |
    """.trimMargin() + wpOff() + """
        |put "${'$'}S" "${'$'}C" "${'$'}H" || fail "${'$'}R"
        |if [ -e "${'$'}V" ]; then
        |    VRW=1
        |    mount -o remount,rw /vendor 2>/dev/null
        |    rm -f "${'$'}V" || fail rmvendor
        |    sync
        |    blockdev --flushbufs "${'$'}D" 2>/dev/null
        |    echo 3 > /proc/sys/vm/drop_caches
        |    [ ! -e "${'$'}V" ] || fail rmvendor
        |fi
        |wp_on
        |trap - EXIT HUP INT TERM PIPE
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
        |    case "${'$'}HUB" in usb*)
        |        N=0
        |        for D in /sys/bus/usb/devices/${'$'}{HUB#usb}-*; do
        |            case "$(basename "${'$'}D")" in *:*) ;; *) [ -e "${'$'}D" ] && N=${'$'}((N + 1)) ;; esac
        |        done
        |        [ "${'$'}N" -eq 1 ] || HUB=$(basename "${'$'}U") ;;
        |    esac
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

    fun restoreScript(): String = scriptPrologue() + """
        |[ -f "${'$'}C" ] || fail nostage
        |grep -q 'audioPolicyConfiguration' "${'$'}C" || fail stage
        |H=${'$'}(md5 "${'$'}C")
        |[ -n "${'$'}H" ] || fail stage
        |unbind
        |
    """.trimMargin() + wpOff() + """
        |put "${'$'}S" "${'$'}C" "${'$'}H" || fail "${'$'}R"
        |wp_on
        |trap - EXIT HUP INT TERM PIPE
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
