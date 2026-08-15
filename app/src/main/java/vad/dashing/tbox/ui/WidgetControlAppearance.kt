package vad.dashing.tbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import vad.dashing.tbox.ACC_CRUISE_WIDGET_DATA_KEY
import vad.dashing.tbox.CRUISE_STATUS_WIDGET_DATA_KEY
import vad.dashing.tbox.DAY_NIGHT_THEME_WIDGET_DATA_KEY
import vad.dashing.tbox.DRIVE_MODE_WIDGET_DATA_KEY
import vad.dashing.tbox.DRIVE_MODE_CYCLE_WIDGET_DATA_KEY
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.FRONT_LEFT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY
import vad.dashing.tbox.FRONT_RIGHT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY
import vad.dashing.tbox.HVAC_BLOW_MODE_CYCLE_WIDGET_DATA_KEY
import vad.dashing.tbox.HVAC_BLOW_MODE_PANEL_WIDGET_HORIZONTAL_DATA_KEY
import vad.dashing.tbox.HVAC_BLOW_MODE_PANEL_WIDGET_VERTICAL_DATA_KEY
import vad.dashing.tbox.HVAC_FAN_WIDGET_HORIZONTAL_DATA_KEY
import vad.dashing.tbox.HVAC_FAN_WIDGET_VERTICAL_DATA_KEY
import vad.dashing.tbox.HVAC_SYNC_WIDGET_DATA_KEY
import vad.dashing.tbox.HVAC_TEMP_LEFT_WIDGET_HORIZONTAL_DATA_KEY
import vad.dashing.tbox.HVAC_TEMP_LEFT_WIDGET_VERTICAL_DATA_KEY
import vad.dashing.tbox.HVAC_TEMP_RIGHT_WIDGET_HORIZONTAL_DATA_KEY
import vad.dashing.tbox.HVAC_TEMP_RIGHT_WIDGET_VERTICAL_DATA_KEY
import vad.dashing.tbox.MEDIA_VOLUME_WIDGET_HORIZONTAL_DATA_KEY
import vad.dashing.tbox.MEDIA_VOLUME_WIDGET_VERTICAL_DATA_KEY
import vad.dashing.tbox.MIRROR_ADJUST_MODE_WIDGET_DATA_KEY
import vad.dashing.tbox.MIRROR_FOLD_WIDGET_DATA_KEY
import vad.dashing.tbox.MUSIC_BUTTONS_WIDGET_HORIZONTAL_DATA_KEY
import vad.dashing.tbox.MUSIC_BUTTONS_WIDGET_VERTICAL_DATA_KEY
import vad.dashing.tbox.MUSIC_COVER_WIDGET_DATA_KEY
import vad.dashing.tbox.MUSIC_SQUARE_WIDGET_DATA_KEY
import vad.dashing.tbox.MUSIC_WIDGET_DATA_KEY
import vad.dashing.tbox.PARKING_RADAR_WIDGET_DATA_KEY
import vad.dashing.tbox.ROAD_MATCH_MAP_WIDGET_DATA_KEY
import vad.dashing.tbox.REAR_LEFT_SEAT_HEAT_WIDGET_DATA_KEY
import vad.dashing.tbox.REAR_RIGHT_SEAT_HEAT_WIDGET_DATA_KEY
import vad.dashing.tbox.SPEED_LIMITER_WIDGET_DATA_KEY
import vad.dashing.tbox.TRUNK_DOOR_WIDGET_DATA_KEY
import vad.dashing.tbox.WIPER_MAINTENANCE_WIDGET_DATA_KEY
import vad.dashing.tbox.isStepperWidgetDataKey
import vad.dashing.tbox.normalizeWidgetControlShape
import vad.dashing.tbox.ui.theme.WidgetActiveColors

/** Default corner radius (dp) for music and stepper control buttons. */
const val DEFAULT_MUSIC_STEPPER_CONTROL_SHAPE_DP = 10

