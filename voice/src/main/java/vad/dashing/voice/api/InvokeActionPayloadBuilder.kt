package vad.dashing.voice.api

import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds External API invoke payloads from catalog action types.
 * MVP: builtins (no params / empty media package) and open_main_screen.
 * can_command needs operation+value — left for a later relative-adjust stage.
 */
object InvokeActionPayloadBuilder {
    fun build(action: CatalogAction): Result<JSONObject> = runCatching {
        when (action.type) {
            "builtin" -> {
                val actionType = action.actionType?.takeIf { it.isNotBlank() }
                    ?: error("builtin without actionType")
                JSONObject()
                    .put("type", "builtin")
                    .put("actionType", actionType)
                    .put("intValue", 0)
                    .put("stringValue", "")
                    .put("boolValue", false)
            }
            "open_main_screen" -> {
                JSONObject()
                    .put("type", "open_main_screen")
                    .put("page", 1)
                    .put("target", "fullscreen")
            }
            "can_command" -> {
                error("can_command из голоса пока без значения — используйте правило или текст с абсолютом")
            }
            "launch_application", "http_request", "delay" -> {
                error("Действие «${action.type}» из голоса пока не поддерживается")
            }
            else -> error("Неизвестный тип действия: ${action.type}")
        }
    }

    fun wrapActions(actionJson: JSONObject): String =
        JSONObject().put("actions", JSONArray().put(actionJson)).toString()
}
