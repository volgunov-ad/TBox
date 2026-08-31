package vad.dashing.tbox

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Parse OEM `avm_state` without touching Android APIs (unit-testable). */
internal object OemOverlayAvmState {
    fun isShowing(raw: String?): Boolean {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return false
        return when (value.lowercase()) {
            "0", "false", "off", "hide", "hidden", "avm_state_hide" -> false
            else -> true
        }
    }
}

/**
 * OEM overlays that never stay as a resumed Activity (Mengbo AVM 360).
 *
 * Visibility is [Settings.Global] `avm_state` (written by `com.mengbo.avm`) plus
 * the `SHOW_AVM_EVENT` broadcast. Merged into [ForegroundAppMonitor] so the
 * existing «Приложение на экране» trigger can select `com.mengbo.avm`.
 */
object OemOverlayAppMonitor {
    const val AVM_PACKAGE = "com.mengbo.avm"
    const val GLOBAL_AVM_STATE = "avm_state"
    const val ACTION_SHOW_AVM_EVENT = "com.mengbo.avm.SHOW_AVM_EVENT"

    private val _packageName = MutableStateFlow<String?>(null)
    val packageName: StateFlow<String?> = _packageName.asStateFlow()

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private var started = false
    private var appContext: Context? = null

    private val settingsObserver by lazy {
        object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean) {
                refreshFromSettings()
            }
        }
    }

    private val avmEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_SHOW_AVM_EVENT) return
            applyBroadcast(intent)
            refreshFromSettings()
        }
    }

    fun start(context: Context) {
        if (started) {
            refreshFromSettings()
            return
        }
        val app = context.applicationContext
        appContext = app
        started = true
        runCatching {
            app.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(GLOBAL_AVM_STATE),
                false,
                settingsObserver,
            )
        }
        val filter = IntentFilter(ACTION_SHOW_AVM_EVENT)
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                app.registerReceiver(avmEventReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                app.registerReceiver(avmEventReceiver, filter)
            }
        }
        refreshFromSettings()
    }

    fun stop() {
        if (!started) return
        val app = appContext
        started = false
        appContext = null
        if (app != null) {
            runCatching { app.contentResolver.unregisterContentObserver(settingsObserver) }
            runCatching { app.unregisterReceiver(avmEventReceiver) }
        }
        _packageName.value = null
    }

    fun refreshFromSettings() {
        val app = appContext ?: return
        publishAvm(OemOverlayAvmState.isShowing(readGlobalAvmState(app)))
    }

    internal fun applyBroadcast(intent: Intent) {
        val extras = intent.extras ?: return
        val showing = when {
            extras.containsKey(GLOBAL_AVM_STATE) ->
                OemOverlayAvmState.isShowing(extras.get(GLOBAL_AVM_STATE)?.toString())
            extras.containsKey("status") ->
                OemOverlayAvmState.isShowing(extras.get("status")?.toString())
            extras.containsKey("isAvmShowing") -> extras.getBoolean("isAvmShowing")
            extras.containsKey("onAvmShow") -> extras.getBoolean("onAvmShow")
            else -> return
        }
        publishAvm(showing)
    }

    private fun readGlobalAvmState(context: Context): String? {
        val resolver = context.contentResolver
        val asString = runCatching { Settings.Global.getString(resolver, GLOBAL_AVM_STATE) }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (asString != null) return asString
        return runCatching { Settings.Global.getInt(resolver, GLOBAL_AVM_STATE, 0).toString() }
            .getOrNull()
    }

    private fun publishAvm(showing: Boolean) {
        _packageName.value = if (showing) AVM_PACKAGE else null
    }
}
