package vad.dashing.tbox.automation

import vad.dashing.tbox.ThemeBundleExport
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AutomationExport {
    const val FILE_EXTENSION = "json"

    fun withUniqueIds(
        incoming: List<AutomationDefinition>,
        existingIds: Set<String>,
    ): List<AutomationDefinition> {
        val used = existingIds.toMutableSet()
        return incoming.map { definition ->
            if (definition.id in used) {
                generateSequence { newAutomationNodeId() }
                    .first { it !in used }
                    .let { nextId ->
                        used += nextId
                        definition.copy(id = nextId)
                    }
            } else {
                used += definition.id
                definition
            }
        }
    }

    fun fileName(definition: AutomationDefinition, timestampMs: Long = System.currentTimeMillis()): String {
        val slug = sanitizeBaseName(definition.name) ?: "automation"
        val ts = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date(timestampMs))
        return "tbox_automation_${slug}_$ts.$FILE_EXTENSION"
    }

    fun writeToDownloads(definition: AutomationDefinition): File {
        val dest = File(ThemeBundleExport.downloadsDir(), fileName(definition))
        dest.writeText(AutomationCodec.encodeDefinitionDocument(definition, pretty = true))
        return dest
    }

    internal fun sanitizeBaseName(input: String): String? {
        var name = input.trim()
        if (name.endsWith(".$FILE_EXTENSION", ignoreCase = true)) {
            name = name.dropLast(FILE_EXTENSION.length + 1).trim()
        }
        name = name.replace(Regex("""[\\/:*?"<>|\s]+"""), "_").trim('_', '.')
        if (name.isBlank() || name == "." || name == "..") return null
        if (name.length > 80) name = name.take(80)
        return name
    }
}
