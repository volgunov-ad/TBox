package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Outer sizes for custom-width dialogs
 * (`DialogProperties(usePlatformDefaultWidth = false)`).
 *
 * Platform-default [androidx.compose.material3.AlertDialog] confirms stay unchanged.
 *
 * Height policy: wrap content up to [HeightFraction] of the screen
 * (`heightIn(max)`), so short dialogs do not leave empty bottom space.
 * Use [tboxDialogSurfaceFill] / [tboxDialogSurfaceCompactFill] when the body is
 * an always-tall scrollable list (Um980, UiIconSettings, AppList, CanCompanion, …)
 * so Close/actions stay pinned: `Column(fillMaxSize)` + middle `weight(1f)`.
 *
 * Sticky footer for **wrap** [Dialog]+[Surface] columns (short menus): put the
 * scrollable middle on `modifier.weight(1f, fill = false)`. `fill = false` keeps
 * short content wrapping; when content exceeds the surface max, the middle
 * absorbs remaining height and scrolls so actions stay visible.
 * Avoid stacking `heightIn(max ≈ screen×HeightFraction)` on the body — title +
 * body + buttons then exceed the surface max and clip the action row.
 */
object TboxDialogSize {
    /** Default/Large custom dialogs (Expert, AppList, maps, pickers, …). */
    const val WidthFraction = 0.94f
    const val HeightFraction = 0.92f

    /** Compact custom dialogs (short labels: left menu, panel order, trip widget). */
    const val CompactWidthFraction = 0.6f

    /**
     * Title + paddings + action row reserved when capping an AlertDialog text
     * body with [tboxDialogScrollBodyMaxHeight] (surface stays under
     * [HeightFraction] of the screen with confirm/dismiss still visible).
     */
    const val ScrollBodyChromeReserveDp = 200
}

/** Max dialog height: [TboxDialogSize.HeightFraction] × screen height. */
@Composable
fun tboxDialogMaxHeight(): Dp {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    return (screenHeightDp * TboxDialogSize.HeightFraction).dp
}

/**
 * Max height for a scrollable body inside a wrap-height dialog
 * (screen×[TboxDialogSize.HeightFraction] minus chrome reserve).
 */
@Composable
fun tboxDialogScrollBodyMaxHeight(): Dp {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val raw = screenHeightDp * TboxDialogSize.HeightFraction -
        TboxDialogSize.ScrollBodyChromeReserveDp
    return raw.coerceAtLeast(120f).dp
}

/**
 * Default/Large outer surface: width [TboxDialogSize.WidthFraction],
 * height wraps content up to [TboxDialogSize.HeightFraction] of the screen.
 */
@Composable
fun Modifier.tboxDialogSurface(): Modifier =
    this
        .fillMaxWidth(TboxDialogSize.WidthFraction)
        .heightIn(max = tboxDialogMaxHeight())

/**
 * Compact outer surface: width [TboxDialogSize.CompactWidthFraction],
 * height wraps content up to [TboxDialogSize.HeightFraction] of the screen.
 */
@Composable
fun Modifier.tboxDialogSurfaceCompact(): Modifier =
    this
        .fillMaxWidth(TboxDialogSize.CompactWidthFraction)
        .heightIn(max = tboxDialogMaxHeight())

/**
 * Large surface that always claims [TboxDialogSize.HeightFraction] of the screen
 * (scrollable list dialogs: AppList, Um980, UiIconSettings, CanCompanion,
 * KeyPress, road tuning, widget pickers, …).
 */
@Composable
fun Modifier.tboxDialogSurfaceFill(): Modifier =
    this
        .fillMaxWidth(TboxDialogSize.WidthFraction)
        .fillMaxHeight(TboxDialogSize.HeightFraction)

/**
 * Compact surface that always claims [TboxDialogSize.HeightFraction] of the screen.
 * Prefer [tboxDialogSurfaceCompact] unless the body intentionally fills height.
 */
@Composable
fun Modifier.tboxDialogSurfaceCompactFill(): Modifier =
    this
        .fillMaxWidth(TboxDialogSize.CompactWidthFraction)
        .fillMaxHeight(TboxDialogSize.HeightFraction)
