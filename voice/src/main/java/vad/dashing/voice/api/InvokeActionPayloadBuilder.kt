package vad.dashing.voice.api

import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds External API invoke payloads from catalog action types.
 * Param-free builtins / open_main_screen work without slots.
 * can_command and valued builtins (set_media_volume) need [buildCanCommand] /
 * [buildBuiltin] with an absolute value from [VoiceInvokePlanner].
 */
object InvokeActionPayloadBuilder {
    fun build(action: CatalogAction): Result<JSONObject> = runCatching {
        when (action.type) {
            "builtin" -> {
                val actionType = action.actionType?.takeIf { it.isNotBlank() }
                    ?: error("builtin without actionType")
                if (action.write != null && needsValue(action)) {
                    error("Нужно значение для «$actionType» (потеплее / тише / число)")
                }
                builtinJson(actionType, intValue = 0)
            }
            "open_main_screen" -> {
                JSONObject()
                    .put("type", "open_main_screen")
                    .put("page", 1)
                    .put("target", "fullscreen")
            }
            "can_command" -> {
                error("can_command из голоса нужен слот (включи / выключи / потеплее / число)")
            }
            "launch_application", "http_request", "delay" -> {
                error("Действие «${action.type}» из голоса пока не поддерживается")
            }
            else -> error("Неизвестный тип действия: ${action.type}")
        }
    }

    fun buildCanCommand(
        bus: String,
        propertyId: Int,
        operation: String,
        value: Any,
    ): JSONObject =
        JSONObject()
            .put("type", "can_command")
            .put("bus", bus)
            .put("propertyId", propertyId)
            .put("operation", operation)
            .put("value", value)

    fun buildBuiltin(actionType: String, intValue: Int = 0): JSONObject =
        builtinJson(actionType, intValue)

    fun wrapActions(actionJson: JSONObject): String =
        JSONObject().put("actions", JSONArray().put(actionJson)).toString()

    fun needsValue(action: CatalogAction): Boolean {
        if (action.type == "can_command") return true
        return action.type == "builtin" &&
            action.actionType == "set_media_volume" &&
            action.write != null
    }

    private fun builtinJson(actionType: String, intValue: Int): JSONObject =
        JSONObject()
            .put("type", "builtin")
            .put("actionType", actionType)
            .put("intValue", intValue)
            .put("stringValue", "")
            .put("boolValue", false)
}
