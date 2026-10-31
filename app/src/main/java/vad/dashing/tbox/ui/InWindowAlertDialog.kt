package vad.dashing.tbox.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private const val IN_WINDOW_DIALOG_SCRIM_ALPHA = 0.32f

/**
 * [androidx.compose.material3.AlertDialog] look-alike drawn inside the host composition instead
 * of a separate dialog window. Use where the host Activity window may not have a valid token yet
 * (A10 Adayo ActivityView reparenting throws BadTokenException from Dialog.show()).
 * Must be the last child of a full-size Box so it draws over and blocks the content below.
 */
@Composable
fun InWindowAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
) {
    BackHandler(onBack = onDismissRequest)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = IN_WINDOW_DIALOG_SCRIM_ALPHA))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                ),
        )
        Surface(
            modifier = modifier,
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    CompositionLocalProvider(
                        LocalContentColor provides AlertDialogDefaults.textContentColor,
                    ) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium, text)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    confirmButton()
                }
            }
        }
    }
}
