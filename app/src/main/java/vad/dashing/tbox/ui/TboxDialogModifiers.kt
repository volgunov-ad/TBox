package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier

/**
 * Shared outer size for custom-width dialogs
 * (`DialogProperties(usePlatformDefaultWidth = false)`).
 *
 * Platform-default [androidx.compose.material3.AlertDialog] confirms stay unchanged.
 */
object TboxDialogSize {
    const val WidthFraction = 0.94f
    const val HeightFraction = 0.92f
}

/** Outer dialog surface: [TboxDialogSize.WidthFraction] × [TboxDialogSize.HeightFraction]. */
fun Modifier.tboxDialogSurface(): Modifier =
    this
        .fillMaxWidth(TboxDialogSize.WidthFraction)
        .fillMaxHeight(TboxDialogSize.HeightFraction)
