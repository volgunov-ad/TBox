package vad.dashing.tbox.cacerts

internal sealed interface HuCaInstallOutcome {
    data class Installed(val totalCerts: Int?) : HuCaInstallOutcome
    data class Failed(val code: String) : HuCaInstallOutcome
}

/**
 * The A10 HU /system is an immutable ext4 image (writes fail with EIO even as root), so the
 * extra roots are installed by mounting a tmpfs copy of the stock store over it. The overlay is
 * lost on reboot.
 */
internal object HuCaCertsCodec {
    const val SYSTEM_STORE_DIR = "/system/etc/security/cacerts"
    const val ASSET_DIR = "ca_certs"
    const val SCRIPT_NAME = "install_ca.sh"

    private const val STAGE_DIR = "/data/local/tmp/tbox-cacerts-stage"
    private const val OK_MARKER = "TBOX_CA_OK"
    private const val ERR_MARKER = "TBOX_CA_ERR"

    /** File names follow the Android `<subject_hash_old>.0` convention. */
    val CERT_FILES = listOf(
        "b92fd57f.0", // HARICA TLS RSA Root CA 2021 (RuStore, *.rustore.ru)
        "4c3982f2.0", // HARICA TLS ECC Root CA 2021
        "8ca696b9.0", // Russian Trusted Root CA (Минцифры)
    )

    fun installScript(certFiles: List<String> = CERT_FILES): String {
        val names = certFiles.joinToString(" ")
        return """
            |D=$SYSTEM_STORE_DIR
            |SRC="${'$'}1"
            |S=$STAGE_DIR
            |fail() { echo "$ERR_MARKER ${'$'}1"; exit 1; }
            |if ! grep -q " ${'$'}D tmpfs " /proc/mounts; then
            |  rm -rf ${'$'}S; mkdir -p ${'$'}S || fail stage
            |  cp -p ${'$'}D/* ${'$'}S/ || { rm -rf ${'$'}S; fail backup; }
            |  mount -t tmpfs -o mode=755 tmpfs ${'$'}D || { rm -rf ${'$'}S; fail mount; }
            |  cp -p ${'$'}S/* ${'$'}D/ || { umount ${'$'}D; rm -rf ${'$'}S; fail restore; }
            |  rm -rf ${'$'}S
            |fi
            |for f in $names; do cp "${'$'}SRC/${'$'}f" ${'$'}D/${'$'}f || fail copy; done
            |chown root:root ${'$'}D ${'$'}D/*
            |chmod 755 ${'$'}D
            |chmod 644 ${'$'}D/*
            |chcon u:object_r:system_file:s0 ${'$'}D ${'$'}D/* 2>/dev/null
            |echo "$OK_MARKER ${'$'}(ls ${'$'}D | wc -l)"
            |
        """.trimMargin()
    }

    fun shellCommand(scriptPath: String, sourceDir: String): String =
        "if command -v su >/dev/null 2>&1; then su 0 sh '$scriptPath' '$sourceDir'; " +
            "else echo '$ERR_MARKER nosu'; fi"

    fun parseOutcome(output: String): HuCaInstallOutcome {
        val lines = output.lineSequence().map { it.trim() }
        lines.firstOrNull { it.startsWith(OK_MARKER) }?.let { line ->
            return HuCaInstallOutcome.Installed(line.removePrefix(OK_MARKER).trim().toIntOrNull())
        }
        val code = lines.firstOrNull { it.startsWith(ERR_MARKER) }
            ?.removePrefix(ERR_MARKER)?.trim()?.ifEmpty { null }
        return HuCaInstallOutcome.Failed(code ?: "failed")
    }
}
