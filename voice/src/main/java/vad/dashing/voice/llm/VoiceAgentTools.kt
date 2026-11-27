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
                    description =
                        "Выполнить действие из каталога: builtin, open_main_screen или can_command " +
                            "(только если у действия есть write). Для volume/brightness/wifi/can_command " +
                            "передавай value / intValue / boolValue / delta (±1).",
                    parameters = JSONObject()
                        .put("type", "object")
                        .put(
                            "properties",
                            JSONObject()
                                .put(
                                    "type",
                                    JSONObject()
                                        .put("type", "string")
                                        .put(
                                            "description",
                                            "builtin | open_main_screen | can_command",
                                        ),
                                )
                                .put(
                                    "actionType",
                                    JSONObject()
                                        .put("type", "string")
                                        .put(
                                            "description",
                                            "Для builtin: media_next, set_media_volume, …",
                                        ),
                                )
                                .put(
                                    "bus",
                                    JSONObject()
                                        .put("type", "string")
                                        .put("description", "Для can_command: vehicle | audio"),
                                )
                                .put(
                                    "propertyId",
                                    JSONObject()
                                        .put("type", "integer")
                                        .put("description", "Для can_command: propertyId из каталога"),
                                )
                                .put(
                                    "signalId",
                                    JSONObject()
                                        .put("type", "string")
                                        .put(
                                            "description",
                                            "Опционально: signalId связанного can_command / builtin",
                                        ),
                                )
                                .put(
                                    "operation",
                                    JSONObject()
                                        .put("type", "string")
                                        .put("description", "set | toggle | trunk_pulse"),
                                )
                                .put(
                                    "value",
                                    JSONObject()
                                        .put(
                                            "description",
                                            "Абсолют для can_command: on/off или число/ключ из write.options",
                                        ),
                                )
                                .put(
                                    "intValue",
                                    JSONObject()
                                        .put("type", "integer")
                                        .put("description", "Для builtin volume/brightness/cruise"),
                                )
                                .put(
                                    "boolValue",
                                    JSONObject()
                                        .put("type", "boolean")
                                        .put("description", "Для wifi_set_enabled"),
                                )
                                .put(
                                    "delta",
                                    JSONObject()
                                        .put("type", "integer")
                                        .put(
                                            "description",
                                            "Относительный шаг ±1 (потеплее/тише/ярче/круиз плюс)",
                                        ),
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

    private val baseSystemPrompt: String =
        """
        Ты голосовой ассистент автомобиля Jetour (TBox / VAD Voice).
        Когда намерение ясно и это про машину/каталог — вызывай tool. Не выдумывай id сигналов, действий и правил — бери их из каталога.
        Если фраза неоднозначна (несколько подходящих id) — не вызывай tool, задай один короткий уточняющий вопрос по-русски (с «?»): какой сигнал / какое правило / какое действие.
        Для can_command вызывай invoke_action только если у действия в каталоге есть write; передавай value или delta.
        Нельзя: http, adb, произвольный код. Tools только query_signal / invoke_action / run_automation.
        """.trimIndent()

    private val freeformAppendix: String =
        """
        
        Режим свободных ответов включён: если вопрос не про автомобиль и каталог (история, факты, объяснения) — ответь обычным текстом по-русски без tools.
        Ответ должен быть удобен для озвучки: связный рассказ, без markdown/списков со звёздочками, примерно до 1500–2500 знаков (можно чуть длиннее, если явно просят «подробно» / «расскажи»).
        """.trimIndent()

    private val refuseAppendix: String =
        """
        
        Если нельзя помочь через tools — одна короткая фраза отказа без вопроса (свободные длинные ответы выключены).
        """.trimIndent()

    fun systemPrompt(allowFreeformSpeak: Boolean): String =
        baseSystemPrompt + if (allowFreeformSpeak) freeformAppendix else refuseAppendix

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
