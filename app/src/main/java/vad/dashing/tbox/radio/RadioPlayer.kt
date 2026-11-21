package vad.dashing.tbox.radio

import android.os.IBinder
import android.os.Parcel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.mbcan.MbCanRadioProperties
import vad.dashing.tbox.mbcan.MbCanRepository
import vad.dashing.tbox.mbcan.UniversalCanRepository
import java.util.Locale

/** Virtual package id of the head-unit radio "player" in the music widget carousel. */
const val RADIO_PLAYER_PACKAGE = "vad.dashing.tbox.radio"

/**
 * Radio band ids for both backends: A10 `IAdayoDAB.enterBand` (AM=1 / FM=2) and
 * A9 mbCAN property 154 (1 FM_Mode / 2 AM_Mode).
 */
enum class RadioBand(val adayoBandId: Int, val mbCanModeValue: Int) {
    FM(adayoBandId = 2, mbCanModeValue = 1),
    AM(adayoBandId = 1, mbCanModeValue = 2);

    companion object {
        fun fromAdayoBandId(id: Int): RadioBand? = entries.firstOrNull { it.adayoBandId == id }
        fun fromMbCanModeValue(value: Int): RadioBand? = entries.firstOrNull { it.mbCanModeValue == value }
    }
}

/**
 * Frequency display formatting. RX/TX unit scale is an open bench question
 * (docs/RADIO_CONTROL_STOCK_APPS_RU.md §5), so the label heuristically detects
 * MHz / 0.1 MHz / kHz / 0.1 kHz ranges; commands never depend on the scale.
 */
object RadioFrequencyFormat {
    fun frequencyLabel(band: RadioBand, raw: Int, locale: Locale = Locale.getDefault()): String {
        if (raw <= 0) return "—"
        return when (band) {
            RadioBand.FM -> when {
                raw in 65900..108000 -> mhzLabel(raw / 1000.0, locale)
                raw in 6590..10800 -> mhzLabel(raw / 100.0, locale)
                raw in 650..1080 -> mhzLabel(raw / 10.0, locale)
                raw in 65..108 -> "$raw ${mhzUnit(locale)}"
                else -> "$raw"
            }
            RadioBand.AM -> when {
                raw in 5200..17100 -> "${raw / 10} ${khzUnit(locale)}"
                raw in 520..1710 -> "$raw ${khzUnit(locale)}"
                else -> "$raw"
            }
        }
    }

    private fun mhzLabel(value: Double, locale: Locale): String {
        val rounded = (value * 10).toInt() / 10.0
        return if (rounded == rounded.toLong().toDouble()) {
            "${rounded.toLong()} ${mhzUnit(locale)}"
        } else {
            String.format(locale, "%.1f %s", rounded, mhzUnit(locale))
        }
    }

    private fun mhzUnit(locale: Locale): String =
        if (locale.language == "ru") "МГц" else "MHz"

    private fun khzUnit(locale: Locale): String =
        if (locale.language == "ru") "кГц" else "kHz"
}

/** Backend-agnostic radio state shown in the music widget artist/track lines. */
data class RadioPlayerState(
    val backendAvailable: Boolean,
    val band: RadioBand,
    /** Raw backend frequency value; units resolved heuristically by [RadioFrequencyFormat]. */
    val frequencyRaw: Int,
    val isPlaying: Boolean,
    val receptionValid: Boolean = true,
    /** Raw RX level for diagnostics; -1 when the backend does not report it. */
    val level: Int = -1,
    /** RDS PS name (A10 only); null on A9 — mbCAN exposes no RDS text. */
    val stationName: String? = null,
    /** False while the band comes from the local default (backend did not confirm it). */
    val bandConfirmed: Boolean = false,
) {
    val bandLabel: String get() = when (band) {
        RadioBand.FM -> "FM"
        RadioBand.AM -> "AM"
    }
    val frequencyLabel: String get() = RadioFrequencyFormat.frequencyLabel(band, frequencyRaw)
    val trackLabel: String get() = stationName?.takeIf { it.isNotBlank() } ?: frequencyLabel
}