/** Alpha for default music/stepper control background (`surfaceVariant`). */
const val DEFAULT_MUSIC_STEPPER_CONTROL_BG_ALPHA = 0.35f

data class ResolvedControlColors(
    val inactiveContent: Color,
    val activeContent: Color,
    val inactiveBackground: Color,
    val activeBackground: Color,
    val shapeDp: Dp,
)

/**
 * Widget class for control-appearance defaults (not persisted).
 */
enum class ControlAppearanceKind {
    /** Heat / defrost: active = Secondary orange. */
    Heat,
    /** Climate toggles / vent / blow: active = Primary blue. */
    Climate,
    /** Music transport and steppers: surfaceVariant bg, shape 10. */
    MusicStepper,
    /** Day = Secondary (active), night = Primary (inactive). */
    DayNight,
    /** Closed = inactive, open = active Primary; blink partner stays Secondary. */
    Trunk,
    /** Per-mode active colors until user overrides active. */
    DriveMode,
    /** No dedicated control chrome (fallbacks). */
    None,
}

val LocalWidgetControlAppearance = compositionLocalOf {
    ResolvedControlColors(
        inactiveContent = Color.Unspecified,
        activeContent = Color.Unspecified,
        inactiveBackground = Color.Transparent,
        activeBackground = Color.Transparent,
        shapeDp = 0.dp,
    )
}

val LocalWidgetControlUsesDefaults = compositionLocalOf { true }

fun controlAppearanceKindForDataKey(dataKey: String): ControlAppearanceKind {
    return when (dataKey) {
        "steeringWheelHeatWidget",
        "frontWindscreenHeatWidget",
        "rearWindowMirrorsDefrostWidget",
        "hvacDefrosterFrontWidget",
        REAR_LEFT_SEAT_HEAT_WIDGET_DATA_KEY,
        REAR_RIGHT_SEAT_HEAT_WIDGET_DATA_KEY,
        "frontLeftSeatHeatVentWidget",
        "frontRightSeatHeatVentWidget",
        FRONT_LEFT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY,
        FRONT_RIGHT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY,
        -> ControlAppearanceKind.Heat

        "hvacAcWidget",
        "hvacAcCleanWhenLockedWidget",
        "hvacAutoWidget",
        "hvacAirRecirculationWidget",
        HVAC_SYNC_WIDGET_DATA_KEY,
        HVAC_BLOW_MODE_CYCLE_WIDGET_DATA_KEY,
        HVAC_BLOW_MODE_PANEL_WIDGET_HORIZONTAL_DATA_KEY,
        HVAC_BLOW_MODE_PANEL_WIDGET_VERTICAL_DATA_KEY,
        WIPER_MAINTENANCE_WIDGET_DATA_KEY,
        PARKING_RADAR_WIDGET_DATA_KEY,
        MIRROR_ADJUST_MODE_WIDGET_DATA_KEY,
        // Fold has no on-state; only inactive colors are painted (transparent bg by default).
        MIRROR_FOLD_WIDGET_DATA_KEY,
        ACC_CRUISE_WIDGET_DATA_KEY,
        CRUISE_STATUS_WIDGET_DATA_KEY,
        ROAD_MATCH_MAP_WIDGET_DATA_KEY,
        -> ControlAppearanceKind.Climate

        MUSIC_WIDGET_DATA_KEY,
        MUSIC_COVER_WIDGET_DATA_KEY,
        MUSIC_SQUARE_WIDGET_DATA_KEY,
        MUSIC_BUTTONS_WIDGET_HORIZONTAL_DATA_KEY,
        MUSIC_BUTTONS_WIDGET_VERTICAL_DATA_KEY,
        MEDIA_VOLUME_WIDGET_HORIZONTAL_DATA_KEY,
        MEDIA_VOLUME_WIDGET_VERTICAL_DATA_KEY,
        HVAC_FAN_WIDGET_HORIZONTAL_DATA_KEY,
        HVAC_FAN_WIDGET_VERTICAL_DATA_KEY,
        HVAC_TEMP_LEFT_WIDGET_HORIZONTAL_DATA_KEY,
        HVAC_TEMP_LEFT_WIDGET_VERTICAL_DATA_KEY,
        HVAC_TEMP_RIGHT_WIDGET_HORIZONTAL_DATA_KEY,
        HVAC_TEMP_RIGHT_WIDGET_VERTICAL_DATA_KEY,
        SPEED_LIMITER_WIDGET_DATA_KEY,
        -> ControlAppearanceKind.MusicStepper

        DAY_NIGHT_THEME_WIDGET_DATA_KEY -> ControlAppearanceKind.DayNight
        TRUNK_DOOR_WIDGET_DATA_KEY -> ControlAppearanceKind.Trunk
        DRIVE_MODE_WIDGET_DATA_KEY -> ControlAppearanceKind.DriveMode
        DRIVE_MODE_CYCLE_WIDGET_DATA_KEY -> ControlAppearanceKind.DriveMode
        else -> if (isStepperWidgetDataKey(dataKey)) {
            ControlAppearanceKind.MusicStepper
        } else {
            ControlAppearanceKind.None
        }
    }
}

