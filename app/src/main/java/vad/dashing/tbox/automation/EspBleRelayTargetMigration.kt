package vad.dashing.tbox.automation

import vad.dashing.tbox.FloatingDashboardConfig
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.MainScreenPanelConfig
import vad.dashing.tbox.esp.EspBleRelayChannel
import vad.dashing.tbox.esp.EspBleRelayIds
import vad.dashing.tbox.esp.espBleRelayLegacyIndex
import vad.dashing.tbox.esp.isEspBleRelayWidgetDataKey

/**
 * Fills a blank board id from the slot the old config used, once, while that
 * board is still at that index. After the id is stored, Forget no longer
 * retargets the tile or the rule.
 */
internal object EspBleRelayTargetMigration {
    fun widget(
        config: FloatingDashboardWidgetConfig,
        relayIds: List<String>,
    ): FloatingDashboardWidgetConfig {
        if (!isEspBleRelayWidgetDataKey(config.dataKey)) return config
        val channel = storedChannel(config.espBleRelayChannel)
        val existing = EspBleRelayIds.normalize(config.espBleRelayId)
        val resolved = existing.ifEmpty {
            val index = espBleRelayLegacyIndex(config.dataKey) ?: return config
            idAt(relayIds, index)
        }
        if (resolved == config.espBleRelayId && channel == config.espBleRelayChannel) return config
        return config.copy(espBleRelayId = resolved, espBleRelayChannel = channel)
    }

    fun widgets(
        configs: List<FloatingDashboardWidgetConfig>,
        relayIds: List<String>,
    ): List<FloatingDashboardWidgetConfig> =
        configs.map { widget(it, relayIds) }.preserving(configs)

    fun floatingDashboards(
        panels: List<FloatingDashboardConfig>,
        relayIds: List<String>,
    ): List<FloatingDashboardConfig> = panels.map { panel ->
        val widgets = widgets(panel.widgetsConfig, relayIds)
        if (widgets === panel.widgetsConfig) panel else panel.copy(widgetsConfig = widgets)
    }.preserving(panels)

    fun mainScreenDashboards(
        panels: List<MainScreenPanelConfig>,
        relayIds: List<String>,
    ): List<MainScreenPanelConfig> = panels.map { panel ->
        val widgets = widgets(panel.widgetsConfig, relayIds)
        if (widgets === panel.widgetsConfig) panel else panel.copy(widgetsConfig = widgets)
    }.preserving(panels)

    fun document(document: AutomationDocument, relayIds: List<String>): AutomationDocument {
        val automations = document.automations.map { definition ->
            val triggers = definition.triggers.map { migrateTrigger(it, relayIds) }.preserving(definition.triggers)
            val conditions = definition.conditions.map { migrateCondition(it, relayIds) }
                .preserving(definition.conditions)
            val actions = definition.actions.map { migrateAction(it, relayIds) }.preserving(definition.actions)
            if (
                triggers === definition.triggers &&
                conditions === definition.conditions &&
                actions === definition.actions
            ) {
                definition
            } else {
                definition.copy(triggers = triggers, conditions = conditions, actions = actions)
            }
        }.preserving(document.automations)
        return if (automations === document.automations) {
            document
        } else {
            document.copy(automations = automations)
        }
    }

    private fun migrateTrigger(
        trigger: AutomationTrigger,
        relayIds: List<String>,
    ): AutomationTrigger = when (trigger) {
        is AutomationTrigger.StateEquals -> {
            if (!isBleRelaySignal(trigger.signal)) trigger
            else {
                val id = resolveSignalId(trigger.signal, trigger.relayId, relayIds)
                val channel = storedChannel(trigger.relayChannel)
                if (id == trigger.relayId && channel == trigger.relayChannel) trigger
                else trigger.copy(relayId = id, relayChannel = channel)
            }
        }
        else -> trigger
    }

