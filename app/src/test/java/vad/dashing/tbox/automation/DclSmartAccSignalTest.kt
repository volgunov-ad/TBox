package vad.dashing.tbox.automation

import vad.dashing.tbox.ruEn

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.dcl.DclLauncherApiContract
import vad.dashing.tbox.dcl.DclLauncherApiRepository
import vad.dashing.tbox.dcl.DclSmartAccState

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DclSmartAccSignalTest {

    @Test
    fun catalog_exposesSmartAccSignals() {
        val active = AutomationSignalCatalog.get(AutomationSignalId.DCL_SMART_ACC_ACTIVE)
        assertEquals(AutomationSignalValueType.STATE, active.id.valueType)
        assertEquals(listOf("off", "on"), active.stateOptions)
        assertEquals("dcl_smart_acc_active", active.id.storageKey)

        val cruise = AutomationSignalCatalog.get(AutomationSignalId.DCL_SMART_ACC_CRUISE_STATE)
        assertEquals(
            listOf("Off", "Standby", "Active", "Override", "Fault"),
            cruise.stateOptions,
        )

        val limit = AutomationSignalCatalog.get(AutomationSignalId.DCL_EFFECTIVE_SPEED_LIMIT)
        assertEquals(AutomationSignalValueType.NUMBER, limit.id.valueType)
        assertEquals(ruEn("км/ч", "km/h"), limit.unit)

        assertEquals(
            ruEn("DCL: Умный ACC", "DCL: Smart ACC"),
            AutomationParameterLabels.signalLabel(AutomationSignalId.DCL_SMART_ACC_ACTIVE),
        )
    }

    @Test
    fun actions_storageKeys() {
        assertEquals(
            "dcl_set_smart_acc",
            AutomationBuiltinActionType.DCL_SET_SMART_ACC.storageKey,
        )
        assertEquals(
            "dcl_toggle_smart_acc",
            AutomationBuiltinActionType.DCL_TOGGLE_SMART_ACC.storageKey,
        )
        assertEquals(
            AutomationBuiltinActionType.DCL_SET_SMART_ACC,
            AutomationBuiltinActionType.fromStorageKey("dcl_set_smart_acc"),
        )
    }

    @Test
    fun signalFlow_mapsSmartAccState() = runBlocking {
        val activeKey = AutomationSignalKey(
            AutomationSignalId.DCL_SMART_ACC_ACTIVE,
            AutomationSignalSource.APP,
        )
        val cruiseKey = AutomationSignalKey(
            AutomationSignalId.DCL_SMART_ACC_CRUISE_STATE,
            AutomationSignalSource.APP,
        )
        val limitKey = AutomationSignalKey(
            AutomationSignalId.DCL_EFFECTIVE_SPEED_LIMIT,
            AutomationSignalSource.APP,
        )
        val offsetKey = AutomationSignalKey(
            AutomationSignalId.DCL_SMART_ACC_SPEED_OFFSET,
            AutomationSignalSource.APP,
        )
        val activeFlow = AutomationSignalReads.flowFor(activeKey)
        val cruiseFlow = AutomationSignalReads.flowFor(cruiseKey)
        val limitFlow = AutomationSignalReads.flowFor(limitKey)
        val offsetFlow = AutomationSignalReads.flowFor(offsetKey)
        assertTrue(activeFlow != null)
        try {
            DclLauncherApiRepository.publishSmartAccStateForTests(null)
            assertEquals(AutomationSignalValue.Unavailable, activeFlow!!.first())
            assertEquals(AutomationSignalValue.Unavailable, cruiseFlow!!.first())
            assertEquals(AutomationSignalValue.Unavailable, limitFlow!!.first())

            DclLauncherApiRepository.publishSmartAccStateForTests(
                DclSmartAccState(
                    smartAccActive = true,
                    smartAccConfigEnabled = false,
                    adasAccSpeedOffset = 19,
                    cruiseState = DclLauncherApiContract.CRUISE_STATE_ACTIVE,
                    effectiveSpeedLimit = 90,
                    targetSpeed = 109,
                ),
            )
            assertEquals(AutomationSignalValue.State("on"), activeFlow.first())
            assertEquals(
                AutomationSignalValue.State(DclLauncherApiContract.CRUISE_STATE_ACTIVE),
                cruiseFlow!!.first(),
            )
            assertEquals(AutomationSignalValue.Number(90.0), limitFlow!!.first())
            assertEquals(AutomationSignalValue.Number(19.0), offsetFlow!!.first())

            DclLauncherApiRepository.publishSmartAccStateForTests(
                DclSmartAccState(
                    smartAccActive = false,
                    smartAccConfigEnabled = true,
                    adasAccSpeedOffset = null,
                    cruiseState = DclLauncherApiContract.CRUISE_STATE_OFF,
                    effectiveSpeedLimit = null,
                    targetSpeed = null,
                ),
            )
            assertEquals(AutomationSignalValue.State("off"), activeFlow.first())
            assertEquals(AutomationSignalValue.Unavailable, limitFlow.first())
            assertEquals(AutomationSignalValue.Unavailable, offsetFlow!!.first())
        } finally {
            DclLauncherApiRepository.publishSmartAccStateForTests(null)
            DclLauncherApiRepository.publishUiStateForTests(null)
        }
    }
}
