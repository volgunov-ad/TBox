package vad.dashing.tbox.automation

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.SettingsBackupCoordinator
import vad.dashing.tbox.PreferenceStoreBackup

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HuDeviceTestFixtureContractTest {
    @Test
    fun automationsFixture_decodesAndIsRunnable() {
        val document = decodeAutomations(fixtureText("hu_test_automations.json"))
        assertHuTestDocument(document)
    }

    @Test
    fun backupFixture_embedsTheSameRunnableAutomations() {
        val backup = JSONObject(fixtureText("hu_test_backup.json"))
        assertEquals(SettingsBackupCoordinator.FORMAT_VERSION, backup.getInt("formatVersion"))
        assertEquals("vad.dashing.tbox", backup.getString("packageName"))
        val settings = backup.getJSONArray("settings")
        val automationsPref = (0 until settings.length())
            .map { settings.getJSONObject(it) }
            .first { it.getString(PreferenceStoreBackup.K_NAME).endsWith("automations_json") }
        assertEquals(PreferenceStoreBackup.T_STRING, automationsPref.getString(PreferenceStoreBackup.K_TYPE))
        val document = decodeAutomations(automationsPref.getString(PreferenceStoreBackup.K_VALUE))
        assertHuTestDocument(document)
    }

    private fun decodeAutomations(raw: String): AutomationDocument {
        val decoded = AutomationCodec.decode(raw)
        assertTrue(decoded.isSuccess)
        return decoded.getOrThrow()
    }

    private fun assertHuTestDocument(document: AutomationDocument) {
        val issues = AutomationValidator.validate(document)
        assertTrue(issues.joinToString { "${it.path}: ${it.message}" }, issues.isEmpty())
        assertEquals(4, document.automations.size)
        val byId = document.automations.associateBy { it.id }
        val hide = requireNotNull(byId["hu-test-hide-on-settings"])
        val show = requireNotNull(byId["hu-test-enable-on-monitor"])
        val navi = requireNotNull(byId["hu-test-navi-disable"])
        val started = requireNotNull(byId["hu-test-service-started"])

        document.automations.forEach { definition ->
            assertTrue(definition.name, definition.enabled)
            assertTrue(definition.name, AutomationValidator.isRunnable(definition))
        }

        assertEquals(AutomationRunMode.RESTART, hide.runMode)
        assertEquals(AutomationRunMode.RESTART, show.runMode)
        assertEquals(AutomationRunMode.RESTART, navi.runMode)
        assertEquals(AutomationRunMode.SINGLE, started.runMode)

        assertForegroundApp(hide, "com.android.settings")
        assertForegroundApp(show, "vad.dashing.tbox")
        assertForegroundApp(navi, "ru.yandex.yandexnavi")
        val serviceTrigger = started.triggers.single() as AutomationTrigger.SystemEvent
        assertEquals(AutomationSystemEvent.BACKGROUND_SERVICE_STARTED, serviceTrigger.event)

        assertToast(hide, "HUTEST hide overlays (Settings)")
        assertToast(show, "HUTEST enable overlays (Monitor)")
        assertToast(navi, "HUTEST disable overlays (Navi)")
        assertToast(started, "HUTEST service started")
        assertBuiltin(hide, AutomationBuiltinActionType.TOGGLE_HIDE_FLOATING_PANELS)
        assertBuiltin(show, AutomationBuiltinActionType.TOGGLE_FLOATING_PANELS_ENABLED)
        assertBuiltin(navi, AutomationBuiltinActionType.TOGGLE_FLOATING_PANELS_ENABLED)
    }

    private fun assertForegroundApp(definition: AutomationDefinition, expectedPackage: String) {
        val trigger = definition.triggers.single() as AutomationTrigger.StateEquals
        assertEquals(AutomationSignalId.FOREGROUND_APP, trigger.signal)
        assertEquals(AutomationSignalSource.APP, trigger.source)
        assertEquals(expectedPackage, trigger.expectedState)
        assertEquals(AutomationStartupBehavior.INITIALIZE_ONLY, trigger.startupBehavior)
    }

    private fun assertToast(definition: AutomationDefinition, text: String) {
        val toast = definition.actions.filterIsInstance<AutomationAction.Builtin>()
            .first { it.type == AutomationBuiltinActionType.SHOW_TOAST }
        assertEquals(text, toast.stringValue)
    }

    private fun assertBuiltin(
        definition: AutomationDefinition,
        actionType: AutomationBuiltinActionType,
    ) {
        assertTrue(
            definition.actions.filterIsInstance<AutomationAction.Builtin>()
                .any { it.type == actionType },
        )
    }

    private fun fixtureText(name: String): String {
        val cwd = File(System.getProperty("user.dir"))
        val candidates = listOf(
            cwd.resolve("scripts/hu-device-test/fixtures/$name"),
            cwd.resolve("../scripts/hu-device-test/fixtures/$name"),
            cwd.resolve("../../scripts/hu-device-test/fixtures/$name"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("missing HU fixture $name (cwd=${cwd.absolutePath})")
        return file.readText()
    }
}
