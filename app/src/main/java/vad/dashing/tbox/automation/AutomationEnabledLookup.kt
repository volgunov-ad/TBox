package vad.dashing.tbox.automation

/**
 * Snapshot of automation `enabled` flags for condition evaluation.
 *
 * Updated by [AutomationEngine] whenever the store document reloads. Unknown ids are treated
 * as disabled (same as [AutomationTriggerWidgetState] for missing trigger ids).
 *
 * Mutual enable/disable loops between rules are the user's responsibility; the action
 * no-ops when the target is already in the requested state.
 */
object AutomationEnabledLookup {
    @Volatile
    private var enabledById: Map<String, Boolean> = emptyMap()

    fun replaceAll(automations: Collection<AutomationDefinition>) {
        enabledById = automations.associate { it.id to it.enabled }
    }

    fun isEnabled(automationId: String): Boolean {
        val id = automationId.trim()
        return id.isNotEmpty() && enabledById[id] == true
    }

    fun contains(automationId: String): Boolean {
        val id = automationId.trim()
        return id.isNotEmpty() && id in enabledById
    }

    internal fun resetForTests() {
        enabledById = emptyMap()
    }
}
