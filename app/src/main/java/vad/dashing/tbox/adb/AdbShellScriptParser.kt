package vad.dashing.tbox.adb

/**
 * Pure helpers for ADB-tab “run shell commands from a text file”.
 *
 * Lines are shell commands only (same path as the manual shell field). Optional
 * `adb shell` / `shell` prefixes are stripped; empty lines and `#` comments are skipped.
 */
object AdbShellScriptParser {
    const val CONFIRM_THRESHOLD = 20
    const val HARD_CAP = 200

    private val ADB_SHELL_PREFIX =
        Regex("^adb\\s+shell\\s+", RegexOption.IGNORE_CASE)
    private val SHELL_PREFIX =
        Regex("^shell\\s+", RegexOption.IGNORE_CASE)

    /**
     * Normalize CRLF/CR to LF, then return executable shell commands in order
     * (prefixes stripped; empty and `#` comment lines dropped).
     */
    fun parseExecutableCommands(fileText: String): List<String> {
        val normalized = normalizeNewlines(fileText)
        if (normalized.isEmpty()) return emptyList()
        return normalized.lineSequence()
            .mapNotNull { line ->
                val trimmed = line.trim()
                when {
                    trimmed.isEmpty() -> null
                    trimmed.startsWith("#") -> null
                    else -> stripOptionalShellPrefix(trimmed).trim().takeIf { it.isNotEmpty() }
                }
            }
            .toList()
    }

    /** Case-insensitive strip of a leading `adb shell` or `shell` token group. */
    fun stripOptionalShellPrefix(line: String): String {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return trimmed
        ADB_SHELL_PREFIX.find(trimmed)?.let { match ->
            return trimmed.substring(match.range.last + 1).trimStart()
        }
        SHELL_PREFIX.find(trimmed)?.let { match ->
            return trimmed.substring(match.range.last + 1).trimStart()
        }
        return trimmed
    }

    fun normalizeNewlines(text: String): String =
        text.replace("\r\n", "\n").replace('\r', '\n')

    sealed class CountGate {
        data class Ok(val count: Int) : CountGate()
        data class NeedsConfirm(val count: Int) : CountGate()
        data class Rejected(val count: Int) : CountGate()
    }

    /**
     * Soft caps for a parsed executable list:
     * - `> [HARD_CAP]` → refuse
     * - `> [CONFIRM_THRESHOLD]` → confirm before start
     * - otherwise OK (including 0; UI may toast separately)
     */
    fun evaluateExecutableCount(count: Int): CountGate = when {
        count > HARD_CAP -> CountGate.Rejected(count)
        count > CONFIRM_THRESHOLD -> CountGate.NeedsConfirm(count)
        else -> CountGate.Ok(count)
    }
}
