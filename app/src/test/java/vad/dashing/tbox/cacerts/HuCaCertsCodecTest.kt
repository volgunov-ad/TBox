package vad.dashing.tbox.cacerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HuCaCertsCodecTest {

    @Test
    fun installScript_overlaysOnlyWhenNotMountedAndCopiesEveryCert() {
        val script = HuCaCertsCodec.installScript()
        assertTrue(script.contains("if ! grep -q \" \$D tmpfs \" /proc/mounts; then"))
        assertTrue(script.contains("mount -t tmpfs -o mode=755 tmpfs \$D"))
        assertTrue(script.contains("for f in b92fd57f.0 4c3982f2.0 8ca696b9.0; do"))
        assertTrue(script.contains("chcon u:object_r:system_file:s0"))
        assertTrue(!script.contains("\r"))
    }

    @Test
    fun shellCommand_runsScriptViaSuOrReportsMissingSu() {
        val cmd = HuCaCertsCodec.shellCommand("/data/x/install_ca.sh", "/data/x")
        assertEquals(
            "if command -v su >/dev/null 2>&1; then su 0 sh '/data/x/install_ca.sh' '/data/x'; " +
                "else echo 'TBOX_CA_ERR nosu'; fi",
            cmd,
        )
    }

    @Test
    fun parseOutcome_ok() {
        assertEquals(HuCaInstallOutcome.Installed(140), HuCaCertsCodec.parseOutcome("noise\nTBOX_CA_OK 140\n"))
    }

    @Test
    fun parseOutcome_errorCode() {
        assertEquals(HuCaInstallOutcome.Failed("mount"), HuCaCertsCodec.parseOutcome("TBOX_CA_ERR mount"))
        assertEquals(HuCaInstallOutcome.Failed("nosu"), HuCaCertsCodec.parseOutcome("TBOX_CA_ERR nosu\n"))
    }

    @Test
    fun parseOutcome_unknownOutputIsFailure() {
        assertEquals(HuCaInstallOutcome.Failed("failed"), HuCaCertsCodec.parseOutcome("Permission denied"))
    }
}