/** Default active content for heat-class widgets (also seat heat levels). */
fun defaultHeatActiveContent(): Color = WidgetActiveColors.Secondary

/** Default active content for climate-class widgets (also seat vent levels). */
fun defaultClimateActiveContent(): Color = WidgetActiveColors.Primary

fun defaultActiveContentForKind(
    kind: ControlAppearanceKind,
    tileTextColor: Color,
    dataKey: String = "",
): Color {
    return when (kind) {
        ControlAppearanceKind.Heat -> WidgetActiveColors.Secondary
        ControlAppearanceKind.Climate -> WidgetActiveColors.Primary
        ControlAppearanceKind.MusicStepper -> when (dataKey) {
            // Fan center «climate on» historically uses Primary; +/− stay inactive (tile text).
            HVAC_FAN_WIDGET_HORIZONTAL_DATA_KEY,
            HVAC_FAN_WIDGET_VERTICAL_DATA_KEY,
            -> WidgetActiveColors.Primary
            else -> tileTextColor
        }
        ControlAppearanceKind.DayNight -> WidgetActiveColors.Secondary
        ControlAppearanceKind.Trunk -> WidgetActiveColors.Primary
        ControlAppearanceKind.DriveMode -> tileTextColor
        ControlAppearanceKind.None -> tileTextColor
    }
}

fun defaultInactiveContentForKind(kind: ControlAppearanceKind, tileTextColor: Color): Color {
    return when (kind) {
        ControlAppearanceKind.DayNight -> WidgetActiveColors.Primary
        else -> tileTextColor
    }
}

fun defaultControlShapeDpForKind(kind: ControlAppearanceKind): Int {
    return when (kind) {
        ControlAppearanceKind.MusicStepper -> DEFAULT_MUSIC_STEPPER_CONTROL_SHAPE_DP
        else -> 0
    }
}

/**
 * Resolve control colors for paint. [musicStepperBackground] is used when config bg is null
 * for [ControlAppearanceKind.MusicStepper] (typically `surfaceVariant` × α).
 */
