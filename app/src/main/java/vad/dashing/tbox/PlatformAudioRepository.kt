package vad.dashing.tbox

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vad.dashing.tbox.mbcan.MbCanEngineFacade
import vad.dashing.tbox.mbcan.MbCanKnownAudioPropertyId
import vad.dashing.tbox.mbcan.MbCanRepository

/**
 * Media / phone / navi / voice volumes (stock mixer) and headrest speaker.
 *
 * A10: Adayo `SettingsSvcIfManager`. A9: OpenOS volume groups, then `AudioManager` streams.
 * Headrest on A9 uses mbCAN audio property 37 (the only stock write path; SystemSettings hides the row).
 * Headrest is polled like other mbCAN/VHAL signals (30 s, 1.5 s burst after a write), not with mixer volume.
 */
object PlatformAudioRepository {
    private const val TAG = "PlatformAudio"
    /**
     * Mixer volumes (OpenOS / AudioManager / Adayo) — not mbCAN.
     * 500 ms tracks steering-wheel / stock volume while the widget or Car Settings is open
     * without the old 350 ms busy-loop. 1 s feels laggy; dropping the poll freezes the slider.
     */
    private const val VOLUME_POLL_MS = 500L
    /** Same cadence as [vad.dashing.tbox.mbcan.MbCanJobManager] / A10 VHAL signal poll. */
    private const val HEADREST_NORMAL_POLL_MS = 30_000L
    private const val HEADREST_BURST_POLL_MS = 1_500L
    private const val HEADREST_BURST_DURATION_MS = 15_000L

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
    private var volumePollRunnable: Runnable? = null
    private var headrestPollRunnable: Runnable? = null
    private var headrestBurstUntilElapsedMs: Long = 0L
    private var lastNonZeroMedia: Int = 10
    @Volatile private var observeGeneration: Int = 0

    fun startObserving(context: Context) {
        observeRefCount++
        appContext = context.applicationContext
        if (observeRefCount == 1) {
            val volumeRunnable = object : Runnable {
                override fun run() {
                    publishVolumes()
                    mainHandler.postDelayed(this, VOLUME_POLL_MS)
                }
            }
            volumePollRunnable = volumeRunnable
            val headrestRunnable = object : Runnable {
                override fun run() {
                    publishHeadrest()
                    mainHandler.postDelayed(this, nextHeadrestDelayMs())
                }
            }
            headrestPollRunnable = headrestRunnable
            publishVolumes()
            publishHeadrest()
            mainHandler.postDelayed(volumeRunnable, VOLUME_POLL_MS)
            mainHandler.postDelayed(headrestRunnable, nextHeadrestDelayMs())
        } else {
            publishVolumes()
        }
    }

    fun stopObserving() {
        if (observeRefCount <= 0) return
        observeRefCount--
        if (observeRefCount > 0) return
        volumePollRunnable?.let { mainHandler.removeCallbacks(it) }
        headrestPollRunnable?.let { mainHandler.removeCallbacks(it) }
        volumePollRunnable = null
        headrestPollRunnable = null
        headrestBurstUntilElapsedMs = 0L
        observeGeneration++
        appContext = null
        _mediaVolume.value = null
        _phoneVolume.value = null
        _naviVolume.value = null
        _voiceVolume.value = null
        _headrestMode.value = null
    }

    fun mediaVolumeRestoreCandidate(): Int = lastNonZeroMedia.coerceAtLeast(1)

    fun adjustVolume(channel: PlatformAudioDomain.VolumeChannel, increase: Boolean): Boolean {
        val live = flowFor(channel).value ?: readVolume(channel)
        val next = PlatformAudioDomain.nextVolume(channel, live, increase) ?: return false
        return setVolume(channel, next)
    }

