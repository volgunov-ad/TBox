package vad.dashing.tbox.automation

import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import vad.dashing.tbox.ui.AppAlertDialogButtonLabel
import vad.dashing.tbox.ui.MyLifecycleOwner
import vad.dashing.tbox.ui.theme.TboxAppTheme
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.ui.theme.tboxTitle

/**
 * Toast and a blocking overlay dialog for automation actions.
 * The dialog waits until the user taps «Закрыть», [autoCloseMillis] elapses (if > 0),
 * or the job is cancelled.
 */
internal object AutomationUserMessageOverlay {
    suspend fun showToast(context: Context, text: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context.applicationContext, text, Toast.LENGTH_LONG).show()
        }
    }

    suspend fun showCloseableMessage(
        context: Context,
        text: String,
        autoCloseMillis: Long = 0L,
    ): Boolean {
        val appContext = context.applicationContext
        if (!Settings.canDrawOverlays(appContext)) {
            return false
        }
        val dismissed = CompletableDeferred<Unit>()
        var composeView: ComposeView? = null
        var windowManager: WindowManager? = null
        var lifecycleOwner: MyLifecycleOwner? = null
        try {
            withContext(Dispatchers.Main) {
                val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val owner = MyLifecycleOwner().also {
                    it.setCurrentState(Lifecycle.State.CREATED)
                    it.setCurrentState(Lifecycle.State.STARTED)
                    it.setCurrentState(Lifecycle.State.RESUMED)
                }
                val view = ComposeView(appContext).apply {
                    setViewTreeLifecycleOwner(owner)
                    setViewTreeSavedStateRegistryOwner(owner)
                    setViewTreeViewModelStoreOwner(owner)
                    setContent {
                        val night = (
                            appContext.resources.configuration.uiMode and
                                Configuration.UI_MODE_NIGHT_MASK
                            ) == Configuration.UI_MODE_NIGHT_YES
                        TboxAppTheme(theme = if (night) 2 else 1) {
                            AutomationCloseableMessageCard(
                                text = text,
                                autoCloseMillis = autoCloseMillis,
                                onClose = {
                                    if (dismissed.isActive) dismissed.complete(Unit)
                                },
                            )
                        }
                    }
                }
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.CENTER
                    title = "TBox automation message"
                }
                wm.addView(view, params)
                windowManager = wm
                composeView = view
                lifecycleOwner = owner
            }
            if (autoCloseMillis > 0L) {
                withTimeoutOrNull(autoCloseMillis) { dismissed.await() }
                if (dismissed.isActive) dismissed.complete(Unit)
            } else {
                dismissed.await()
            }
            return true
        } finally {
            withContext(NonCancellable + Dispatchers.Main) {
                val view = composeView
                if (view != null) {
                    runCatching {
                        if (view.isAttachedToWindow) windowManager?.removeView(view)
                    }
                    runCatching { view.disposeComposition() }
                }
                lifecycleOwner?.setCurrentState(Lifecycle.State.DESTROYED)
                lifecycleOwner?.clear()
            }
        }
    }
}

@Composable
private fun AutomationCloseableMessageCard(
    text: String,
    autoCloseMillis: Long,
    onClose: () -> Unit,
) {
    val remainingMillis = remainingAutoCloseMillis(autoCloseMillis)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .padding(32.dp)
                .widthIn(min = 360.dp, max = 720.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.tboxTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (autoCloseMillis > 0L) {
                        Arrangement.SpaceBetween
                    } else {
                        Arrangement.End
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (autoCloseMillis > 0L) {
                        Text(
                            text = formatAutoCloseCountdown(remainingMillis),
                            style = MaterialTheme.typography.tboxCaption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp),
                        )
                    }
                    Button(onClick = onClose) {
                        AppAlertDialogButtonLabel("Закрыть")
                    }
                }
            }
        }
    }
}

@Composable
private fun remainingAutoCloseMillis(autoCloseMillis: Long): Long {
    if (autoCloseMillis <= 0L) return 0L
    val deadlineElapsed = remember(autoCloseMillis) {
        SystemClock.elapsedRealtime() + autoCloseMillis
    }
    var remaining by remember(autoCloseMillis) { mutableLongStateOf(autoCloseMillis) }
    LaunchedEffect(autoCloseMillis) {
        while (true) {
            val left = (deadlineElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            remaining = left
            if (left <= 0L) break
            val step = if (left > 1_000L) 250L else 100L
            delay(step.coerceAtMost(left))
        }
    }
    return remaining
}

internal fun formatAutoCloseCountdown(remainingMillis: Long): String {
    val seconds = (remainingMillis.coerceAtLeast(0L) + 999L) / 1000L
    return if (seconds <= 0L) {
        "Закроется автоматически"
    } else {
        "Автозакрытие через $seconds с"
    }
}
