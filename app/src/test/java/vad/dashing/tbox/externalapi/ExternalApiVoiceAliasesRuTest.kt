package vad.dashing.tbox.externalapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.automation.AutomationBuiltinActionType
import vad.dashing.tbox.automation.AutomationCanBus
import vad.dashing.tbox.automation.AutomationSignalCatalog
import vad.dashing.tbox.automation.AutomationSignalId
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExternalApiVoiceAliasesRuTest {
    @Test
    fun everyCatalogSignal_hasAtLeastOneAlias() {
        AutomationSignalCatalog.entries.forEach { descriptor ->
            val aliases = ExternalApiVoiceAliasesRu.forSignal(descriptor.id, descriptor.label)
            assertTrue(
                "signal ${descriptor.id.storageKey} must have voiceAliasesRu",
                aliases.isNotEmpty(),
            )
        }
    }

    @Test
    fun outsideTemperature_includesSpokenPhrases() {
        val aliases = ExternalApiVoiceAliasesRu.forSignal(
            AutomationSignalId.OUTSIDE_TEMPERATURE,
            "Температура снаружи",
        )
        assertTrue(aliases.contains("температура снаружи"))
        assertTrue(aliases.contains("сколько градусов на улице"))
        assertTrue(aliases.contains("на улице"))
    }

    @Test
    fun mediaPlay_includesSpokenPhrases() {
        val aliases = ExternalApiVoiceAliasesRu.forBuiltin(AutomationBuiltinActionType.MEDIA_PLAY)
        assertTrue(aliases.contains("воспроизведение"))
        assertTrue(aliases.contains("играй"))
    }

    @Test
    fun hvacPowerCan_includesClimatePhrases() {
        val aliases = ExternalApiVoiceAliasesRu.forCanCommand(
            AutomationCanBus.VEHICLE,
            MbCanKnownVehiclePropertyId.HVAC_POWER,
            "Управление кондиционером",
        )
        assertTrue(aliases.contains("кондиционер"))
        assertTrue(aliases.contains("включи климат"))
    }

    @Test
    fun aliases_areLowercaseNormalized() {
        val aliases = ExternalApiVoiceAliasesRu.forSignal(
            AutomationSignalId.CAR_SPEED,
            "Скорость автомобиля",
        )
        assertFalse(aliases.any { it != it.lowercase() })
        assertEquals(aliases, aliases.distinct())
    }
}
