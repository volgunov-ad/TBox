package vad.dashing.tbox

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vad.dashing.tbox.mbcan.MbCanEngineFacade
import vad.dashing.tbox.mbcan.MbCanKnownAudioPropertyId

/**
 * Media / phone / navi / voice volumes and headrest speaker — stock mixer, not mbCAN/VHAL.
 *
 * A10: Adayo `SettingsSvcIfManager`. A9: OpenOS volume groups, then `AudioManager` streams.
 * Headrest on A9 uses mbCAN audio property 37 (the only stock write path; SystemSettings hides the row).
 */
object PlatformAudioRepository {
    private const val TAG = "PlatformAudio"
    private const val POLL_MS = 350L

    private val _mediaVolume = MutableStateFlow<Int?>(null)
    val mediaVolume: StateFlow<Int?> = _mediaVolume.asStateFlow()
    private val _phoneVolume = MutableStateFlow<Int?>(null)
    val phoneVolume: StateFlow<Int?> = _phoneVolume.asStateFlow()
    private val _naviVolume = MutableStateFlow<Int?>(null)
    val naviVolume: StateFlow<Int?> = _naviVolume.asStateFlow()
    private val _voiceVolume = MutableStateFlow<Int?>(null)
    val voiceVolume: StateFlow<Int?> = _voiceVolume.asStateFlow()
    private val _headrestMode = MutableStateFlow<Int?>(null)
    val headrestMode: StateFlow<Int?> = _headrestMode.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var observeRefCount = 0
    private var appContext: Context? = null
    private var pollRunnable: Runnable? = null
    private var lastNonZeroMedia: Int = 10

    fun startObserving(context: Context) {
        observeRefCount++
        appContext = context.applicationContext
        if (observeRefCount == 1) {
            val runnable = object : Runnable {
                override fun run() {
                    publish()
                    mainHandler.postDelayed(this, POLL_MS)
                }
            }
            pollRunnable = runnable
            publish()
            mainHandler.postDelayed(runnable, POLL_MS)
        } else {
            publish()
        }
    }

    fun stopObserving() {
        if (observeRefCount <= 0) return
        observeRefCount--
        if (observeRefCount > 0) return
        pollRunnable?.let { mainHandler.removeCallbacks(it) }
        pollRunnable = null
        appContext = null
        _mediaVolume.value = null
        _phoneVolume.value = null
        _naviVolume.value = null
        _voiceVolume.value = null
        _headrestMode.value = null
    }

    fun mediaVolumeRestoreCandidate(): Int = lastNonZeroMedia.coerceAtLeast(1)

    fun setVolume(channel: PlatformAudioDomain.VolumeChannel, value: Int): Boolean {
        val target = PlatformAudioDomain.sanitizeVolume(channel, value) ?: return false
        val ok = if (HeadUnitDayNightMapping.usesAdayoKeys()) {
            writeA10Volume(channel, target)
        } else {
            writeA9Volume(channel, target)
        }
        if (ok) {
            flowFor(channel).value = target
            if (channel == PlatformAudioDomain.VolumeChannel.Media && target > 0) {
                lastNonZeroMedia = target
            }
        }
        return ok
    }

    fun setHeadrestMode(uiValue: Int): Boolean {
        val ok = if (HeadUnitDayNightMapping.usesAdayoKeys()) {
            if (PlatformAudioDomain.decodeHeadrestVhal(uiValue) == null) return false
            AdayoSettingsService.setInt("setHeadrestSpeakerMode", uiValue)
        } else {
            val raw = PlatformAudioDomain.encodeHeadrestMbCan(uiValue) ?: return false
            val result = MbCanEngineFacade.canSetAudioParam(MbCanKnownAudioPropertyId.HEADREST_SPEAKER, raw)
            result != null && result >= 0
        }
        if (ok) _headrestMode.value = uiValue
        return ok
    }

    private fun publish() {
        PlatformAudioDomain.VolumeChannel.entries.forEach { channel ->
            val value = readVolume(channel)
            flowFor(channel).value = value
            if (channel == PlatformAudioDomain.VolumeChannel.Media && value != null && value > 0) {
                lastNonZeroMedia = value
            }
        }
        _headrestMode.value = readHeadrest()
    }

    private fun readVolume(channel: PlatformAudioDomain.VolumeChannel): Int? {
        val raw = if (HeadUnitDayNightMapping.usesAdayoKeys()) {
            AdayoSettingsService.getInt("getAudioStreamVolume", PlatformAudioDomain.a10StreamType(channel))
        } else {
            readA9Volume(channel)
        } ?: return null
        return PlatformAudioDomain.sanitizeVolume(channel, raw)
    }

