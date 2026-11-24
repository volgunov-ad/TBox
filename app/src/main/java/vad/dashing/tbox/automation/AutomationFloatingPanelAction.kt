package vad.dashing.tbox.automation

import vad.dashing.tbox.ruEn

enum class AutomationFloatingPanelScope {
    ALL,
    SELECTED,
}

enum class AutomationFloatingPanelVisibilityOp {
    TOGGLE,
    HIDE,
    SHOW,
}

enum class AutomationFloatingPanelEnabledOp {
    TOGGLE,
    ENABLE,
    DISABLE,
}

internal const val AUTOMATION_FLOATING_PANEL_OP_TOGGLE = 0
internal const val AUTOMATION_FLOATING_PANEL_OP_ON = 1
internal const val AUTOMATION_FLOATING_PANEL_OP_OFF = 2

fun AutomationAction.Builtin.floatingPanelScope(): AutomationFloatingPanelScope =
    if (stringValue.isBlank()) AutomationFloatingPanelScope.ALL else AutomationFloatingPanelScope.SELECTED

fun AutomationAction.Builtin.floatingPanelId(): String? =
    stringValue.trim().takeIf { it.isNotEmpty() }

fun AutomationAction.Builtin.floatingPanelVisibilityOp(): AutomationFloatingPanelVisibilityOp =
    when (intValue) {
        AUTOMATION_FLOATING_PANEL_OP_ON -> AutomationFloatingPanelVisibilityOp.HIDE
        AUTOMATION_FLOATING_PANEL_OP_OFF -> AutomationFloatingPanelVisibilityOp.SHOW
        else -> AutomationFloatingPanelVisibilityOp.TOGGLE
    }

fun AutomationAction.Builtin.floatingPanelEnabledOp(): AutomationFloatingPanelEnabledOp =
    when (intValue) {
        AUTOMATION_FLOATING_PANEL_OP_ON -> AutomationFloatingPanelEnabledOp.ENABLE
        AUTOMATION_FLOATING_PANEL_OP_OFF -> AutomationFloatingPanelEnabledOp.DISABLE
        else -> AutomationFloatingPanelEnabledOp.TOGGLE
    }

fun floatingPanelVisibilityOpLabel(op: AutomationFloatingPanelVisibilityOp): String = when (op) {
    AutomationFloatingPanelVisibilityOp.TOGGLE -> ruEn("Переключить", "Toggle")
    AutomationFloatingPanelVisibilityOp.HIDE -> ruEn("Скрыть", "Hide")
    AutomationFloatingPanelVisibilityOp.SHOW -> ruEn("Показать", "Show")
}

fun floatingPanelEnabledOpLabel(op: AutomationFloatingPanelEnabledOp): String = when (op) {
    AutomationFloatingPanelEnabledOp.TOGGLE -> ruEn("Переключить", "Toggle")
    AutomationFloatingPanelEnabledOp.ENABLE -> ruEn("Включить", "On")
    AutomationFloatingPanelEnabledOp.DISABLE -> ruEn("Выключить", "Off")
}

fun floatingPanelScopeLabel(scope: AutomationFloatingPanelScope): String = when (scope) {
    AutomationFloatingPanelScope.ALL -> ruEn("Все панели", "All panels")
    AutomationFloatingPanelScope.SELECTED -> ruEn("Выбранная панель", "Selected panel")
}

fun floatingPanelVisibilityOpToInt(op: AutomationFloatingPanelVisibilityOp): Int = when (op) {
    AutomationFloatingPanelVisibilityOp.TOGGLE -> AUTOMATION_FLOATING_PANEL_OP_TOGGLE
    AutomationFloatingPanelVisibilityOp.HIDE -> AUTOMATION_FLOATING_PANEL_OP_ON
    AutomationFloatingPanelVisibilityOp.SHOW -> AUTOMATION_FLOATING_PANEL_OP_OFF
}

fun floatingPanelEnabledOpToInt(op: AutomationFloatingPanelEnabledOp): Int = when (op) {
    AutomationFloatingPanelEnabledOp.TOGGLE -> AUTOMATION_FLOATING_PANEL_OP_TOGGLE
    AutomationFloatingPanelEnabledOp.ENABLE -> AUTOMATION_FLOATING_PANEL_OP_ON
    AutomationFloatingPanelEnabledOp.DISABLE -> AUTOMATION_FLOATING_PANEL_OP_OFF
}

fun floatingPanelVisibilityResultMessage(
    scope: AutomationFloatingPanelScope,
    op: AutomationFloatingPanelVisibilityOp,
): String = when (scope) {
    AutomationFloatingPanelScope.ALL -> when (op) {
        AutomationFloatingPanelVisibilityOp.TOGGLE -> ruEn("Видимость всех плавающих панелей изменена", "Visibility of all floating panels changed")
        AutomationFloatingPanelVisibilityOp.HIDE -> ruEn("Все плавающие панели скрыты", "All floating panels hidden")
        AutomationFloatingPanelVisibilityOp.SHOW -> ruEn("Все плавающие панели показаны", "All floating panels shown")
    }

    AutomationFloatingPanelScope.SELECTED -> when (op) {
        AutomationFloatingPanelVisibilityOp.TOGGLE -> ruEn("Видимость плавающей панели изменена", "Floating panel visibility changed")
        AutomationFloatingPanelVisibilityOp.HIDE -> ruEn("Плавающая панель скрыта", "Floating panel hidden")
        AutomationFloatingPanelVisibilityOp.SHOW -> ruEn("Плавающая панель показана", "Floating panel shown")
    }
}

fun floatingPanelEnabledResultMessage(
    scope: AutomationFloatingPanelScope,
    op: AutomationFloatingPanelEnabledOp,
): String = when (scope) {
    AutomationFloatingPanelScope.ALL -> when (op) {
        AutomationFloatingPanelEnabledOp.TOGGLE -> ruEn("Состояние всех плавающих панелей изменено", "State of all floating panels changed")
        AutomationFloatingPanelEnabledOp.ENABLE -> ruEn("Все плавающие панели включены", "All floating panels on")
        AutomationFloatingPanelEnabledOp.DISABLE -> ruEn("Все плавающие панели выключены", "All floating panels off")
    }

    AutomationFloatingPanelScope.SELECTED -> when (op) {
        AutomationFloatingPanelEnabledOp.TOGGLE -> ruEn("Состояние плавающей панели изменено", "Floating panel state changed")
        AutomationFloatingPanelEnabledOp.ENABLE -> ruEn("Плавающая панель включена", "Floating panel on")
        AutomationFloatingPanelEnabledOp.DISABLE -> ruEn("Плавающая панель выключена", "Floating panel off")
    }
}
