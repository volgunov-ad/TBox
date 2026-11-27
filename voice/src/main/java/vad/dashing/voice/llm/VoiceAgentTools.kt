package vad.dashing.voice.llm

import org.json.JSONArray
import org.json.JSONObject

object VoiceAgentTools {
    const val QUERY_SIGNAL = "query_signal"
    const val INVOKE_ACTION = "invoke_action"
    const val RUN_AUTOMATION = "run_automation"

    fun definitions(): JSONArray =
        JSONArray()
            .put(
                functionTool(
                    name = QUERY_SIGNAL,
                    description = "Прочитать значение сигнала телеметрии автомобиля по id из каталога.",
                    parameters = JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject().put(
                                "signalId",
                                JSONObject()
                                    .put("type", "string")
                                    .put("description", "id сигнала из каталога, например outside_temperature"),
                            ),
                        )
                        .put("required", JSONArray().put("signalId")),
                ),
            )
            .put(
                functionTool(
                    name = INVOKE_ACTION,
                    description = "Выполнить действие из каталога. Только builtin (actionType) или open_main_screen.",
                    parameters = JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put(
                                    "type",
                                    JSONObject()
                                        .put("type", "string")
                                        .put("description", "builtin или open_main_screen"),
                                )
                                .put(
                                    "actionType",
                                    JSONObject()
                                        .put("type", "string")
                                        .put("description", "Для builtin: media_next, media_play, …"),
                                ),
                        )
                        .put("required", JSONArray().put("type")),
                ),
            )
            .put(
                functionTool(
                    name = RUN_AUTOMATION,
                    description = "Запустить правило автоматизации по id или точному имени.",
                    parameters = JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put(
                                    "automationId",
                                    JSONObject().put("type", "string").put("description", "UUID правила"),
                                )
                                .put(
                                    "name",
                                    JSONObject().put("type", "string").put("description", "Имя правила"),
                                ),
                        ),
                ),
            )

    val systemPrompt: String =
        """
        Ты голосовой ассистент автомобиля Jetour (TBox / VAD Voice).
        Отвечай только через tools. Не выдумывай id сигналов, действий и правил — бери их из каталога.
        Если фраза неоднозначна или id нет в каталоге — не вызывай tool, ответь коротко по-русски одной фразой.
        Нельзя: can_command, http, adb, произвольный код. Только query_signal / invoke_action / run_automation.
        """.trimIndent()

    private fun functionTool(name: String, description: String, parameters: JSONObject): JSONObject =
        JSONObject()
            .put("type", "function")
            .put(
                "function",
                JSONObject()
                    .put("name", name)
                    .put("description", description)
                    .put("parameters", parameters),
            )
}
