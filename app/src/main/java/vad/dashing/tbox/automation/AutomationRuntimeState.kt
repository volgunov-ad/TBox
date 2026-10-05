package vad.dashing.tbox.automation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class AutomationExecutionState {
    IDLE,
    RUNNING,
    SUCCESS,
    ERROR,
}

data class AutomationRuntimeStatus(
    val state: AutomationExecutionState = AutomationExecutionState.IDLE,
    val activeRuns: Int = 0,
    val lastTriggerId: String? = null,
    val lastStartedAtEpochMillis: Long? = null,
    val lastFinishedAtEpochMillis: Long? = null,
    val lastMessage: String = "",
)

object AutomationRuntimeState {
    private val _statuses = MutableStateFlow<Map<String, AutomationRuntimeStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, AutomationRuntimeStatus>> = _statuses.asStateFlow()

    fun markStarted(automationId: String, triggerId: String, startedAtEpochMillis: Long) {
        _statuses.update { current ->
            val previous = current[automationId] ?: AutomationRuntimeStatus()
            current + (
                automationId to previous.copy(
                    state = AutomationExecutionState.RUNNING,
                    activeRuns = previous.activeRuns + 1,
                    lastTriggerId = triggerId,
                    lastStartedAtEpochMillis = startedAtEpochMillis,
                    lastMessage = "",
                )
            )
        }
    }

    fun markFinished(
        automationId: String,
        success: Boolean,
        message: String,
        finishedAtEpochMillis: Long,
    ) {
        _statuses.update { current ->
            val previous = current[automationId] ?: AutomationRuntimeStatus()
            val remaining = (previous.activeRuns - 1).coerceAtLeast(0)
            current + (
                automationId to previous.copy(
                    state = if (remaining > 0) {
                        AutomationExecutionState.RUNNING
                    } else if (success) {
                        AutomationExecutionState.SUCCESS
                    } else {
                        AutomationExecutionState.ERROR
                    },
                    activeRuns = remaining,
                    lastFinishedAtEpochMillis = finishedAtEpochMillis,
                    lastMessage = message,
                )
            )
        }
    }

    fun retainAutomationIds(ids: Set<String>) {
        _statuses.update { statuses -> statuses.filterKeys { it in ids } }
    }

    /**
     * Surface a rejected launch (validation, cooldown, engine not ready) without
     * changing [AutomationRuntimeStatus.activeRuns] of an in-flight run.
     */
    fun markRejected(automationId: String, message: String, finishedAtEpochMillis: Long) {
        _statuses.update { current ->
            val previous = current[automationId] ?: AutomationRuntimeStatus()
            if (previous.activeRuns > 0) {
                current
            } else {
                current + (
                    automationId to previous.copy(
                        state = AutomationExecutionState.ERROR,
                        lastFinishedAtEpochMillis = finishedAtEpochMillis,
                        lastMessage = message,
                    )
                )
            }
        }
    }

    internal fun resetForTests() {
        _statuses.value = emptyMap()
    }
}
