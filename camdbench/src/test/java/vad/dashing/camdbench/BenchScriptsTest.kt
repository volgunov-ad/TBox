package vad.dashing.camdbench

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class BenchScriptsTest {

    @Test
    fun startScriptStagesAndAnswersWithNonce() {
        val script = BenchScripts.start("ab12cd34", 660)
        assertTrue(script.startsWith("#!/system/bin/sh\n"))
        assertFalse(script.contains("\r"))
        assertTrue(script.contains("cp ${BenchScripts.SAMPLER_STAGED} ${BenchScripts.SAMPLER}"))
        assertTrue(script.contains("sh ${BenchScripts.RUN} ab12cd34 660 ${BenchScripts.INTERVAL_MS} > "))
        assertTrue(script.contains("< /dev/null &"))
        assertTrue(script.contains("echo ab12cd34 > ${BenchScripts.ARM}"))
        assertTrue(script.trimEnd().endsWith("exit 0"))
    }

    @Test
    fun runScriptStopsLogcatAndMarksDone() {
        val script = BenchScripts.run()
        assertTrue(script.contains("trap '' HUP"))
        assertTrue(script.contains("${BenchScripts.SAMPLER} ${BenchScripts.RAW} ${BenchScripts.CTL} \$NONCE \$2 \$3"))
        assertTrue(script.contains("-s CamdRecorder:I CamdQcar:I"))
        val sampler = script.indexOf(BenchScripts.SAMPLER + " ")
        assertTrue(script.indexOf("while [ ! -f ${BenchScripts.LOGCAT_STOP} ]") in 0 until sampler)
        assertTrue(script.indexOf("touch ${BenchScripts.LOGCAT_STOP}") > sampler)
        assertTrue(script.indexOf("kill \$(cat ${BenchScripts.LOGCAT_PID})") > sampler)
        assertTrue(script.contains("-d >> ${BenchScripts.LOGCAT}"))
        assertTrue(script.indexOf("echo \$NONCE > ${BenchScripts.DONE}") > sampler)
    }

    @Test
    fun ownLeftoverAutorunIsNotForeign() {
        assertFalse(BenchScripts.isForeignAutorun(null))
        assertFalse(BenchScripts.isForeignAutorun(BenchScripts.start("x", 1)))
        assertFalse(
            BenchScripts.isForeignAutorun(
                "#!/system/bin/sh\nexec /system/bin/sh '${BenchScripts.ARM_BODY}'\n",
            ),
        )
        assertTrue(BenchScripts.isForeignAutorun("#!/system/bin/sh\necho /data/local/tmp/camd-grab.arm\n"))
    }

    @Test
    fun ctlMatchesWhatTheSamplerExpects() {
        assertTrue(BenchScripts.ctlRun("ab").trim() == "run ab")
    }

    @Test
    fun rawTailFeedsOnlyCompleteLines() {
        val file = Files.createTempFile("raw", ".txt").toFile()
        try {
            val seen = ArrayList<String>()
            val tail = RawTail(file, seen::add)
            file.writeText("K 100\nN 2\nT 10")
            assertTrue(tail.poll())
            assertTrue(seen == listOf("K 100", "N 2"))
            assertFalse(tail.poll())
            file.appendText("00 0\nE\n")
            assertTrue(tail.poll())
            assertTrue(seen == listOf("K 100", "N 2", "T 1000 0", "E"))
        } finally {
            File(file.path).delete()
        }
    }
}
