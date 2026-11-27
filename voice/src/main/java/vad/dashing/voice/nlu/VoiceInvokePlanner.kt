package vad.dashing.voice.nlu

import org.json.JSONObject
import vad.dashing.voice.api.ApiCatalog
import vad.dashing.voice.api.CatalogAction
import vad.dashing.voice.api.CatalogWriteSchema
import vad.dashing.voice.api.InvokeActionPayloadBuilder
import vad.dashing.voice.api.SignalReading

/**
 * Turns a matched [CatalogAction] + spoken phrase into an invoke payload,
 * optionally after reading the linked signal (relative adjust).
 */
object VoiceInvokePlanner {
    sealed class Plan {
        data class Immediate(val payload: JSONObject) : Plan()

        data class NeedsSignal(
            val signalId: String,
            val apply: (SignalReading) -> Result<JSONObject>,
        ) : Plan()

        data class Fail(val message: String) : Plan()
    }

    fun plan(phrase: String, action: CatalogAction, catalog: ApiCatalog): Plan {
        if (!InvokeActionPayloadBuilder.needsValue(action)) {
            return InvokeActionPayloadBuilder.build(action).fold(
                onSuccess = { Plan.Immediate(it) },
                onFailure = { Plan.Fail(it.message ?: "Нельзя вызвать действие") },
            )
        }

        val write = action.write
            ?: return Plan.Fail("У действия нет схемы записи")
        val slot = VoiceSlotParser.parse(phrase)

        return when (write.kind) {
            CatalogWriteSchema.KIND_BINARY -> planBinary(action, write, slot)
            CatalogWriteSchema.KIND_PULSE -> planPulse(action, write, slot)
            CatalogWriteSchema.KIND_NUMBER, CatalogWriteSchema.KIND_OPTIONS ->
                planNumberOrOptions(action, write, slot, catalog)
            else -> Plan.Fail("Неизвестный kind записи: ${write.kind}")
        }
    }

    fun linkedSignalId(action: CatalogAction): String? {
        action.signalId?.takeIf { it.isNotBlank() }?.let { return it }
        if (action.type == "builtin" && action.actionType == "set_media_volume") {
            return "hu_media_volume"
        }
        return null
    }

    private fun planBinary(
        action: CatalogAction,
        write: CatalogWriteSchema,
        slot: VoiceSlotParser.Slot,
    ): Plan {
        val on = when (slot) {
            is VoiceSlotParser.Slot.Binary -> slot.on
            else -> return Plan.Fail("Скажите «включи» или «выключи»")
        }
        val solved = RelativeAdjustSolver.binary(on, write)
            ?: return Plan.Fail("Нет значения on/off в каталоге")
        return canPayload(action, operation = "set", value = solved.invokeValue)
    }

    private fun planPulse(
        action: CatalogAction,
        write: CatalogWriteSchema,
        slot: VoiceSlotParser.Slot,
    ): Plan {
        val open = when (slot) {
            is VoiceSlotParser.Slot.Pulse -> slot.open
            is VoiceSlotParser.Slot.Binary -> slot.on
            else -> return Plan.Fail("Скажите «открой» или «закрой»")
        }
        val solved = RelativeAdjustSolver.pulse(open, write)
            ?: return Plan.Fail("Нет значения open/close в каталоге")
        val operation = preferredOperation(action, preferred = "trunk_pulse", fallback = "set")
        return canPayload(action, operation = operation, value = solved.invokeValue)
    }

    private fun planNumberOrOptions(
        action: CatalogAction,
        write: CatalogWriteSchema,
        slot: VoiceSlotParser.Slot,
        catalog: ApiCatalog,
    ): Plan {
        return when (slot) {
            is VoiceSlotParser.Slot.AbsoluteNumber -> {
                val solved = RelativeAdjustSolver.absolute(slot.value, write)
                    ?: return Plan.Fail("Значение вне диапазона")
                valuedPayload(action, solved.invokeValue)
            }
            is VoiceSlotParser.Slot.Relative -> {
                val signalId = linkedSignalId(action)
                    ?: return Plan.Fail("Нет сигнала для относительной настройки")
                // Empty catalog (unit tests with partial fixtures) skips the check.
                if (catalog.signals.isNotEmpty() &&
                    catalog.signals.none { it.id == signalId }
                ) {
                    return Plan.Fail("В каталоге нет сигнала $signalId")
                }
                val steps = slot.steps
                Plan.NeedsSignal(signalId) { reading ->
                    if (!reading.available) {
                        return@NeedsSignal Result.failure(
                            IllegalStateException("Сейчас нет данных по сигналу $signalId"),
                        )
                    }
                    val current = RelativeAdjustSolver.readingToDouble(reading.value)
                        ?: return@NeedsSignal Result.failure(
                            IllegalStateException("Не удалось прочитать текущее значение"),
                        )
                    val solved = RelativeAdjustSolver.relative(current, write, steps)
                        ?: return@NeedsSignal Result.failure(
                            IllegalStateException("Не удалось подобрать значение"),
                        )
                    runCatching { valuedPayloadJson(action, solved.invokeValue) }
                }
            }
            is VoiceSlotParser.Slot.Binary, is VoiceSlotParser.Slot.Pulse ->
                Plan.Fail("Для этого действия скажите число или «потеплее» / «тише»")
            VoiceSlotParser.Slot.None ->
                Plan.Fail("Скажите число или «потеплее» / «тише»")
        }
    }

    private fun valuedPayload(action: CatalogAction, value: Any): Plan =
        runCatching { valuedPayloadJson(action, value) }.fold(
            onSuccess = { Plan.Immediate(it) },
            onFailure = { Plan.Fail(it.message ?: "Нельзя собрать команду") },
        )

    private fun valuedPayloadJson(action: CatalogAction, value: Any): JSONObject =
        when (action.type) {
            "builtin" -> {
                val actionType = action.actionType?.takeIf { it.isNotBlank() }
                    ?: error("builtin without actionType")
                val intValue = when (value) {
                    is Int -> value
                    is Number -> value.toInt()
                    is String -> value.toDoubleOrNull()?.toInt()
                        ?: error("Нужно целое значение громкости")
                    else -> error("Нужно целое значение громкости")
                }
                InvokeActionPayloadBuilder.buildBuiltin(actionType, intValue)
            }
            "can_command" -> {
                val bus = action.bus?.takeIf { it.isNotBlank() } ?: error("can_command without bus")
                val propertyId = action.propertyId ?: error("can_command without propertyId")
                InvokeActionPayloadBuilder.buildCanCommand(
                    bus = bus,
                    propertyId = propertyId,
                    operation = preferredOperation(action, preferred = "set", fallback = "set"),
                    value = value,
                )
            }
            else -> error("Неподдерживаемый тип для слотов: ${action.type}")
        }

    private fun canPayload(action: CatalogAction, operation: String, value: Any): Plan {
        val bus = action.bus?.takeIf { it.isNotBlank() }
            ?: return Plan.Fail("can_command without bus")
        val propertyId = action.propertyId
            ?: return Plan.Fail("can_command without propertyId")
        return Plan.Immediate(
            InvokeActionPayloadBuilder.buildCanCommand(bus, propertyId, operation, value),
        )
    }

    private fun preferredOperation(
        action: CatalogAction,
        preferred: String,
        fallback: String,
    ): String {
        if (preferred in action.operations) return preferred
        if (fallback in action.operations) return fallback
        return action.operations.firstOrNull() ?: preferred
    }
}