/**
 * Head-unit FM/AM radio control for the music widget: A10 via the system binder
 * `com.adayo.service.AdayoDABService` (raw Parcel transactions, descriptors of
 * `com.adayo.project.aidl.IAdayoDAB`), A9 via mbCAN radio commands
 * (`canRadioSet` seek + properties 154/161) dispatched on the `mbcan-state-apply`
 * thread through [MbCanRepository].
 */
object RadioPlayer {
    private const val TAG = "RadioPlayer"
    private const val ADAYO_SERVICE_NAME = "com.adayo.service.AdayoDABService"
    private const val ADAYO_DESCRIPTOR = "com.adayo.project.aidl.IAdayoDAB"
    private const val TXN_REGISTER_CALLBACK = 1
    private const val TXN_ENTER_BAND = 3
    private const val TXN_GET_BAND = 4
    private const val TXN_PLAY = 5
    private const val TXN_PAUSE = 6
    private const val TXN_IS_PLAYING = 7
    private const val TXN_PREV = 8
    private const val TXN_NEXT = 9
    private const val TXN_GET_CUR_FREQ = 21
    private const val TXN_GET_PS_NAME = 94
    private const val POLL_MS_A10 = 2_000L
    private const val POLL_MS_A9 = 3_000L

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        runCatching {
            TboxRepository.addLog("ERROR", TAG, "Radio loop error: ${throwable.message}")
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)

    private val _state = MutableStateFlow<RadioPlayerState?>(null)
    val state: StateFlow<RadioPlayerState?> = _state.asStateFlow()

    private val watchLock = Any()
    private var watchers = 0
    private var pollJob: kotlinx.coroutines.Job? = null
    private val pollKick = MutableStateFlow(0L)

    @Volatile
    private var localBand = RadioBand.FM

    @Volatile
    private var a9PlayingAssumed = false

    fun isRadioPackage(packageName: String?): Boolean =
        packageName?.trim() == RADIO_PLAYER_PACKAGE

    /** Refcounted polling: runs while at least one music widget shows the radio player. */
    fun beginWatch() {
        synchronized(watchLock) {
            watchers += 1
            if (pollJob == null) {
                pollJob = scope.launch { pollLoop() }
            }
        }
    }

    fun endWatch() {
        synchronized(watchLock) {
            watchers = (watchers - 1).coerceAtLeast(0)
            if (watchers == 0) {
                pollJob?.cancel()
                pollJob = null
            }
        }
    }

    fun commandPlayPause() = launchCommand { playPause() }

    fun commandSeekNext() = launchCommand { seek(next = true) }

    fun commandSeekPrevious() = launchCommand { seek(next = false) }

    /** Band switch is exposed for future UI; play/seek are the widget buttons for now. */
    suspend fun toggleBand() {
        val target = when (_state.value?.band ?: localBand) {
            RadioBand.FM -> RadioBand.AM
            RadioBand.AM -> RadioBand.FM
        }
        if (isA10()) {
            transactVoid(TXN_ENTER_BAND) { it.writeInt(target.adayoBandId) }
        } else {
            reportCommandError(
                "band switch",
                MbCanRepository.sendRadioVehicleSet(
                    MbCanRadioProperties.FREQUENCY_MODE,
                    target.mbCanModeValue,
                ),
            )
        }
        localBand = target
        kick()
    }

