package vad.dashing.tbox.automation

/**
 * Manual «run now» uses the first trigger id so [AutomationCondition.TriggeredBy]
 * inside IfThenElse can still match. Top-level conditions are skipped by the engine.
 */
internal object AutomationRunNow {
    const val FALLBACK_TRIGGER_ID = "run_now"

    fun triggerId(definition: AutomationDefinition): String =
        definition.triggers.firstOrNull()?.id?.takeIf { it.isNotBlank() } ?: FALLBACK_TRIGGER_ID

    fun rejection(definition: AutomationDefinition?): String? {
        if (definition == null) {
            return "Автоматизация не найдена. Сначала сохраните правило."
        }
        val issues = AutomationValidator.validate(definition)
        if (issues.isNotEmpty()) {
            return issues.joinToString("; ") { it.message }
        }
        return null
    }
}
