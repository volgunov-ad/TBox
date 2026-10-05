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
 * Central head-unit screen backlight, separate from HUD and ICM brightness.
 *
 * A9 stores values in Settings. A10 accesses Adayo's settings Binder at runtime because
 * its SDK JAR is not bundled with the application.
 */
object HeadUnitBrightnessRepository {
    const val SCREEN_BRIGHTNESS_KEY = "screen_brightness"
    const val AUTO_BRIGHTNESS_KEY = "auto_bright"

    private val _brightnessUiLevel = MutableStateFlow<Int?>(null)
    val brightnessUiLevel: StateFlow<Int?> = _brightnessUiLevel.asStateFlow()
    private val _autoBrightness = MutableStateFlow<Boolean?>(null)
    val autoBrightness: StateFlow<Boolean?> = _autoBrightness.asStateFlow()

    private var observer: ContentObserver? = null
    private var observeRefCount = 0

    fun readBrightnessUiLevel(context: Context): Int? =
        if (HeadUnitDayNightMapping.usesAdayoKeys()) {
            AdayoSettingsService.getInt("getSysBacklight")?.let(HeadUnitBrightnessDomain::decodeUiLevel)
                ?: readA9Brightness(context)
        } else {
            readA9Brightness(context)
        }

    fun readAutoBrightness(context: Context): Boolean? =
        if (HeadUnitDayNightMapping.usesAdayoKeys()) {
            AdayoSettingsService.getInt("getDayNightMode")?.let { it == 1 }
        } else {
            runCatching {
                Settings.Global.getInt(context.contentResolver, AUTO_BRIGHTNESS_KEY, 1) == 2
            }.getOrNull()
        }

    fun writeBrightnessUiLevel(context: Context, level: Int): Boolean {
        val raw = HeadUnitBrightnessDomain.encodeRawLevel(level)
        val success = if (HeadUnitDayNightMapping.usesAdayoKeys()) {
            AdayoSettingsService.setInt("setSysBacklight", raw)
        } else {
            runCatching {
                Settings.System.putInt(context.contentResolver, SCREEN_BRIGHTNESS_KEY, raw)
            }.getOrDefault(false)
        }
        if (success) publish(context)
        return success
    }

    fun writeAutoBrightness(context: Context, enabled: Boolean): Boolean {
        val success = if (HeadUnitDayNightMapping.usesAdayoKeys()) {
            // Adayo brightness auto modes: 1 automatic, 4 manual. This is not theme mode.
            AdayoSettingsService.setInt("setDayNightMode", if (enabled) 1 else 4)
        } else {
            runCatching {
                Settings.Global.putInt(context.contentResolver, AUTO_BRIGHTNESS_KEY, if (enabled) 2 else 1)
            }.getOrDefault(false)
        }
        if (success) publish(context)
        return success
    }

    fun isAvailable(context: Context): Boolean =
        !HeadUnitDayNightMapping.usesAdayoKeys() || AdayoSettingsService.isAvailable()

    fun startObserving(context: Context) {
        observeRefCount++
        if (observer != null) {
            publish(context)
            return
        }
        val appContext = context.applicationContext
        val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) = publish(appContext)
        }
        observer = contentObserver
        observedSettingUris().forEach { appContext.contentResolver.registerContentObserver(it, false, contentObserver) }
        publish(appContext)
    }

    fun stopObserving(context: Context) {
        if (observeRefCount <= 0) return
        observeRefCount--
        if (observeRefCount > 0) return
        observer?.let { context.applicationContext.contentResolver.unregisterContentObserver(it) }
        observer = null
        _brightnessUiLevel.value = null
        _autoBrightness.value = null
    }

    fun observedSettingUris(): Set<Uri> = setOf(
        Settings.System.getUriFor(SCREEN_BRIGHTNESS_KEY),
        Settings.Global.getUriFor(AUTO_BRIGHTNESS_KEY),
    )

    private fun readA9Brightness(context: Context): Int? = runCatching {
        Settings.System.getInt(
            context.contentResolver,
            SCREEN_BRIGHTNESS_KEY,
            HeadUnitBrightnessDomain.DEFAULT_RAW,
        )
    }.getOrNull()?.let(HeadUnitBrightnessDomain::decodeUiLevel)

    private fun publish(context: Context) {
        _brightnessUiLevel.value = readBrightnessUiLevel(context)
        _autoBrightness.value = readAutoBrightness(context)
    }
}
