package vad.dashing.tbox.dcl

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds the latest DCL UI snapshot from push broadcasts and optional poll refreshes.
 * Does not yet drive floating-panel hide automations — callers observe [uiState].
 */
object DclLauncherApiRepository {
    private const val TAG = "DclLauncherApi"

    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _uiState = MutableStateFlow<DclUiState?>(null)
    val uiState: StateFlow<DclUiState?> = _uiState.asStateFlow()

    private val _capabilities = MutableStateFlow<DclCapabilities?>(null)
    val capabilities: StateFlow<DclCapabilities?> = _capabilities.asStateFlow()

    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    @Volatile
    private var appContext: Context? = null

    private var pushReceiver: BroadcastReceiver? = null

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        appContext = app
        registerPushReceiver(app)
        scope.launch { refreshBlocking(app) }
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) return
        val app = appContext
        val receiver = pushReceiver
        pushReceiver = null
        if (app != null && receiver != null) {
            runCatching { app.unregisterReceiver(receiver) }
        }
        appContext = null
    }

    /** Manifest [DclUiStateReceiver] and the dynamic receiver both land here. */
    fun onUiStateBroadcast(intent: Intent) {
        if (!isUiStateAction(intent.action)) return
        val state = DclLauncherApiCodec.parseUiStateFromExtras(intent.extras) ?: return
        publishUiState(state)
    }

    suspend fun refresh(context: Context = requireNotNull(appContext)): Boolean =
        withContext(Dispatchers.IO) { refreshBlocking(context.applicationContext) }

    suspend fun launchApp(
        packageName: String,
        mode: String = DclLauncherApiContract.MODE_APP_AREA,
        activityClass: String? = null,
        context: Context = requireNotNull(appContext),
    ): DclCallResult<DclLaunchResult> =
        DclLauncherApiClient.launchApp(
            context = context.applicationContext,
            packageName = packageName,
            mode = mode,
            activityClass = activityClass,
        )

    fun isUiStateAction(action: String?): Boolean =
        action == DclLauncherApiContract.ACTION_UI_STATE_CHANGED ||
            action == DclLauncherApiContract.ACTION_UI_CHROME_CHANGED

    private fun refreshBlocking(context: Context): Boolean {
        when (val caps = DclLauncherApiClient.getCapabilitiesBlocking(context)) {
            is DclCallResult.Ok -> {
                _capabilities.value = caps.value
                _available.value = true
            }
            is DclCallResult.Error -> {
                Log.w(TAG, "getCapabilities error: ${caps.error.errorCode} ${caps.error.message}")
                _available.value = true
            }
            DclCallResult.Unavailable -> {
                _available.value = false
                return false
            }
        }
        when (val state = DclLauncherApiClient.getUiStateBlocking(context)) {
            is DclCallResult.Ok -> publishUiState(state.value)
            is DclCallResult.Error ->
                Log.w(TAG, "getUiState error: ${state.error.errorCode} ${state.error.message}")
            DclCallResult.Unavailable -> {
                _available.value = false
                return false
            }
        }
        return true
    }

    private fun publishUiState(state: DclUiState) {
        val current = _uiState.value
        if (current != null && state.revision > 0L && state.revision < current.revision) {
            // Ignore stale push that arrived out of order.
            return
        }
        _uiState.value = state
        _available.value = true
    }

    private fun registerPushReceiver(context: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                onUiStateBroadcast(intent)
            }
        }
        val filter = IntentFilter().apply {
            addAction(DclLauncherApiContract.ACTION_UI_STATE_CHANGED)
            addAction(DclLauncherApiContract.ACTION_UI_CHROME_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        pushReceiver = receiver
    }

}
