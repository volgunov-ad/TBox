package vad.dashing.tbox.adb

/**
 * Shared interpretation of [AdbShellResult] for localhost automation
 * (permission grants, virtual-display launch, etc.).
 */
internal object AdbShellResults {
    /**
     * Non-null detail when the shell command should be treated as failed:
     * non-zero exit code, or legacy shell output containing Error:/Exception.
     */
    fun failureDetail(result: AdbShellResult): String? {
        if (result.exitCode != null && result.exitCode != 0) {
            return listOf(result.stderr, result.stdout)
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
                .ifBlank { "exit ${result.exitCode}" }
        }
        val combined = "${result.stderr}\n${result.stdout}"
        if (combined.contains("Error:", ignoreCase = true) ||
            combined.contains("Exception", ignoreCase = true)
        ) {
            return combined.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() }
                .orEmpty()
                .ifBlank { null }
        }
        return null
    }

    fun isFailure(result: AdbShellResult): Boolean = failureDetail(result) != null
}
