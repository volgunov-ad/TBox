package vad.dashing.tbox.externalapi

import vad.dashing.tbox.automation.AutomationAction
import vad.dashing.tbox.automation.AutomationBuiltinActionType

enum class ExternalApiActionSafety(val storageKey: String) {
    SAFE("safe"),
    CONFIRM("confirm"),
    DANGEROUS("dangerous"),
}

object ExternalApiActionSafetyRules {
    fun safetyOf(action: AutomationAction): ExternalApiActionSafety = when (action) {
        is AutomationAction.Builtin -> builtinSafety(action.type)
        is AutomationAction.CanCommand -> ExternalApiActionSafety.CONFIRM
        is AutomationAction.LaunchApplication -> ExternalApiActionSafety.CONFIRM
        is AutomationAction.OpenMainScreen -> ExternalApiActionSafety.SAFE
        is AutomationAction.HttpRequest -> ExternalApiActionSafety.CONFIRM
        is AutomationAction.Delay -> ExternalApiActionSafety.SAFE
        is AutomationAction.IfThenElse -> ExternalApiActionSafety.SAFE
    }

    fun isDangerous(action: AutomationAction): Boolean =
        safetyOf(action) == ExternalApiActionSafety.DANGEROUS

    fun builtinSafety(type: AutomationBuiltinActionType): ExternalApiActionSafety = when (type) {
        AutomationBuiltinActionType.ADB_SET_TCP,
        AutomationBuiltinActionType.ADB_SHELL,
        AutomationBuiltinActionType.ADB_FORCE_STOP,
        -> ExternalApiActionSafety.DANGEROUS

        AutomationBuiltinActionType.RESTART_TBOX,
        AutomationBuiltinActionType.WIFI_SET_ENABLED,
        AutomationBuiltinActionType.WIFI_DISCONNECT,
        AutomationBuiltinActionType.WIFI_MODEM_REBOOT,
        AutomationBuiltinActionType.GNSS_MODULE_REBOOT,
        AutomationBuiltinActionType.CRUISE_ENGAGE_TO_TARGET,
        AutomationBuiltinActionType.CRUISE_PAUSE,
        AutomationBuiltinActionType.CRUISE_FULL_OFF,
        AutomationBuiltinActionType.CRUISE_RESUME,
        AutomationBuiltinActionType.CRUISE_ACTIVATE_AT_CURRENT_SPEED,
        AutomationBuiltinActionType.CRUISE_NUDGE,
        AutomationBuiltinActionType.SHOW_ALERT,
        AutomationBuiltinActionType.SET_AUTOMATION_ENABLED,
        AutomationBuiltinActionType.SET_AUTOMATION_TRIGGER_WIDGET,
        -> ExternalApiActionSafety.CONFIRM

        else -> ExternalApiActionSafety.SAFE
    }
}