    fun setVolume(channel: PlatformAudioDomain.VolumeChannel, value: Int): Boolean {
        val target = PlatformAudioDomain.sanitizeVolume(channel, value) ?: return false
        val previous = flowFor(channel).value
        if (channel == PlatformAudioDomain.VolumeChannel.Media &&
            target > 0 &&
            (previous == null || previous == 0)
        ) {
            unmuteMediaStream()
        }
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
            _headrestMode.value = uiValue
            beginHeadrestBurst()
            val generation = observeGeneration
            MbCanRepository.runOnStateApply {
                val result = MbCanEngineFacade.canSetAudioParam(
                    MbCanKnownAudioPropertyId.HEADREST_SPEAKER,
                    raw,
                )
                if (generation != observeGeneration) return@runOnStateApply
                if (result == null || result < 0) {
                    _headrestMode.value = readHeadrestMbCan()
                }
            }
            return true
        }
        if (ok) {
            _headrestMode.value = uiValue
            beginHeadrestBurst()
        }
        return ok
    }

    private fun publishVolumes() {
        PlatformAudioDomain.VolumeChannel.entries.forEach { channel ->
            val value = readVolume(channel)
            flowFor(channel).value = value
            if (channel == PlatformAudioDomain.VolumeChannel.Media && value != null && value > 0) {
                lastNonZeroMedia = value
            }
        }
    }

    private fun publishHeadrest() {
        if (HeadUnitDayNightMapping.usesAdayoKeys()) {
            _headrestMode.value = readHeadrestAdayo()
            return
        }
        val generation = observeGeneration
        MbCanRepository.runOnStateApply {
            if (generation != observeGeneration) return@runOnStateApply
            _headrestMode.value = readHeadrestMbCan()
        }
    }

    private fun nextHeadrestDelayMs(): Long {
        val now = SystemClock.elapsedRealtime()
        return if (now < headrestBurstUntilElapsedMs) HEADREST_BURST_POLL_MS else HEADREST_NORMAL_POLL_MS
    }

    private fun beginHeadrestBurst() {
        headrestBurstUntilElapsedMs = SystemClock.elapsedRealtime() + HEADREST_BURST_DURATION_MS
        val runnable = headrestPollRunnable ?: return
        mainHandler.removeCallbacks(runnable)
        mainHandler.postDelayed(runnable, HEADREST_BURST_POLL_MS)
    }

    private fun readVolume(channel: PlatformAudioDomain.VolumeChannel): Int? {
        val raw = if (HeadUnitDayNightMapping.usesAdayoKeys()) {
            AdayoSettingsService.getInt("getAudioStreamVolume", PlatformAudioDomain.a10StreamType(channel))
        } else {
            readA9Volume(channel)
        } ?: return null
        return PlatformAudioDomain.sanitizeVolume(channel, raw)
    }

    private fun readHeadrestAdayo(): Int? =
        AdayoSettingsService.getInt("getHeadrestSpeakerMode")
            ?.let(PlatformAudioDomain::decodeHeadrestVhal)

    /** Call only from [MbCanRepository.runOnStateApply]. */
    private fun readHeadrestMbCan(): Int? =
        MbCanEngineFacade.canGetAudioParam(MbCanKnownAudioPropertyId.HEADREST_SPEAKER)
            ?.let(PlatformAudioDomain::decodeHeadrestMbCan)

    private fun writeA10Volume(channel: PlatformAudioDomain.VolumeChannel, value: Int): Boolean =
        AdayoSettingsService.setInt(
            "setAudioStreamVolume",
            PlatformAudioDomain.a10StreamType(channel),
            value,
        )

    /**
     * Stock mixer can keep STREAM_MUSIC muted after [setStreamVolume](0).
     * Raising the absolute level alone then reads back 0 until ADJUST_UNMUTE.
     */
    private fun unmuteMediaStream() {
        val context = appContext ?: return
        val audioManager =
            context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        runCatching {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_UNMUTE,
                0,
            )
        }.onFailure { Log.w(TAG, "ADJUST_UNMUTE failed: ${it.message}") }
    }

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
        /** After first probe, never Class.forName again — missing OpenOS used to spam logcat every poll. */
        @Volatile
        private var probeCompleted = false

        fun getVolume(channel: PlatformAudioDomain.VolumeChannel): Int? = runCatching {
            val audio = manager() ?: return null
            val groupId = groupId(audio, channel) ?: return null
            invokeInt(audio, "getGroupVolume", groupId)
        }.onFailure { Log.w(TAG, "OpenOS getVolume failed: ${it.javaClass.simpleName}: ${it.message}") }
            .getOrNull()

        fun setVolume(channel: PlatformAudioDomain.VolumeChannel, value: Int): Boolean = runCatching {
            val audio = manager() ?: return false
            val groupId = groupId(audio, channel) ?: return false
            val method = audio.javaClass.methods.firstOrNull {
                it.name == "setGroupVolume" && it.parameterTypes.size == 3
            } ?: return false
            method.invoke(audio, groupId, value, 0)
            true
        }.onFailure { Log.w(TAG, "OpenOS setVolume failed: ${it.javaClass.simpleName}: ${it.message}") }
            .getOrDefault(false)

        private fun groupId(audio: Any, channel: PlatformAudioDomain.VolumeChannel): Int? =
            invokeInt(audio, "getVolumeGroupIdForUsage", PlatformAudioDomain.a9Usage(channel))

        private fun invokeInt(target: Any, name: String, arg: Int): Int? {
            val method = target.javaClass.methods.firstOrNull {
                it.name == name && it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
            } ?: return null
            return (method.invoke(target, arg) as? Number)?.toInt()
        }

        private fun manager(): Any? {
            if (probeCompleted) return manager
            val context = appContext ?: return null
            synchronized(this) {
                if (probeCompleted) return manager
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
                }.onFailure { e ->
                    Log.w(
                        TAG,
                        "OpenOS AudioManager unavailable (${e.javaClass.simpleName}); " +
                            "using AudioManager streams",
                    )
                }.getOrNull()
                manager = created
                probeCompleted = true
                return created
            }
        }
    }
}