fun resolveControlAppearance(
    config: FloatingDashboardWidgetConfig,
    currentTheme: Int,
    tileTextColor: Color,
    kind: ControlAppearanceKind,
    musicStepperBackground: Color = Color.Transparent,
): ResolvedControlColors {
    val dark = currentTheme == 2
    val defaultInactive = defaultInactiveContentForKind(kind, tileTextColor)
    val defaultActive = defaultActiveContentForKind(kind, tileTextColor, config.dataKey)
    val defaultBg = when (kind) {
        ControlAppearanceKind.MusicStepper -> musicStepperBackground
        else -> Color.Transparent
    }
    val inactiveContent = Color(
        (if (dark) config.controlInactiveColorDark else config.controlInactiveColorLight)
            ?: defaultInactive.toArgb()
    )
    val activeContent = Color(
        (if (dark) config.controlActiveColorDark else config.controlActiveColorLight)
            ?: defaultActive.toArgb()
    )
    val inactiveBgInt =
        if (dark) config.controlInactiveBackgroundColorDark else config.controlInactiveBackgroundColorLight
    val activeBgInt =
        if (dark) config.controlActiveBackgroundColorDark else config.controlActiveBackgroundColorLight
    val inactiveBackground = inactiveBgInt?.let { Color(it) } ?: defaultBg
    val activeBackground = activeBgInt?.let { Color(it) } ?: defaultBg
    val shapeDp = (
        config.controlShape?.let { normalizeWidgetControlShape(it) }
            ?: defaultControlShapeDpForKind(kind)
        ).dp
    return ResolvedControlColors(
        inactiveContent = inactiveContent,
        activeContent = activeContent,
        inactiveBackground = inactiveBackground,
        activeBackground = activeBackground,
        shapeDp = shapeDp,
    )
}

/**
 * Seed dialog color editors when user turns off «colors by default».
 * Background for music/stepper uses opaque approximation of the enabled-button fill.
 */
fun seedControlColorsFromDefaults(
    kind: ControlAppearanceKind,
    tileTextColorLight: Int,
    tileTextColorDark: Int,
    musicStepperBgArgb: Int,
    dataKey: String = "",
): ControlColorSeed {
    val inactiveLight = defaultInactiveContentForKind(kind, Color(tileTextColorLight)).toArgb()
    val inactiveDark = defaultInactiveContentForKind(kind, Color(tileTextColorDark)).toArgb()
    val activeLight = defaultActiveContentForKind(kind, Color(tileTextColorLight), dataKey).toArgb()
    val activeDark = defaultActiveContentForKind(kind, Color(tileTextColorDark), dataKey).toArgb()
    val bg = when (kind) {
        ControlAppearanceKind.MusicStepper -> musicStepperBgArgb
        else -> Color.Transparent.toArgb()
    }
    return ControlColorSeed(
        inactiveColorLight = inactiveLight,
        inactiveColorDark = inactiveDark,
        activeColorLight = activeLight,
        activeColorDark = activeDark,
        inactiveBackgroundLight = bg,
        inactiveBackgroundDark = bg,
        activeBackgroundLight = bg,
        activeBackgroundDark = bg,
    )
}

data class ControlColorSeed(
    val inactiveColorLight: Int,
    val inactiveColorDark: Int,
    val activeColorLight: Int,
    val activeColorDark: Int,
    val inactiveBackgroundLight: Int,
    val inactiveBackgroundDark: Int,
    val activeBackgroundLight: Int,
    val activeBackgroundDark: Int,
)

@Composable
fun rememberResolvedControlAppearance(
    config: FloatingDashboardWidgetConfig,
    currentTheme: Int,
    tileTextColor: Color,
    dataKey: String = config.dataKey,
): ResolvedControlColors {
    val kind = controlAppearanceKindForDataKey(dataKey)
    val musicStepperBg = MaterialTheme.colorScheme.surfaceVariant.copy(
        alpha = DEFAULT_MUSIC_STEPPER_CONTROL_BG_ALPHA,
    )
    return remember(config, currentTheme, tileTextColor, kind, musicStepperBg) {
        resolveControlAppearance(
            config = config,
            currentTheme = currentTheme,
            tileTextColor = tileTextColor,
            kind = kind,
            musicStepperBackground = musicStepperBg,
        )
    }
}

@Composable
fun WidgetControlChrome(
    background: Color,
    shapeDp: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(shapeDp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Pick content color for binary on/off; unavailable uses dimmed inactive. */
fun ResolvedControlColors.contentForBinaryOn(on: Boolean, available: Boolean): Color {
    if (!available) return inactiveContent.copy(alpha = 0.25f)
    return if (on) activeContent else inactiveContent
}
