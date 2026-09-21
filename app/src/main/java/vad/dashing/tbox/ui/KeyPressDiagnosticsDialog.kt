package vad.dashing.tbox.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.concurrent.atomic.AtomicBoolean
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.Android10VhalRepository
import vad.dashing.tbox.mbcan.KeyPressDiagnosticFormat
import vad.dashing.tbox.mbcan.KeyPressDiagnosticLog
import vad.dashing.tbox.mbcan.MbCanRepository
import vad.dashing.tbox.mbcan.formatDiagnosticClock
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxCaption

/** Survives dialog close/open within the app process (one session); «Очистить» resets it. */
private var sessionLog by mutableStateOf(KeyPressDiagnosticLog())

@Composable
fun KeyPressDiagnosticsDialog(
    visible: Boolean,
    mode: HeadUnitCanMode,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val context = LocalContext.current
    val clipboardLabel = stringResource(R.string.key_press_diagnostics_title)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val focusRequester = remember { FocusRequester() }
    val sessionActive = remember(mode) { AtomicBoolean(true) }

    fun append(message: String) {
        val apply = {
            if (sessionActive.get()) {
                sessionLog = sessionLog.append("${formatDiagnosticClock(SystemClock.elapsedRealtime())} $message")
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) apply() else mainHandler.post(apply)
    }

    LaunchedEffect(mode) {
        sessionActive.set(true)
        append("mode=${mode.storageValue}")
        when (mode) {
            HeadUnitCanMode.Android9MbCan -> {
                MbCanRepository.startHardKeyDiagnostics { keyCode, keyStatus, keyType ->
                    append(KeyPressDiagnosticFormat.mbCan(keyCode, keyStatus, keyType))
                }.fold(
                    onSuccess = { append("A9 mbCAN subscribe OK") },
                    onFailure = {
                        append(KeyPressDiagnosticFormat.error("A9 mbCAN subscribe", "${it.javaClass.simpleName}: ${it.message}"))
                    },
                )
            }
            HeadUnitCanMode.Android10Vhal -> {
                Android10VhalRepository.startKeyDiagnostics(
                    onEvent = { append(KeyPressDiagnosticFormat.vhal(it)) },
                    onError = { propertyId, areaId ->
                        append(KeyPressDiagnosticFormat.error("A10 VHAL", "propertyId=$propertyId areaId=$areaId"))
                    },
                ).forEach { append(KeyPressDiagnosticFormat.subscription(it)) }
            }
        }
    }

    DisposableEffect(mode) {
        onDispose {
            sessionActive.set(false)
            when (mode) {
                HeadUnitCanMode.Android9MbCan -> MbCanRepository.stopHardKeyDiagnosticsAsync()
                HeadUnitCanMode.Android10Vhal -> Android10VhalRepository.stopKeyDiagnosticsAsync()
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f)
                .onPreviewKeyEvent { event ->
                    append(
                        KeyPressDiagnosticFormat.android(
                            action = if (event.type == KeyEventType.KeyDown) "DOWN" else "UP",
                            keyCode = event.key.keyCode,
                            nativeEvent = event.nativeKeyEvent,
                        )
                    )
                    false
                }
                .focusRequester(focusRequester)
                .focusable(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                AppAlertDialogTitle(stringResource(R.string.key_press_diagnostics_title))
                Text(
                    text = stringResource(R.string.key_press_diagnostics_desc),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                )
                if (sessionLog.lines.isEmpty()) {
                    Text(
                        text = stringResource(R.string.key_press_diagnostics_empty),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    SelectionContainer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(sessionLog.lines.asReversed()) { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.tboxCaption.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = rememberWrappedOnClick { sessionLog = sessionLog.clear() },
                        enabled = sessionLog.lines.isNotEmpty(),
                    ) {
                        AppAlertDialogButtonLabel(stringResource(R.string.action_clear))
                    }
                    OutlinedButton(
                        onClick = rememberWrappedOnClick {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText(clipboardLabel, sessionLog.asText())
                            )
                            Toast.makeText(context, R.string.key_press_diagnostics_copied, Toast.LENGTH_SHORT).show()
                        },
                        enabled = sessionLog.lines.isNotEmpty(),
                    ) {
                        AppAlertDialogButtonLabel(stringResource(R.string.action_copy))
                    }
                    Button(
                        onClick = rememberWrappedOnClick(onDismiss),
                        modifier = Modifier.weight(1f),
                    ) {
                        AppAlertDialogButtonLabel(stringResource(R.string.action_close))
                    }
                }
            }
        }
    }
}
