package vad.dashing.dataresize

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** The script runs only on the head unit; these checks pin its safety guards. */
class DataResizeScriptTest {
    private val script = File("src/main/assets/dataresize.sh").readText().replace("\r\n", "\n")

    private fun body(mode: String): String = script.substringAfter("\n$mode() {\n").substringBefore("\n}\n")

    @Test
    fun move_refusesOverlappingTargetBeforeStoppingAndroid() {
        val move = body("move")
        val guard = move.indexOf("[ \"\$NEW_FIRST\" -gt \$((OLD_FIRST + OLD_SECT - 1)) ] || fail")
        assertTrue(guard > 0)
        assertTrue(guard < move.indexOf("stop; sleep"))
        assertTrue(guard < move.indexOf("dd if=\$UD of=\$D"))
    }

    @Test
    fun runs_takeLockBeforeTruncatingJournal() {
        assertTrue(script.contains("move|stage2|rollback|fixsgpt) lock \$1; \$1 ;;"))
        assertTrue(script.contains("mkdir \$LOCK"))
        assertTrue(script.contains("running) "))
        for (mode in listOf("move", "stage2", "rollback", "fixsgpt")) {
            assertTrue(mode, body(mode).trimStart().startsWith("LOG=\$DIR/$mode.log; : > \$LOG"))
        }
    }

    @Test
    fun rollback_restoresNewTablesWhenOldOnesFail() {
        val rollback = body("rollback")
        assertTrue(rollback.indexOf("new_sgpt.bin") > rollback.indexOf("old_pgpt.bin"))
        assertTrue(rollback.indexOf("new_pgpt.bin") > rollback.indexOf("new_sgpt.bin"))
    }

    @Test
    fun fixsgpt_onlyForCopiesOfKnownTables() {
        assertTrue(body("fixsgpt").contains("preflight mixed_old mixed_new"))
        assertTrue(script.contains("\"\$MD5_OLD_P:\$MD5_NEW_S\") echo mixed_old ;;"))
        assertTrue(script.contains("\"\$MD5_NEW_P:\$MD5_OLD_S\") echo mixed_new ;;"))
    }
}
