package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Outer sizes for custom-width dialogs
 * (`DialogProperties(usePlatformDefaultWidth = false)`).
 *
 * Platform-default [androidx.compose.material3.AlertDialog] confirms stay unchanged.
 */
object TboxDialogSize {
    /** Default/Large custom dialogs (Expert, AppList, maps, pickers, …). */
    const val WidthFraction = 0.94f
    const val HeightFraction = 0.92f

    /** Compact custom dialogs (short labels: left menu, panel order, trip widget). */
    const val CompactWidthFraction = 0.6f
    val CompactMaxHeight = 800.dp
}

/** Default/Large outer surface: [TboxDialogSize.WidthFraction] × [TboxDialogSize.HeightFraction]. */
fun Modifier.tboxDialogSurface(): Modifier =
    this
        .fillMaxWidth(TboxDialogSize.WidthFraction)
        .fillMaxHeight(TboxDialogSize.HeightFraction)

/**
 * Compact outer surface: [TboxDialogSize.CompactWidthFraction] wide, height by content
 * (capped at [TboxDialogSize.CompactMaxHeight]).
 */
fun Modifier.tboxDialogSurfaceCompact(): Modifier =
    this
        .fillMaxWidth(TboxDialogSize.CompactWidthFraction)
        .heightIn(max = TboxDialogSize.CompactMaxHeight)
