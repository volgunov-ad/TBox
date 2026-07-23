package vad.dashing.tbox

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.UniversalCanRepository

/**
 * Mirror steering-wheel adjustment mode (stock A9 `ro.mb.mirror.adjust.mode`,
 * A10 `mirrorAdjustment`). Not a CAN property — enables D-pad mirror control in firmware.
 *
 * «Android 10» here is the Adayo/VHAL HU product line; [Build.VERSION.SDK_INT] may still be 28.
 */
object MirrorAdjustModeRepository {
    private const val MIRROR_ADJUST_MODE_GLOBAL_KEY = "ro.mb.mirror.adjust.mode"
    private const val MIRROR_ADJUST_MODE_SYSTEM_KEY = "mirrorAdjustment"

    private val _mirrorAdjustModeState = MutableStateFlow<MbCanBinaryState>(MbCanBinaryState.Unknown)
    val mirrorAdjustModeState: StateFlow<MbCanBinaryState> = _mirrorAdjustModeState.asStateFlow()

    private var observer: ContentObserver? = null
    private var observedUri: Uri? = null
    private var observeRefCount = 0

    private fun usesSystemSettingsKey(): Boolean {
        if (UniversalCanRepository.mode.value == HeadUnitCanMode.Android10Vhal) return true
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    fun readMirrorAdjustModeEnabled(context: Context): Boolean {
        return runCatching {
            if (usesSystemSettingsKey()) {
                Settings.System.getInt(context.contentResolver, MIRROR_ADJUST_MODE_SYSTEM_KEY, 0) == 1
            } else {
                Settings.Global.getInt(context.contentResolver, MIRROR_ADJUST_MODE_GLOBAL_KEY, 0) == 1
            }
        }.getOrDefault(false)
    }

    fun toggleMirrorAdjustMode(context: Context): Boolean {
        val target = if (readMirrorAdjustModeEnabled(context)) 0 else 1
        return writeMirrorAdjustMode(context, target).also {
            if (it) {
                publishState(target == 1)
            }
        }
    }

    fun writeMirrorAdjustMode(context: Context, value: Int): Boolean {
        val normalized = if (value != 0) 1 else 0
        return runCatching {
            if (usesSystemSettingsKey()) {
                Settings.System.putInt(context.contentResolver, MIRROR_ADJUST_MODE_SYSTEM_KEY, normalized)
            } else {
                Settings.Global.putInt(context.contentResolver, MIRROR_ADJUST_MODE_GLOBAL_KEY, normalized)
            }
        }.getOrDefault(false)
    }

    fun startObserving(context: Context) {
        observeRefCount++
        if (observer != null) {
            publishFromContext(context)
            return
        }
        val appContext = context.applicationContext
        val uri = if (usesSystemSettingsKey()) {
            Settings.System.getUriFor(MIRROR_ADJUST_MODE_SYSTEM_KEY)
        } else {
            Settings.Global.getUriFor(MIRROR_ADJUST_MODE_GLOBAL_KEY)
        }
        observedUri = uri
        val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                if (uri == null || uri == observedUri) {
                    publishFromContext(appContext)
                }
            }
        }
        observer = contentObserver
        appContext.contentResolver.registerContentObserver(uri, false, contentObserver)
        publishFromContext(appContext)
    }

    fun stopObserving(context: Context) {
        if (observeRefCount <= 0) return
        observeRefCount--
        if (observeRefCount > 0) return
        observer?.let { context.applicationContext.contentResolver.unregisterContentObserver(it) }
        observer = null
        observedUri = null
        _mirrorAdjustModeState.value = MbCanBinaryState.Unknown
    }

    private fun publishFromContext(context: Context) {
        publishState(readMirrorAdjustModeEnabled(context))
    }

    private fun publishState(enabled: Boolean) {
        _mirrorAdjustModeState.value = if (enabled) MbCanBinaryState.On else MbCanBinaryState.Off
    }
}