    private fun launchCommand(block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }.onFailure {
                runCatching {
                    TboxRepository.addLog("ERROR", TAG, "Command failed: ${it.message}")
                }
            }
        }
    }

    private suspend fun playPause() {
        if (isA10()) {
            if (_state.value?.isPlaying == true) {
                transactVoid(TXN_PAUSE)
            } else {
                transactVoid(TXN_PLAY)
            }
        } else {
            val target = !(_state.value?.isPlaying ?: a9PlayingAssumed)
            a9PlayingAssumed = target
            reportCommandError(
                "radio play/pause",
                MbCanRepository.sendRadioVehicleSet(
                    MbCanRadioProperties.PLAY_STATE,
                    if (target) MbCanRadioProperties.PLAY_ON else MbCanRadioProperties.PLAY_OFF,
                ),
            )
        }
        kick()
    }

    private suspend fun seek(next: Boolean) {
        if (isA10()) {
            transactVoid(if (next) TXN_NEXT else TXN_PREV)
        } else {
            reportCommandError(
                "radio seek",
                MbCanRepository.sendRadioCmd(
                    if (next) MbCanRadioProperties.CMD_SEEK_UP else MbCanRadioProperties.CMD_SEEK_DOWN,
                    0,
                ),
            )
        }
        kick()
    }

    private suspend fun pollLoop() {
        while (coroutineContext.isActive) {
            val next = runCatching { pollOnce() }.getOrNull()
            if (next != null) {
                _state.value = next
            }
            val kick = pollKick.value
            withTimeoutOrNull(pollIntervalMs()) {
                pollKick.first { it != kick }
            }
        }
    }
    private fun pollIntervalMs(): Long = if (isA10()) POLL_MS_A10 else POLL_MS_A9

    private fun isA10(): Boolean =
        UniversalCanRepository.mode.value == HeadUnitCanMode.Android10Vhal

    private suspend fun pollOnce(): RadioPlayerState {
        return if (isA10()) pollA10() else pollA9()
    }

    private suspend fun pollA10(): RadioPlayerState {
        val bandValue = transactInt(TXN_GET_BAND)
        val band = bandValue?.let { RadioBand.fromAdayoBandId(it) } ?: localBand
        localBand = band
        val available = obtainBinder() != null
        val freq = transactInt(TXN_GET_CUR_FREQ) ?: 0
        val playing = transactInt(TXN_IS_PLAYING) == 1
        val psName = transactString(TXN_GET_PS_NAME).orEmpty().trim()
        return RadioPlayerState(
            backendAvailable = available,
            band = band,
            frequencyRaw = freq,
            isPlaying = playing && available,
            receptionValid = true,
            level = -1,
            stationName = psName.takeIf { it.isNotBlank() },
            bandConfirmed = bandValue != null,
        )
    }

    private suspend fun pollA9(): RadioPlayerState {
        val snapshot = MbCanRepository.getRadioSnapshot()
        val band = snapshot.modeValue?.let { RadioBand.fromMbCanModeValue(it) } ?: localBand
        return RadioPlayerState(
            backendAvailable = snapshot.error == null,
            band = band,
            frequencyRaw = snapshot.frequency ?: 0,
            isPlaying = a9PlayingAssumed && snapshot.error == null,
            receptionValid = snapshot.valid != 0,
            level = snapshot.level,
            stationName = null,
            bandConfirmed = snapshot.modeValue != null,
        )
    }

    private fun reportCommandError(action: String, error: String?) {
        if (error != null) {
            runCatching {
                TboxRepository.addLog("ERROR", TAG, "$action: $error")
            }
        }
    }

    private fun kick() {
        pollKick.value += 1
    }

    @Volatile
    private var cachedBinder: IBinder? = null

    private fun obtainBinder(): IBinder? {
        cachedBinder?.let { return it }
        val binder = runCatching {
            val sm = Class.forName("android.os.ServiceManager")
            sm.getMethod("getService", String::class.java)
                .invoke(null, ADAYO_SERVICE_NAME) as? IBinder
        }.getOrNull() ?: return null
        cachedBinder = binder
        return binder
    }

    private fun <T> transact(code: Int, write: (Parcel) -> Unit = {}, read: (Parcel) -> T): T? {
        val binder = obtainBinder() ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(ADAYO_DESCRIPTOR)
            write(data)
            binder.transact(code, data, reply, 0)
            reply.readException()
            read(reply)
        } catch (t: Throwable) {
            cachedBinder = null
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun transactVoid(code: Int, write: (Parcel) -> Unit = {}) {
        transact(code, write) { Unit }
    }

    private fun transactInt(code: Int): Int? = transact(code) { it.readInt() }

    private fun transactString(code: Int): String? = transact(code) { it.readString() }
}
