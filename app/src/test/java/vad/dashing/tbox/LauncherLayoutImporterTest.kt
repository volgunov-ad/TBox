package vad.dashing.tbox

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LauncherLayoutImporterTest {

    @Test
    fun importJson_appliesMainScreenPanelsFromLauncherLayout() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val sm = SettingsManager(context)
        val json = context.assets.open("launcher_tesla_preset.json").bufferedReader().use { it.readText() }

        val result = LauncherLayoutImporter.importJson(sm, json)

        assertTrue(result.isSuccess)
        val panels = sm.mainScreenDashboardsFlow.first()
        assertEquals(2, panels.size)
        assertEquals("left_card", panels[0].id)
        assertEquals("dock", panels[1].id)
        assertTrue(panels[0].widgetsConfig.isNotEmpty())
    }

    @Test
    fun importJson_rejectsUnsupportedType() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val sm = SettingsManager(context)
        val result = LauncherLayoutImporter.importJson(sm, """{"type":"other","formatVersion":1}""")
        assertTrue(result.isFailure)
    }
}
