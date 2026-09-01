package vad.dashing.tbox.automation

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
    AutomationFloatingPanelVisibilityOp.TOGGLE -> "Переключить"
    AutomationFloatingPanelVisibilityOp.HIDE -> "Скрыть"
    AutomationFloatingPanelVisibilityOp.SHOW -> "Показать"
}

fun floatingPanelEnabledOpLabel(op: AutomationFloatingPanelEnabledOp): String = when (op) {
    AutomationFloatingPanelEnabledOp.TOGGLE -> "Переключить"
    AutomationFloatingPanelEnabledOp.ENABLE -> "Включить"
    AutomationFloatingPanelEnabledOp.DISABLE -> "Выключить"
}

fun floatingPanelScopeLabel(scope: AutomationFloatingPanelScope): String = when (scope) {
    AutomationFloatingPanelScope.ALL -> "Все панели"
    AutomationFloatingPanelScope.SELECTED -> "Выбранная панель"
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
        AutomationFloatingPanelVisibilityOp.TOGGLE -> "Видимость всех плавающих панелей изменена"
        AutomationFloatingPanelVisibilityOp.HIDE -> "Все плавающие панели скрыты"
        AutomationFloatingPanelVisibilityOp.SHOW -> "Все плавающие панели показаны"
    }

    AutomationFloatingPanelScope.SELECTED -> when (op) {
        AutomationFloatingPanelVisibilityOp.TOGGLE -> "Видимость плавающей панели изменена"
        AutomationFloatingPanelVisibilityOp.HIDE -> "Плавающая панель скрыта"
        AutomationFloatingPanelVisibilityOp.SHOW -> "Плавающая панель показана"
    }
}

fun floatingPanelEnabledResultMessage(
    scope: AutomationFloatingPanelScope,
    op: AutomationFloatingPanelEnabledOp,
): String = when (scope) {
    AutomationFloatingPanelScope.ALL -> when (op) {
        AutomationFloatingPanelEnabledOp.TOGGLE -> "Состояние всех плавающих панелей изменено"
        AutomationFloatingPanelEnabledOp.ENABLE -> "Все плавающие панели включены"
        AutomationFloatingPanelEnabledOp.DISABLE -> "Все плавающие панели выключены"
    }

    AutomationFloatingPanelScope.SELECTED -> when (op) {
        AutomationFloatingPanelEnabledOp.TOGGLE -> "Состояние плавающей панели изменено"
        AutomationFloatingPanelEnabledOp.ENABLE -> "Плавающая панель включена"
        AutomationFloatingPanelEnabledOp.DISABLE -> "Плавающая панель выключена"
    }
}
