package vad.dashing.tbox.ui

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.HttpRequestWidgetConfig
import vad.dashing.tbox.HttpRequestWidgetResult
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.executeHttpRequestWidget

/**
 * Fire-and-forget runner for HTTP-tile single-tap work (optional launch → delay → HTTP).
 *
 * Must **not** use [androidx.compose.runtime.rememberCoroutineScope] or a floating-panel
 * [androidx.lifecycle.viewModelScope]: launching an app can hide/dispose the overlay
 * ComposeView and clear its ViewModelStore, which would cancel the delay and drop the
 * HTTP request. This process-lifetime scope survives that teardown.
 */
internal object DashboardHttpRequestTileActionRunner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun enqueue(
        launchContext: Context,
        widgetConfig: FloatingDashboardWidgetConfig,
        settingsViewModel: SettingsViewModel,
        requestConfig: HttpRequestWidgetConfig,
        launchAppBeforeRequest: Boolean,
        launchDelayMs: Long,
        onFinished: (HttpRequestWidgetResult) -> Unit,
    ) {
        scope.launch {
            if (launchAppBeforeRequest) {
                runCatching {
                    launchAppFromWidget(launchContext, widgetConfig, settingsViewModel)
                }
                if (launchDelayMs > 0L) {
                    delay(launchDelayMs)
                }
            }
            val result = executeHttpRequestWidget(requestConfig)
            onFinished(result)
        }
    }
}