    private fun readHeadrest(): Int? = if (HeadUnitDayNightMapping.usesAdayoKeys()) {
        AdayoSettingsService.getInt("getHeadrestSpeakerMode")
            ?.let(PlatformAudioDomain::decodeHeadrestVhal)
    } else {
        MbCanEngineFacade.canGetAudioParam(MbCanKnownAudioPropertyId.HEADREST_SPEAKER)
            ?.let(PlatformAudioDomain::decodeHeadrestMbCan)
    }

    private fun writeA10Volume(channel: PlatformAudioDomain.VolumeChannel, value: Int): Boolean =
        AdayoSettingsService.setInt(
            "setAudioStreamVolume",
            PlatformAudioDomain.a10StreamType(channel),
            value,
        )

    private fun writeA9Volume(channel: PlatformAudioDomain.VolumeChannel, value: Int): Boolean {
        if (OpenOsAudio.setVolume(channel, value)) return true
        val context = appContext ?: return false
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val stream = PlatformAudioDomain.a9FallbackStream(channel)
        return runCatching {
            audioManager.setStreamVolume(stream, value.coerceIn(0, audioManager.getStreamMaxVolume(stream)), 0)
            true
        }.getOrDefault(false)
    }

    private fun readA9Volume(channel: PlatformAudioDomain.VolumeChannel): Int? {
        OpenOsAudio.getVolume(channel)?.let { return it }
        val context = appContext ?: return null
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        val stream = PlatformAudioDomain.a9FallbackStream(channel)
        return runCatching { audioManager.getStreamVolume(stream) }.getOrNull()
    }

    private fun flowFor(channel: PlatformAudioDomain.VolumeChannel): MutableStateFlow<Int?> = when (channel) {
        PlatformAudioDomain.VolumeChannel.Media -> _mediaVolume
        PlatformAudioDomain.VolumeChannel.Phone -> _phoneVolume
        PlatformAudioDomain.VolumeChannel.Navi -> _naviVolume
        PlatformAudioDomain.VolumeChannel.Voice -> _voiceVolume
    }

    private object OpenOsAudio {
        @Volatile
        private var manager: Any? = null

        fun getVolume(channel: PlatformAudioDomain.VolumeChannel): Int? = runCatching {
            val audio = manager() ?: return null
            val groupId = groupId(audio, channel) ?: return null
            invokeInt(audio, "getGroupVolume", groupId)
        }.onFailure { Log.w(TAG, "OpenOS getVolume failed", it) }.getOrNull()

        fun setVolume(channel: PlatformAudioDomain.VolumeChannel, value: Int): Boolean = runCatching {
            val audio = manager() ?: return false
            val groupId = groupId(audio, channel) ?: return false
            val method = audio.javaClass.methods.firstOrNull {
                it.name == "setGroupVolume" && it.parameterTypes.size == 3
            } ?: return false
            method.invoke(audio, groupId, value, 0)
            true
        }.onFailure { Log.w(TAG, "OpenOS setVolume failed", it) }.getOrDefault(false)

        private fun groupId(audio: Any, channel: PlatformAudioDomain.VolumeChannel): Int? =
            invokeInt(audio, "getVolumeGroupIdForUsage", PlatformAudioDomain.a9Usage(channel))

        private fun invokeInt(target: Any, name: String, arg: Int): Int? {
            val method = target.javaClass.methods.firstOrNull {
                it.name == name && it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
            } ?: return null
            return (method.invoke(target, arg) as? Number)?.toInt()
        }

        private fun manager(): Any? {
            manager?.let { return it }
            val context = appContext ?: return null
            val created = runCatching {
                val openOsClass = Class.forName("com.openos.OpenOSContext")
                val instance = openOsClass.getField("INSTANCE").get(null)
                val audioCtx = Class.forName("com.openos.ManagerContext\$AudioManagerContext")
                    .getField("INSTANCE")
                    .get(null)
                openOsClass.methods.firstOrNull { it.name == "initContext" && it.parameterTypes.size == 2 }
                    ?.invoke(instance, context, audioCtx)
                openOsClass.methods.firstOrNull { it.name == "getManager" && it.parameterTypes.size == 1 }
                    ?.invoke(instance, audioCtx)
            }.onFailure { Log.w(TAG, "OpenOS AudioManager unavailable", it) }.getOrNull()
            manager = created
            return created
        }
    }
}
