package vad.dashing.tbox

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Head-unit day/night theme (stock `com.mb.provider.night_mode_auto` + `DAY_NIGHT_STATUS`).
 * Same keys as [vad.dashing.tbox.utils.ThemeObserver].
 */
object HeadUnitDayNightRepository {
    const val NIGHT_MODE_AUTO_KEY = "com.mb.provider.night_mode_auto"
    const val DAY_NIGHT_STATUS_KEY = "DAY_NIGHT_STATUS"

    const val NIGHT_MODE_LIGHT_MANUAL = 0
    const val NIGHT_MODE_AUTO = 1
    const val NIGHT_MODE_DARK_MANUAL = 2

    const val DAY_NIGHT_LIGHT = 1
    const val DAY_NIGHT_DARK = 2

    enum class Mode {
        LightManual,
        LightAuto,
        DarkManual,
        DarkAuto,
    }

    private val _modeState = MutableStateFlow<Mode?>(null)
    val modeState: StateFlow<Mode?> = _modeState.asStateFlow()

    private var observer: ContentObserver? = null
    private var observedUris: Set<Uri> = emptySet()
    private var observeRefCount = 0

    fun readMode(context: Context): Mode {
        val autoMode = readAutoMode(context)
        return when (autoMode) {
            NIGHT_MODE_LIGHT_MANUAL -> Mode.LightManual
            NIGHT_MODE_DARK_MANUAL -> Mode.DarkManual
            else -> if (readDayNightStatus(context) == DAY_NIGHT_DARK) {
                Mode.DarkAuto
            } else {
                Mode.LightAuto
            }
        }
    }

    fun cycleMode(context: Context): Boolean {
        val nextAutoValue = when (readMode(context)) {
            Mode.LightManual -> NIGHT_MODE_AUTO
            Mode.LightAuto -> NIGHT_MODE_DARK_MANUAL
            Mode.DarkManual -> NIGHT_MODE_AUTO
            Mode.DarkAuto -> NIGHT_MODE_LIGHT_MANUAL
        }
        return writeAutoMode(context, nextAutoValue).also { success ->
            if (success) {
                publishFromContext(context)
            }
        }
    }

    fun writeAutoMode(context: Context, value: Int): Boolean {
        return runCatching {
            Settings.Global.putInt(context.contentResolver, NIGHT_MODE_AUTO_KEY, value)
        }.getOrDefault(false)
    }

    fun startObserving(context: Context) {
        observeRefCount++
        if (observer != null) {
            publishFromContext(context)
            return
        }
        val appContext = context.applicationContext
        val uris = setOf(
            Settings.Global.getUriFor(NIGHT_MODE_AUTO_KEY),
            Settings.System.getUriFor(DAY_NIGHT_STATUS_KEY),
        )
        observedUris = uris
        val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                if (uri == null || uri in observedUris) {
                    publishFromContext(appContext)
                }
            }
        }
        observer = contentObserver
        val resolver = appContext.contentResolver
        uris.forEach { uri ->
            resolver.registerContentObserver(uri, false, contentObserver)
        }
        publishFromContext(appContext)
    }

    fun stopObserving(context: Context) {
        if (observeRefCount <= 0) return
        observeRefCount--
        if (observeRefCount > 0) return
        observer?.let { context.applicationContext.contentResolver.unregisterContentObserver(it) }
        observer = null
        observedUris = emptySet()
        _modeState.value = null
    }

    private fun readAutoMode(context: Context): Int {
        return runCatching {
            Settings.Global.getInt(context.contentResolver, NIGHT_MODE_AUTO_KEY, NIGHT_MODE_AUTO)
        }.getOrDefault(NIGHT_MODE_AUTO)
    }

    private fun readDayNightStatus(context: Context): Int {
        return runCatching {
            Settings.System.getInt(context.contentResolver, DAY_NIGHT_STATUS_KEY, DAY_NIGHT_LIGHT)
        }.getOrDefault(DAY_NIGHT_LIGHT)
    }

    private fun publishFromContext(context: Context) {
        _modeState.value = readMode(context)
    }
}