    private fun migrateCondition(
        condition: AutomationCondition,
        relayIds: List<String>,
    ): AutomationCondition = when (condition) {
        is AutomationCondition.State -> {
            if (!isBleRelaySignal(condition.signal)) condition
            else {
                val id = resolveSignalId(condition.signal, condition.relayId, relayIds)
                val channel = storedChannel(condition.relayChannel)
                if (id == condition.relayId && channel == condition.relayChannel) condition
                else condition.copy(relayId = id, relayChannel = channel)
            }
        }
        is AutomationCondition.All -> {
            val nested = condition.conditions.map { migrateCondition(it, relayIds) }
                .preserving(condition.conditions)
            if (nested === condition.conditions) condition else condition.copy(conditions = nested)
        }
        is AutomationCondition.Any -> {
            val nested = condition.conditions.map { migrateCondition(it, relayIds) }
                .preserving(condition.conditions)
            if (nested === condition.conditions) condition else condition.copy(conditions = nested)
        }
        is AutomationCondition.Not -> {
            val nested = migrateCondition(condition.condition, relayIds)
            if (nested === condition.condition) condition else condition.copy(condition = nested)
        }
        else -> condition
    }

    private fun migrateAction(
        action: AutomationAction,
        relayIds: List<String>,
    ): AutomationAction = when (action) {
        is AutomationAction.Builtin -> migrateBuiltin(action, relayIds)
        is AutomationAction.IfThenElse -> {
            val condition = migrateCondition(action.condition, relayIds)
            val thenActions = action.thenActions.map { migrateAction(it, relayIds) }
                .preserving(action.thenActions)
            val elseActions = action.elseActions.map { migrateAction(it, relayIds) }
                .preserving(action.elseActions)
            if (
                condition === action.condition &&
                thenActions === action.thenActions &&
                elseActions === action.elseActions
            ) {
                action
            } else {
                action.copy(
                    condition = condition,
                    thenActions = thenActions,
                    elseActions = elseActions,
                )
            }
        }
        else -> action
    }

    private fun migrateBuiltin(
        action: AutomationAction.Builtin,
        relayIds: List<String>,
    ): AutomationAction.Builtin {
        if (!isBleRelayAction(action.type)) return action
        val existing = EspBleRelayActionEncoding.idOf(action)
        val id = existing.ifEmpty { idAt(relayIds, action.intValue) }
        if (id.isEmpty()) return action
        val channel = EspBleRelayActionEncoding.channelField(action)
            ?.takeIf(EspBleRelayChannel::inRange)
            ?: EspBleRelayChannel.DEFAULT
        val packed = EspBleRelayActionEncoding.pack(
            action.type,
            id,
            channel,
            EspBleRelayActionEncoding.pulseText(action),
        )
        return if (packed == action.stringValue) action else action.copy(stringValue = packed)
    }

    private fun resolveSignalId(
        signal: AutomationSignalId,
        relayId: String,
        relayIds: List<String>,
    ): String {
        val existing = EspBleRelayIds.normalize(relayId)
        if (existing.isNotEmpty()) return existing
        val index = when (signal) {
            AutomationSignalId.ESP_BLE_RELAY_0 -> 0
            AutomationSignalId.ESP_BLE_RELAY_1 -> 1
            else -> return ""
        }
        return idAt(relayIds, index)
    }

    private fun idAt(relayIds: List<String>, index: Int): String =
        EspBleRelayIds.normalize(relayIds.getOrNull(index).orEmpty())

    private fun storedChannel(channel: Int): Int =
        if (EspBleRelayChannel.inRange(channel)) channel else EspBleRelayChannel.DEFAULT
}

private fun <T> List<T>.preserving(original: List<T>): List<T> =
    if (size == original.size && indices.all { this[it] === original[it] }) original else this

internal fun isBleRelaySignal(signal: AutomationSignalId): Boolean =
    signal == AutomationSignalId.ESP_BLE_RELAY_0 || signal == AutomationSignalId.ESP_BLE_RELAY_1

internal fun isBleRelayAction(type: AutomationBuiltinActionType): Boolean =
    type == AutomationBuiltinActionType.ESP_BLE_RELAY_SET ||
        type == AutomationBuiltinActionType.ESP_BLE_RELAY_TOGGLE ||
        type == AutomationBuiltinActionType.ESP_BLE_RELAY_PULSE
