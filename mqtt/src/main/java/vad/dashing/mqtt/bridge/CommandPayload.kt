package vad.dashing.mqtt.bridge

import org.json.JSONArray
import org.json.JSONObject
import vad.dashing.mqtt.ha.CatalogEntity
import vad.dashing.mqtt.ha.WritePair
import kotlin.math.abs

sealed class InvokeRequest {
    data class Actions(val json: String) : InvokeRequest()
    data class RunAutomation(val id: String) : InvokeRequest()
}

data class CommandResult(
    val request: InvokeRequest,
    val publishedState: String?,
)

fun commandToInvoke(entity: CatalogEntity, payload: String): CommandResult? {
    val text = payload.trim()
    if (text.isEmpty()) return null
    entity.automationId?.let { id ->
        if (!text.equals("PRESS", ignoreCase = true)) return null
        return CommandResult(InvokeRequest.RunAutomation(id), publishedState = null)
    }
    entity.builtinAction?.let { action ->
        if (!text.equals("PRESS", ignoreCase = true)) return null
        return CommandResult(InvokeRequest.Actions(builtinBody(action)), publishedState = null)
    }
    val command = entity.canCommand ?: return null
    if (entity.isButton) {
        if (!text.equals("PRESS", ignoreCase = true)) return null
        val value = command.fixedText ?: command.fixedNumber ?: return null
        return CommandResult(
            InvokeRequest.Actions(canBody(command.bus, command.propertyId, command.operation, value)),
            publishedState = null,
        )
    }
    return when (entity.component) {
        vad.dashing.mqtt.ha.HaComponent.SWITCH -> switchInvoke(entity, command, text)
        vad.dashing.mqtt.ha.HaComponent.SELECT -> selectInvoke(entity, command, text)
        vad.dashing.mqtt.ha.HaComponent.NUMBER -> numberInvoke(entity, command, text)
        else -> null
    }
}

private fun switchInvoke(
    entity: CatalogEntity,
    command: vad.dashing.mqtt.ha.CanCommandBinding,
    text: String,
): CommandResult? {
    val state = when {
        text.equals("ON", ignoreCase = true) -> "on"
        text.equals("OFF", ignoreCase = true) -> "off"
        else -> return null
    }
    val pair = entity.pairs.firstOrNull { it.state == state } ?: return null
    val value = pair.textValue ?: pair.numberValue ?: return null
    return CommandResult(
        InvokeRequest.Actions(canBody(command.bus, command.propertyId, "set", value)),
        publishedState = if (state == "on") "ON" else "OFF",
    )
}

private fun selectInvoke(
    entity: CatalogEntity,
    command: vad.dashing.mqtt.ha.CanCommandBinding,
    text: String,
): CommandResult? {
    val pair = entity.pairs.firstOrNull { it.state == text } ?: return null
    val value = pair.textValue ?: pair.numberValue ?: return null
    return CommandResult(
        InvokeRequest.Actions(canBody(command.bus, command.propertyId, "set", value)),
        publishedState = pair.state,
    )
}

private fun numberInvoke(
    entity: CatalogEntity,
    command: vad.dashing.mqtt.ha.CanCommandBinding,
    text: String,
): CommandResult? {
    val number = text.toDoubleOrNull() ?: return null
    val min = entity.numberMin
    val max = entity.numberMax
    if (min != null && number < min - 0.0001) return null
    if (max != null && number > max + 0.0001) return null
    val pair = nearestPair(entity.pairs, number) ?: return null
    val value = pair.numberValue ?: pair.textValue ?: return null
    return CommandResult(
        InvokeRequest.Actions(canBody(command.bus, command.propertyId, "set", value)),
        publishedState = pair.state,
    )
}

private fun nearestPair(pairs: List<WritePair>, number: Double): WritePair? {
    return pairs
        .mapNotNull { pair ->
            val stateNumber = pair.state.toDoubleOrNull() ?: return@mapNotNull null
            pair to abs(stateNumber - number)
        }
        .minByOrNull { it.second }
        ?.first
}

private fun canBody(bus: String, propertyId: Int, operation: String, value: Any): String {
    val action = JSONObject()
        .put("type", "can_command")
        .put("bus", bus)
        .put("propertyId", propertyId)
        .put("operation", operation)
        .put("value", value)
    return JSONObject().put("actions", JSONArray().put(action)).toString()
}

private fun builtinBody(actionType: String): String {
    val action = JSONObject()
        .put("type", "builtin")
        .put("actionType", actionType)
        .put("intValue", 0)
        .put("stringValue", "")
        .put("boolValue", false)
    return JSONObject().put("actions", JSONArray().put(action)).toString()
}
