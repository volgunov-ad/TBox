package vad.dashing.tbox.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbShellScriptParserTest {

    @Test
    fun stripOptionalShellPrefix_adbShellCaseInsensitive() {
        assertEquals("pm list packages", AdbShellScriptParser.stripOptionalShellPrefix("adb shell pm list packages"))
        assertEquals("pm list packages", AdbShellScriptParser.stripOptionalShellPrefix("ADB SHELL pm list packages"))
        assertEquals("pm list packages", AdbShellScriptParser.stripOptionalShellPrefix("  Adb  Shell  pm list packages"))
    }

    @Test
    fun stripOptionalShellPrefix_shellOnly() {
        assertEquals("getprop ro.build.version.release", AdbShellScriptParser.stripOptionalShellPrefix("shell getprop ro.build.version.release"))
        assertEquals("id", AdbShellScriptParser.stripOptionalShellPrefix("SHELL id"))
    }

    @Test
    fun stripOptionalShellPrefix_leavesPlainCommands() {
        assertEquals("pm grant foo bar", AdbShellScriptParser.stripOptionalShellPrefix("pm grant foo bar"))
        assertEquals("settings put global adb_enabled 1", AdbShellScriptParser.stripOptionalShellPrefix("settings put global adb_enabled 1"))
        // "shell" as part of a path / arg must not strip mid-command
        assertEquals("echo shell hello", AdbShellScriptParser.stripOptionalShellPrefix("echo shell hello"))
    }

    @Test
    fun parseExecutableCommands_skipsEmptyAndComments() {
        val text = """
            # header
            pm list packages
            
            # another comment
            shell dumpsys window
            adb shell id

            ## trailing
        """.trimIndent()
        assertEquals(
            listOf("pm list packages", "dumpsys window", "id"),
            AdbShellScriptParser.parseExecutableCommands(text),
        )
    }

    @Test
    fun parseExecutableCommands_normalizesCrlf() {
        val text = "echo a\r\n# skip\r\nshell echo b\r\n"
        assertEquals(listOf("echo a", "echo b"), AdbShellScriptParser.parseExecutableCommands(text))
    }

    @Test
    fun parseExecutableCommands_commentMustStartLineAfterTrim() {
        val text = "echo # not a comment line\n  # real comment\necho ok"
        assertEquals(
            listOf("echo # not a comment line", "echo ok"),
            AdbShellScriptParser.parseExecutableCommands(text),
        )
    }

    @Test
    fun evaluateExecutableCount_thresholds() {
        assertEquals(
            AdbShellScriptParser.CountGate.Ok(0),
            AdbShellScriptParser.evaluateExecutableCount(0),
        )
        assertEquals(
            AdbShellScriptParser.CountGate.Ok(20),
            AdbShellScriptParser.evaluateExecutableCount(20),
        )
        assertEquals(
            AdbShellScriptParser.CountGate.NeedsConfirm(21),
            AdbShellScriptParser.evaluateExecutableCount(21),
        )
        assertEquals(
            AdbShellScriptParser.CountGate.NeedsConfirm(200),
            AdbShellScriptParser.evaluateExecutableCount(200),
        )
        assertEquals(
            AdbShellScriptParser.CountGate.Rejected(201),
            AdbShellScriptParser.evaluateExecutableCount(201),
        )
    }

    @Test
    fun evaluateExecutableCount_constantsMatchProduct() {
        assertEquals(20, AdbShellScriptParser.CONFIRM_THRESHOLD)
        assertEquals(200, AdbShellScriptParser.HARD_CAP)
        assertTrue(AdbShellScriptParser.HARD_CAP > AdbShellScriptParser.CONFIRM_THRESHOLD)
    }

    @Test
    fun normalizeNewlines_crOnly() {
        assertEquals("a\nb\nc", AdbShellScriptParser.normalizeNewlines("a\rb\rc"))
    }
}
