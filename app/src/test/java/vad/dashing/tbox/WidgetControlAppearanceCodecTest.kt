package vad.dashing.tbox

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.ui.ControlAppearanceKind
import vad.dashing.tbox.ui.controlAppearanceKindForDataKey
import vad.dashing.tbox.ui.defaultActiveContentForKind
import vad.dashing.tbox.ui.defaultControlShapeDpForKind
import vad.dashing.tbox.ui.defaultInactiveContentForKind
import vad.dashing.tbox.ui.resolveControlAppearance
import vad.dashing.tbox.ui.theme.WidgetActiveColors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WidgetControlAppearanceCodecTest {

    @Test
    fun normalizeWidgetControlShape_clampsToAllowedRange() {
        assertEquals(0, normalizeWidgetControlShape(-1))
        assertEquals(10, normalizeWidgetControlShape(10))
        assertEquals(50, normalizeWidgetControlShape(99))
    }

    @Test
    fun usesDefaultControlColors_trueWhenAllNull() {
        val cfg = FloatingDashboardWidgetConfig(dataKey = "steeringWheelHeatWidget")
        assertTrue(cfg.usesDefaultControlColors())
    }

    @Test
    fun usesDefaultControlColors_falseWhenAnySet() {
        val cfg = FloatingDashboardWidgetConfig(
            dataKey = "steeringWheelHeatWidget",
            controlActiveColorLight = 0xFF2180F3.toInt(),
        )
        assertFalse(cfg.usesDefaultControlColors())
    }

    @Test
    fun codec_roundTripsControlColorsAndShape() {
        val original = FloatingDashboardWidgetConfig(
            dataKey = "hvacAcWidget",
            controlInactiveColorLight = 0xFF111111.toInt(),
            controlInactiveColorDark = 0xFFEEEEEE.toInt(),
            controlActiveColorLight = 0xFF2180F3.toInt(),
            controlActiveColorDark = 0xFF2180F3.toInt(),
            controlInactiveBackgroundColorLight = 0x00000000,
            controlActiveBackgroundColorLight = 0x80FFFFFF.toInt(),
            controlShape = 12,
        )
        val parsed = parseWidgetConfigsFromString(serializeWidgetConfigs(listOf(original))).single()
        assertEquals(original.controlInactiveColorLight, parsed.controlInactiveColorLight)
        assertEquals(original.controlInactiveColorDark, parsed.controlInactiveColorDark)
        assertEquals(original.controlActiveColorLight, parsed.controlActiveColorLight)
        assertEquals(original.controlActiveColorDark, parsed.controlActiveColorDark)
        assertEquals(
            original.controlInactiveBackgroundColorLight,
            parsed.controlInactiveBackgroundColorLight,
        )
        assertEquals(
            original.controlActiveBackgroundColorLight,
            parsed.controlActiveBackgroundColorLight,
        )
        assertNull(parsed.controlInactiveBackgroundColorDark)
        assertNull(parsed.controlActiveBackgroundColorDark)
        assertEquals(12, parsed.controlShape)
    }

    @Test
    fun codec_omitsControlFieldsWhenDefaults() {
        val json = serializeWidgetConfigs(
            listOf(FloatingDashboardWidgetConfig(dataKey = "musicWidget")),
        )
        assertFalse(json.contains("controlInactiveColorLight"))
        assertFalse(json.contains("controlShape"))
    }

    @Test
    fun controlAppearanceKind_mapsHeatClimateMusicDayNight() {
        assertEquals(
            ControlAppearanceKind.Heat,
            controlAppearanceKindForDataKey("steeringWheelHeatWidget"),
        )
        assertEquals(
            ControlAppearanceKind.Climate,
            controlAppearanceKindForDataKey("hvacAcWidget"),
        )
        assertEquals(
            ControlAppearanceKind.MusicStepper,
            controlAppearanceKindForDataKey(MUSIC_WIDGET_DATA_KEY),
        )
        assertEquals(
            ControlAppearanceKind.MusicStepper,
            controlAppearanceKindForDataKey(MEDIA_VOLUME_WIDGET_HORIZONTAL_DATA_KEY),
        )
        assertEquals(
            ControlAppearanceKind.DayNight,
            controlAppearanceKindForDataKey(DAY_NIGHT_THEME_WIDGET_DATA_KEY),
        )
    }

    @Test
    fun resolve_heatDefaults_toSecondaryActive() {
        val tile = Color(0xFF1A1C1E)
        val resolved = resolveControlAppearance(
            config = FloatingDashboardWidgetConfig(dataKey = "steeringWheelHeatWidget"),
            currentTheme = 1,
            tileTextColor = tile,
            kind = ControlAppearanceKind.Heat,
        )
        assertEquals(WidgetActiveColors.Secondary.toArgb(), resolved.activeContent.toArgb())
        assertEquals(tile.toArgb(), resolved.inactiveContent.toArgb())
        assertEquals(0, resolved.shapeDp.value.toInt())
    }

    @Test
    fun resolve_climateDefaults_toPrimaryActive() {
        val tile = Color(0xFFE2E2E6)
        val resolved = resolveControlAppearance(
            config = FloatingDashboardWidgetConfig(dataKey = "hvacAcWidget"),
            currentTheme = 2,
            tileTextColor = tile,
            kind = ControlAppearanceKind.Climate,
        )
        assertEquals(WidgetActiveColors.Primary.toArgb(), resolved.activeContent.toArgb())
        assertEquals(tile.toArgb(), resolved.inactiveContent.toArgb())
    }

    @Test
    fun resolve_dayNight_swapsPrimarySecondary() {
        val tile = Color.White
        assertEquals(
            WidgetActiveColors.Primary.toArgb(),
            defaultInactiveContentForKind(ControlAppearanceKind.DayNight, tile).toArgb(),
        )
        assertEquals(
            WidgetActiveColors.Secondary.toArgb(),
            defaultActiveContentForKind(ControlAppearanceKind.DayNight, tile).toArgb(),
        )
    }

    @Test
    fun resolve_fanStepper_defaultActiveIsPrimary() {
        val tile = Color(0xFF1A1C1E)
        val resolved = resolveControlAppearance(
            config = FloatingDashboardWidgetConfig(dataKey = HVAC_FAN_WIDGET_HORIZONTAL_DATA_KEY),
            currentTheme = 1,
            tileTextColor = tile,
            kind = ControlAppearanceKind.MusicStepper,
        )
        assertEquals(WidgetActiveColors.Primary.toArgb(), resolved.activeContent.toArgb())
        assertEquals(tile.toArgb(), resolved.inactiveContent.toArgb())
        assertEquals(10, resolved.shapeDp.value.toInt())
    }

    @Test
    fun resolve_volumeStepper_defaultActiveMatchesTileText() {
        val tile = Color(0xFF1A1C1E)
        val resolved = resolveControlAppearance(
            config = FloatingDashboardWidgetConfig(dataKey = MEDIA_VOLUME_WIDGET_HORIZONTAL_DATA_KEY),
            currentTheme = 1,
            tileTextColor = tile,
            kind = ControlAppearanceKind.MusicStepper,
        )
        assertEquals(tile.toArgb(), resolved.activeContent.toArgb())
        assertEquals(tile.toArgb(), resolved.inactiveContent.toArgb())
    }

    @Test
    fun resolve_musicStepper_defaultShape10() {
        assertEquals(10, defaultControlShapeDpForKind(ControlAppearanceKind.MusicStepper))
        val bg = Color(0x592180F3)
        val resolved = resolveControlAppearance(
            config = FloatingDashboardWidgetConfig(dataKey = MUSIC_WIDGET_DATA_KEY),
            currentTheme = 1,
            tileTextColor = Color.Black,
            kind = ControlAppearanceKind.MusicStepper,
            musicStepperBackground = bg,
        )
        assertEquals(10, resolved.shapeDp.value.toInt())
        assertEquals(bg.toArgb(), resolved.inactiveBackground.toArgb())
    }

    @Test
    fun resolve_customActiveOverridesDefault() {
        val custom = 0xFFFF0000.toInt()
        val resolved = resolveControlAppearance(
            config = FloatingDashboardWidgetConfig(
                dataKey = "steeringWheelHeatWidget",
                controlActiveColorLight = custom,
            ),
            currentTheme = 1,
            tileTextColor = Color.Black,
            kind = ControlAppearanceKind.Heat,
        )
        assertEquals(custom, resolved.activeContent.toArgb())
    }
}
