package vad.dashing.tbox.dcl

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DclLauncherApiCodecTest {

    @Test
    fun parseUiState_okBundle_mapsGeometryAndLayout() {
        val bundle = Bundle().apply {
            putString(DclLauncherApiContract.KEY_STATUS, DclLauncherApiContract.STATUS_OK)
            putString(DclLauncherApiContract.KEY_SCREEN_MODE, DclLauncherApiContract.SCREEN_MODE_SINGLE)
            putString(DclLauncherApiContract.KEY_FOREGROUND_PACKAGE, "ru.yandex.yandexnavi")
            putInt(DclLauncherApiContract.KEY_TOP_BAR_HEIGHT_PX, 51)
            putInt(DclLauncherApiContract.KEY_BOTTOM_BAR_HEIGHT_PX, 82)
            putString(DclLauncherApiContract.KEY_ADAS_PANEL_BOUNDS, "0,51,344,998")
            putString(DclLauncherApiContract.KEY_APP_AREA_BOUNDS, "344,51,1920,998")
            putInt(DclLauncherApiContract.KEY_DISPLAY_WIDTH_PX, 1920)
            putInt(DclLauncherApiContract.KEY_DISPLAY_HEIGHT_PX, 1080)
            putBoolean(DclLauncherApiContract.KEY_OVERLAY_OPEN, true)
            putLong(DclLauncherApiContract.KEY_REVISION, 42L)
            putBoolean(DclLauncherApiContract.KEY_TOP_BAR_VISIBLE, true)
            putBoolean(DclLauncherApiContract.KEY_BOTTOM_BAR_VISIBLE, true)
            putBoolean(DclLauncherApiContract.KEY_ADAS_PANEL_VISIBLE, true)
            putBoolean(DclLauncherApiContract.KEY_NOTIFICATION_SHADE_EXPANDED, true)
        }

        val result = DclLauncherApiCodec.parseUiState(bundle)
        assertTrue(result is DclCallResult.Ok)
        val state = (result as DclCallResult.Ok).value
        assertEquals(DclLauncherApiContract.SCREEN_MODE_SINGLE, state.screenMode)
        assertEquals("ru.yandex.yandexnavi", state.foregroundPackage)
        assertEquals(51, state.topBarHeightPx)
        assertEquals(82, state.bottomBarHeightPx)
        assertEquals("0,51,344,998", state.adasPanelBounds)
        assertEquals("344,51,1920,998", state.appAreaBounds)
        assertEquals(1920, state.displayWidthPx)
        assertEquals(1080, state.displayHeightPx)
        assertTrue(state.overlayOpen)
        assertEquals(42L, state.revision)
        assertEquals(true, state.topBarVisible)
        assertEquals(true, state.bottomBarVisible)
        assertEquals(true, state.adasPanelVisible)
        assertEquals(true, state.notificationShadeExpanded)
        assertNull(state.immersive)
        assertNull(state.smartAccActive)
        assertNull(state.smartAccConfigEnabled)
        assertNull(state.cruiseState)
        assertNull(state.effectiveSpeedLimit)
        assertEquals(DclBounds(344, 51, 1920, 998), state.appArea())
        assertEquals(DclBounds(0, 51, 344, 998), state.adasBounds())
    }

    @Test
    fun parseUiState_notificationShadeExpanded_optionalBoolean() {
        val absent = Bundle().apply {
            putString(DclLauncherApiContract.KEY_STATUS, DclLauncherApiContract.STATUS_OK)
            putLong(DclLauncherApiContract.KEY_REVISION, 1L)
        }
        val absentState = (DclLauncherApiCodec.parseUiState(absent) as DclCallResult.Ok).value
        assertNull(absentState.notificationShadeExpanded)

        val collapsed = Bundle().apply {
            putString(DclLauncherApiContract.KEY_STATUS, DclLauncherApiContract.STATUS_OK)
            putLong(DclLauncherApiContract.KEY_REVISION, 2L)
            putBoolean(DclLauncherApiContract.KEY_NOTIFICATION_SHADE_EXPANDED, false)
        }
        val collapsedState = (DclLauncherApiCodec.parseUiState(collapsed) as DclCallResult.Ok).value
        assertEquals(false, collapsedState.notificationShadeExpanded)

        val stringTrue = Bundle().apply {
            putString(DclLauncherApiContract.KEY_STATUS, DclLauncherApiContract.STATUS_OK)
            putLong(DclLauncherApiContract.KEY_REVISION, 3L)
            putString(DclLauncherApiContract.KEY_NOTIFICATION_SHADE_EXPANDED, "true")
        }
        val stringState = (DclLauncherApiCodec.parseUiState(stringTrue) as DclCallResult.Ok).value
        assertEquals(true, stringState.notificationShadeExpanded)
    }

    @Test
    fun parseUiStateFromExtras_notificationShadeExpanded() {
        val extras = Bundle().apply {
            putString(DclLauncherApiContract.KEY_SCREEN_MODE, DclLauncherApiContract.SCREEN_MODE_HOME)
            putLong(DclLauncherApiContract.KEY_REVISION, 9L)
            putBoolean(DclLauncherApiContract.KEY_NOTIFICATION_SHADE_EXPANDED, true)
        }
        val state = DclLauncherApiCodec.parseUiStateFromExtras(extras)
        assertEquals(true, state!!.notificationShadeExpanded)
        assertEquals(9L, state.revision)
    }

    @Test
    fun parseUiState_errorBundle_returnsError() {
        val bundle = Bundle().apply {
            putString(DclLauncherApiContract.KEY_STATUS, DclLauncherApiContract.STATUS_ERROR)
            putString(DclLauncherApiContract.KEY_ERROR_CODE, DclLauncherApiContract.ERROR_FORBIDDEN)
            putString(DclLauncherApiContract.KEY_MESSAGE, "caller not allowed")
        }

        val result = DclLauncherApiCodec.parseUiState(bundle)
        assertTrue(result is DclCallResult.Error)
        val error = (result as DclCallResult.Error).error
        assertEquals(DclLauncherApiContract.ERROR_FORBIDDEN, error.errorCode)
        assertEquals("caller not allowed", error.message)
    }

    @Test
    fun parseUiState_splitPackages() {
        val bundle = Bundle().apply {
            putString(DclLauncherApiContract.KEY_STATUS, DclLauncherApiContract.STATUS_OK)
            putString(DclLauncherApiContract.KEY_SCREEN_MODE, DclLauncherApiContract.SCREEN_MODE_SPLIT)
            putString(DclLauncherApiContract.KEY_LEFT_PACKAGE, "a.left")
            putString(DclLauncherApiContract.KEY_RIGHT_PACKAGE, "b.right")
            putString(DclLauncherApiContract.KEY_FOREGROUND_PACKAGE, "a.left")
            putLong(DclLauncherApiContract.KEY_REVISION, 7L)
        }

        val state = (DclLauncherApiCodec.parseUiState(bundle) as DclCallResult.Ok).value
        assertEquals("a.left", state.leftPackage)
        assertEquals("b.right", state.rightPackage)
        assertEquals("a.left", state.foregroundPackage)
    }

    @Test
    fun parseUiStateFromExtras_withoutStatus_stillParses() {
        val extras = Bundle().apply {
            putString(DclLauncherApiContract.KEY_SCREEN_MODE, DclLauncherApiContract.SCREEN_MODE_HOME)
            putLong(DclLauncherApiContract.KEY_REVISION, 3L)
        }
        val state = DclLauncherApiCodec.parseUiStateFromExtras(extras)
        assertEquals(DclLauncherApiContract.SCREEN_MODE_HOME, state!!.screenMode)
        assertEquals(3L, state.revision)
    }

    @Test
    fun parseUiStateFromExtras_nullOrError_returnsNull() {
        assertNull(DclLauncherApiCodec.parseUiStateFromExtras(null))
        assertNull(DclLauncherApiCodec.parseUiStateFromExtras(Bundle()))
        val error = Bundle().apply {
            putString(DclLauncherApiContract.KEY_STATUS, DclLauncherApiContract.STATUS_ERROR)
        }
        assertNull(DclLauncherApiCodec.parseUiStateFromExtras(error))
    }

    @Test
    fun parseUiStateFromExtras_poisonEmptyKeys_returnsNull() {
        // Unrelated keys only — must not publish a default home snapshot (P1-3).
        val poison = Bundle().apply {
            putString("unrelated", "x")
        }
        assertFalse(DclLauncherApiCodec.hasUsableUiStateMarkers(poison))
        assertNull(DclLauncherApiCodec.parseUiStateFromExtras(poison))
    }

    @Test
    fun parseUiStateFromExtras_wrongTypedRevision_doesNotCrash() {
        val extras = Bundle().apply {
            putString(DclLauncherApiContract.KEY_STATUS, DclLauncherApiContract.STATUS_OK)
            putString(DclLauncherApiContract.KEY_REVISION, "42")
            putString(DclLauncherApiContract.KEY_TOP_BAR_HEIGHT_PX, "51")
            putString(DclLauncherApiContract.KEY_SCREEN_MODE, DclLauncherApiContract.SCREEN_MODE_SINGLE)
        }
        val state = DclLauncherApiCodec.parseUiStateFromExtras(extras)
        assertEquals(42L, state!!.revision)
        assertEquals(51, state.topBarHeightPx)
        assertEquals(DclLauncherApiContract.SCREEN_MODE_SINGLE, state.screenMode)
    }

    @Test
    fun parseCapabilities_wrongTypedBooleans_doesNotCrash() {
        val bundle = Bundle().apply {
            putString(DclLauncherApiContract.KEY_STATUS, DclLauncherApiContract.STATUS_OK)
            putString(DclLauncherApiContract.KEY_LAUNCH_APP_AREA, "true")
            putString(DclLauncherApiContract.KEY_LAUNCH_FULLSCREEN, "1")
            putString(DclLauncherApiContract.KEY_API_VERSION, "2")
        }
        val result = DclLauncherApiCodec.parseCapabilities(bundle)
        assertTrue(result is DclCallResult.Ok)
        val caps = (result as DclCallResult.Ok).value
        assertTrue(caps.launchAppArea)
        assertTrue(caps.launchFullscreen)
        assertEquals(2, caps.apiVersion)
    }

    @Test
    fun parseBounds_validAndInvalid() {
        assertEquals(
            DclBounds(10, 20, 30, 40),
            DclLauncherApiCodec.parseBounds("10,20,30,40"),
        )
        assertEquals(
            DclBounds(10, 20, 30, 40),
            DclLauncherApiCodec.parseBounds(" 10 , 20 , 30 , 40 "),
        )
        assertEquals(
            DclBounds.DEFAULT_APP_AREA,
            DclLauncherApiCodec.parseBounds("bad"),
        )
        assertEquals(
            DclBounds.DEFAULT_ADAS,
            DclLauncherApiCodec.parseBounds(null, DclBounds.DEFAULT_ADAS),
        )
        assertEquals(
            DclBounds.DEFAULT_APP_AREA,
            DclLauncherApiCodec.parseBounds("1,2,3"),
        )
        val rect = DclLauncherApiCodec.parseBoundsToRect("344,51,1920,998")
        assertEquals(344, rect.left)
        assertEquals(51, rect.top)
        assertEquals(1920, rect.right)
        assertEquals(998, rect.bottom)
    }

    @Test
    fun parseCapabilities_okAndAliases() {
        val bundle = Bundle().apply {
            putString(DclLauncherApiContract.KEY_STATUS, DclLauncherApiContract.STATUS_OK)
            putInt(DclLauncherApiContract.KEY_API_VERSION, 1)
            putBoolean(DclLauncherApiContract.KEY_LAUNCH_FULLSCREEN, true)
            putBoolean(DclLauncherApiContract.KEY_LAUNCH_APP_AREA, true)
            putBoolean(DclLauncherApiContract.KEY_UI_CHROME_QUERY, true)
            putBoolean(DclLauncherApiContract.KEY_UI_CHROME_EVENTS, false)
            putBoolean(DclLauncherApiContract.KEY_ADAS_PANEL, true)
            putString(
                DclLauncherApiContract.KEY_SCREEN_MODES,
                "home,single,full_width,fullscreen,split",
            )
            putBoolean(DclLauncherApiContract.KEY_SPLIT_SUPPORTED, true)
        }
        val caps = (DclLauncherApiCodec.parseCapabilities(bundle) as DclCallResult.Ok).value
        assertEquals(1, caps.apiVersion)
        assertTrue(caps.launchFullscreen)
        assertTrue(caps.launchAppArea)
        assertNull(caps.launchFullWidth)
        assertTrue(caps.uiStateQuery)
        assertFalse(caps.uiStateEvents)
        assertTrue(caps.adasPanel)
        assertEquals(
            listOf("home", "single", "full_width", "fullscreen", "split"),
            caps.screenModes,
        )
        assertEquals(true, caps.splitSupported)
    }

    @Test
    fun parseCapabilities_launchFullWidth_optionalTriState() {
        val withFlag = Bundle().apply {
            putString(DclLauncherApiContract.KEY_STATUS, DclLauncherApiContract.STATUS_OK)
            putBoolean(DclLauncherApiContract.KEY_LAUNCH_FULL_WIDTH, true)
        }
        val capsWith = (DclLauncherApiCodec.parseCapabilities(withFlag) as DclCallResult.Ok).value
        assertEquals(true, capsWith.launchFullWidth)

        val explicitFalse = Bundle().apply {
            putString(DclLauncherApiContract.KEY_STATUS, DclLauncherApiContract.STATUS_OK)
            putBoolean(DclLauncherApiContract.KEY_LAUNCH_FULL_WIDTH, false)
        }
        val capsFalse =
            (DclLauncherApiCodec.parseCapabilities(explicitFalse) as DclCallResult.Ok).value
        assertEquals(false, capsFalse.launchFullWidth)
    }

    @Test
    fun parseUiState_fullWidthScreenMode() {
        val bundle = Bundle().apply {
            putString(DclLauncherApiContract.KEY_STATUS, DclLauncherApiContract.STATUS_OK)
            putString(
                DclLauncherApiContract.KEY_SCREEN_MODE,
                DclLauncherApiContract.SCREEN_MODE_FULL_WIDTH,
            )
            putString(DclLauncherApiContract.KEY_FOREGROUND_PACKAGE, "ru.yandex.music")
            putString(
                DclLauncherApiContract.KEY_APP_AREA_BOUNDS,
                DclLauncherApiContract.DEFAULT_FULL_WIDTH_APP_AREA_BOUNDS,
            )
            putBoolean(DclLauncherApiContract.KEY_ADAS_PANEL_VISIBLE, false)
            putBoolean(DclLauncherApiContract.KEY_TOP_BAR_VISIBLE, true)
            putBoolean(DclLauncherApiContract.KEY_BOTTOM_BAR_VISIBLE, true)
            putBoolean(DclLauncherApiContract.KEY_IMMERSIVE, false)
            putLong(DclLauncherApiContract.KEY_REVISION, 9L)
        }
        val state = (DclLauncherApiCodec.parseUiState(bundle) as DclCallResult.Ok).value
        assertEquals(DclLauncherApiContract.SCREEN_MODE_FULL_WIDTH, state.screenMode)
        assertEquals("ru.yandex.music", state.foregroundPackage)
        assertEquals(
            DclLauncherApiContract.DEFAULT_FULL_WIDTH_APP_AREA_BOUNDS,
            state.appAreaBounds,
        )
        assertEquals(false, state.adasPanelVisible)
        assertEquals(true, state.topBarVisible)
        assertEquals(true, state.bottomBarVisible)
        assertEquals(false, state.immersive)
    }

    @Test
    fun parseLaunchResult_okAndError() {
        val ok = DclLauncherApiCodec.parseLaunchResult(
            Bundle().apply {
                putString(DclLauncherApiContract.KEY_STATUS, DclLauncherApiContract.STATUS_OK)
                putString(DclLauncherApiContract.EXTRA_PACKAGE_NAME, "vad.dashing.tbox")
                putString(DclLauncherApiContract.EXTRA_MODE, DclLauncherApiContract.MODE_APP_AREA)
            },
        )
        assertTrue(ok.success)
        assertNull(ok.error)

        val accepted = DclLauncherApiCodec.parseLaunchResult(
            Bundle().apply {
                putString(DclLauncherApiContract.KEY_STATUS, DclLauncherApiContract.STATUS_ACCEPTED)
            },
        )
        assertTrue(accepted.success)

        val err = DclLauncherApiCodec.parseLaunchResult(
            Bundle().apply {
                putString(DclLauncherApiContract.KEY_STATUS, DclLauncherApiContract.STATUS_ERROR)
                putString(DclLauncherApiContract.KEY_ERROR_CODE, DclLauncherApiContract.ERROR_NOT_INSTALLED)
                putString(DclLauncherApiContract.KEY_MESSAGE, "missing")
            },
        )
        assertFalse(err.success)
        assertEquals(DclLauncherApiContract.ERROR_NOT_INSTALLED, err.error?.errorCode)
        assertEquals("missing", err.error?.message)
    }

    @Test
    fun isUiStateAction_matchesBothAliases() {
        assertTrue(
            DclLauncherApiRepository.isUiStateAction(DclLauncherApiContract.ACTION_UI_STATE_CHANGED),
        )
        assertTrue(
            DclLauncherApiRepository.isUiStateAction(DclLauncherApiContract.ACTION_UI_CHROME_CHANGED),
        )
        assertFalse(DclLauncherApiRepository.isUiStateAction("other.action"))
        assertFalse(DclLauncherApiRepository.isUiStateAction(null))
    }

    @Test
    fun parseUiState_smartAccFields_optional() {
        val withFields = Bundle().apply {
            putString(DclLauncherApiContract.KEY_STATUS, DclLauncherApiContract.STATUS_OK)
            putLong(DclLauncherApiContract.KEY_REVISION, 11L)
            putBoolean(DclLauncherApiContract.KEY_SMART_ACC_ACTIVE, true)
            putBoolean(DclLauncherApiContract.KEY_SMART_ACC_CONFIG_ENABLED, false)
            putString(DclLauncherApiContract.KEY_CRUISE_STATE, DclLauncherApiContract.CRUISE_STATE_ACTIVE)
            putInt(DclLauncherApiContract.KEY_EFFECTIVE_SPEED_LIMIT, 90)
        }
        val state = (DclLauncherApiCodec.parseUiState(withFields) as DclCallResult.Ok).value
        assertEquals(true, state.smartAccActive)
        assertEquals(false, state.smartAccConfigEnabled)
        assertEquals(DclLauncherApiContract.CRUISE_STATE_ACTIVE, state.cruiseState)
        assertEquals(90, state.effectiveSpeedLimit)

        val absent = Bundle().apply {
            putString(DclLauncherApiContract.KEY_STATUS, DclLauncherApiContract.STATUS_OK)
            putLong(DclLauncherApiContract.KEY_REVISION, 12L)
        }
        val absentState = (DclLauncherApiCodec.parseUiState(absent) as DclCallResult.Ok).value
        assertNull(absentState.smartAccActive)
        assertNull(absentState.cruiseState)
        assertNull(absentState.effectiveSpeedLimit)
    }

    @Test
    fun parseSmartAccState_okBundle_mapsAllFields() {
        val bundle = Bundle().apply {
            putString(DclLauncherApiContract.KEY_STATUS, DclLauncherApiContract.STATUS_OK)
            putBoolean(DclLauncherApiContract.KEY_SMART_ACC_ACTIVE, true)
            putBoolean(DclLauncherApiContract.KEY_SMART_ACC_CONFIG_ENABLED, true)
            putInt(DclLauncherApiContract.KEY_ADAS_ACC_SPEED_OFFSET, 19)
            putString(DclLauncherApiContract.KEY_CRUISE_STATE, DclLauncherApiContract.CRUISE_STATE_STANDBY)
            putInt(DclLauncherApiContract.KEY_EFFECTIVE_SPEED_LIMIT, 60)
            putInt(DclLauncherApiContract.KEY_TARGET_SPEED, 79)
        }
        val state = (DclLauncherApiCodec.parseSmartAccState(bundle) as DclCallResult.Ok).value
        assertEquals(true, state.smartAccActive)
        assertEquals(true, state.smartAccConfigEnabled)
        assertEquals(19, state.adasAccSpeedOffset)
        assertEquals(DclLauncherApiContract.CRUISE_STATE_STANDBY, state.cruiseState)
        assertEquals(60, state.effectiveSpeedLimit)
        assertEquals(79, state.targetSpeed)
    }

    @Test
    fun parseSmartAccState_optionalAbsents() {
        val bundle = Bundle().apply {
            putString(DclLauncherApiContract.KEY_STATUS, DclLauncherApiContract.STATUS_OK)
            putBoolean(DclLauncherApiContract.KEY_SMART_ACC_ACTIVE, false)
            putString(DclLauncherApiContract.KEY_CRUISE_STATE, DclLauncherApiContract.CRUISE_STATE_OFF)
        }
        val state = (DclLauncherApiCodec.parseSmartAccState(bundle) as DclCallResult.Ok).value
        assertEquals(false, state.smartAccActive)
        assertNull(state.smartAccConfigEnabled)
        assertNull(state.adasAccSpeedOffset)
        assertEquals(DclLauncherApiContract.CRUISE_STATE_OFF, state.cruiseState)
        assertNull(state.effectiveSpeedLimit)
        assertNull(state.targetSpeed)
    }

    @Test
    fun parseSmartAccState_errorBundle() {
        val bundle = Bundle().apply {
            putString(DclLauncherApiContract.KEY_STATUS, DclLauncherApiContract.STATUS_ERROR)
            putString(DclLauncherApiContract.KEY_ERROR_CODE, DclLauncherApiContract.ERROR_UNSUPPORTED)
            putString(DclLauncherApiContract.KEY_MESSAGE, "no smart acc")
        }
        val result = DclLauncherApiCodec.parseSmartAccState(bundle)
        assertTrue(result is DclCallResult.Error)
        assertEquals(
            DclLauncherApiContract.ERROR_UNSUPPORTED,
            (result as DclCallResult.Error).error.errorCode,
        )
    }

    @Test
    fun buildSetSmartAccExtras_andArg() {
        assertEquals("true", DclLauncherApiCodec.setSmartAccArg(true))
        assertEquals("false", DclLauncherApiCodec.setSmartAccArg(false))

        val full = DclLauncherApiCodec.buildSetSmartAccExtras(
            enabled = true,
            targetSpeed = 90,
            configEnabled = false,
        )
        assertTrue(full.getBoolean(DclLauncherApiContract.EXTRA_ENABLED))
        assertEquals(90, full.getInt(DclLauncherApiContract.EXTRA_TARGET_SPEED))
        assertFalse(full.getBoolean(DclLauncherApiContract.EXTRA_CONFIG_ENABLED))

        val minimal = DclLauncherApiCodec.buildSetSmartAccExtras(enabled = false)
        assertFalse(minimal.getBoolean(DclLauncherApiContract.EXTRA_ENABLED))
        assertFalse(minimal.containsKey(DclLauncherApiContract.EXTRA_TARGET_SPEED))
        assertFalse(minimal.containsKey(DclLauncherApiContract.EXTRA_CONFIG_ENABLED))
    }

    @Test
    fun parseStatusOk_okAndError() {
        val ok = DclLauncherApiCodec.parseStatusOk(
            Bundle().apply {
                putString(DclLauncherApiContract.KEY_STATUS, DclLauncherApiContract.STATUS_OK)
            },
        )
        assertTrue(ok is DclCallResult.Ok)

        val err = DclLauncherApiCodec.parseStatusOk(
            Bundle().apply {
                putString(DclLauncherApiContract.KEY_STATUS, DclLauncherApiContract.STATUS_ERROR)
                putString(DclLauncherApiContract.KEY_ERROR_CODE, DclLauncherApiContract.ERROR_BUSY)
            },
        )
        assertTrue(err is DclCallResult.Error)
        assertEquals(DclLauncherApiContract.ERROR_BUSY, (err as DclCallResult.Error).error.errorCode)
    }

    @Test
    fun smartAccFromUiState_mergesWithPrevious() {
        val ui = DclUiState(
            screenMode = "home",
            foregroundPackage = "",
            leftPackage = "",
            rightPackage = "",
            topBarHeightPx = 51,
            bottomBarHeightPx = 82,
            adasPanelBounds = DclBounds.DEFAULT_ADAS.toCsv(),
            appAreaBounds = DclBounds.DEFAULT_APP_AREA.toCsv(),
            displayWidthPx = 1920,
            displayHeightPx = 1080,
            overlayOpen = false,
            revision = 1L,
            updatedAtEpochMs = null,
            adasPanelVisible = true,
            topBarVisible = true,
            bottomBarVisible = true,
            immersive = false,
            notificationShadeExpanded = false,
            smartAccActive = true,
            smartAccConfigEnabled = null,
            cruiseState = DclLauncherApiContract.CRUISE_STATE_ACTIVE,
            effectiveSpeedLimit = 80,
        )
        val previous = DclSmartAccState(
            smartAccActive = false,
            smartAccConfigEnabled = true,
            adasAccSpeedOffset = 5,
            cruiseState = DclLauncherApiContract.CRUISE_STATE_OFF,
            effectiveSpeedLimit = 60,
            targetSpeed = 65,
        )
        val merged = DclLauncherApiCodec.smartAccFromUiState(ui, previous)
        assertEquals(true, merged.smartAccActive)
        assertEquals(true, merged.smartAccConfigEnabled)
        assertEquals(5, merged.adasAccSpeedOffset)
        assertEquals(DclLauncherApiContract.CRUISE_STATE_ACTIVE, merged.cruiseState)
        assertEquals(80, merged.effectiveSpeedLimit)
        assertEquals(65, merged.targetSpeed)
    }
}
