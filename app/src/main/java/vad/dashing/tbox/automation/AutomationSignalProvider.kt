package vad.dashing.tbox.automation

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import vad.dashing.tbox.CanDataRepository
import vad.dashing.tbox.CarDataRepository
import vad.dashing.tbox.ForegroundAppMonitor
import vad.dashing.tbox.AppContextHolder
import vad.dashing.tbox.HeadUnitBrightnessRepository
import vad.dashing.tbox.HeadUnitDayNightRepository
import vad.dashing.tbox.MediaPlayerState
import vad.dashing.tbox.PlatformAudioDomain
import vad.dashing.tbox.PlatformAudioRepository
import vad.dashing.tbox.SharedMediaControlService
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.TripTelemetryRepository
import vad.dashing.tbox.Wheels
import vad.dashing.tbox.esp.EspCompanionRepository
import vad.dashing.tbox.location.GeoDisplayRepository
import vad.dashing.tbox.location.LocIndicatorState
import vad.dashing.tbox.mbcan.BodyComfortDomain
import vad.dashing.tbox.mbcan.BodyComfortRawRead
import vad.dashing.tbox.mbcan.MbCanAvailability
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.MbCanSeatModeState
import vad.dashing.tbox.mbcan.MbCanSignal
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.trip.TripRepository

class AutomationSignalProvider(
    private val scope: CoroutineScope,
    private val onSample: suspend (AutomationSignalSample) -> Unit,
) {
    private val jobs = mutableListOf<Job>()
    private var activeKeys: Set<AutomationSignalKey> = emptySet()

    suspend fun replaceInterests(keys: Set<AutomationSignalKey>) {
        ForegroundAppMonitor.setAutomationWatching(
            keys.any {
                it.signal == AutomationSignalId.FOREGROUND_APP
            },
        )
        if (keys == activeKeys) return
        jobs.forEach(Job::cancel)
        jobs.clear()
        activeKeys = keys

        val huSignals = keys
            .asSequence()
            .filter {
                AutomationSignalCatalog.resolveSource(it.signal, it.source) == AutomationSignalSource.HEAD_UNIT
            }
            .mapNotNull { huInterestFor(it.signal) }
            .toSet()
        if (huSignals.isEmpty()) {
            UniversalCanRepository.clearSourceNow(SOURCE_ID)
        } else {
            UniversalCanRepository.setSourceSignals(SOURCE_ID, huSignals)
        }

        keys.forEach { key ->
            val flow = flowFor(key) ?: return@forEach
            jobs += scope.launch {
                try {
                    flow.collect { value ->
                        val sample = AutomationSignalSample(
                            key = key,
                            value = value,
                            observedAtElapsedMillis = SystemClock.elapsedRealtime(),
                        )
                        onSample(sample)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    TboxRepository.addLog(
                        "ERROR",
                        "Automation",
                        "Signal ${key.signal.storageKey}/${key.source.storageKey}: " +
                            (error.message ?: error.javaClass.simpleName),
                    )
                }
            }
        }
    }

    fun stop() {
        jobs.forEach(Job::cancel)
        jobs.clear()
        activeKeys = emptySet()
        ForegroundAppMonitor.setAutomationWatching(false)
        UniversalCanRepository.clearSourceNow(SOURCE_ID)
    }

    private fun flowFor(key: AutomationSignalKey): Flow<AutomationSignalValue>? =
        AutomationSignalReads.flowFor(key)

    private fun huInterestFor(signal: AutomationSignalId): MbCanSignal? =
        huInterestForSignal(signal)

    companion object {
        const val SOURCE_ID = "user-automations"
    }
}

internal fun espMaskBitFlow(mask: Flow<Int>, bit: Int): Flow<AutomationSignalValue> =
    mask.map { value ->
        AutomationSignalValue.State(if ((value and (1 shl bit)) != 0) "on" else "off")
    }.withAvailability(EspCompanionRepository.connected)

/**
 * Input levels stay [AutomationSignalValue.Unavailable] until the companion sends a `gpio`
 * snapshot on this link. The default mask (0) and a mask left from the previous session
 * are not a state change.
 */
internal fun espGpioInputFlow(bit: Int): Flow<AutomationSignalValue> =
    combine(
        EspCompanionRepository.connected,
        EspCompanionRepository.gpioInputsReady,
        EspCompanionRepository.gpioMask,
    ) { connected, ready, mask ->
        if (!connected || !ready) {
            AutomationSignalValue.Unavailable
        } else {
            AutomationSignalValue.State(if ((mask and (1 shl bit)) != 0) "on" else "off")
        }
    }.distinctUntilChanged()

internal fun espBleBoundFlow(): Flow<AutomationSignalValue> =
    combine(
        EspCompanionRepository.connected,
        EspCompanionRepository.bleOn,
        EspCompanionRepository.bleMacs,
    ) { connected, bleOn, macs ->
        if (!connected) {
            AutomationSignalValue.Unavailable
        } else {
            AutomationSignalValue.State(
                if (bleOn && macs.isNotEmpty()) "on" else "off",
            )
        }
    }.distinctUntilChanged()

internal fun espBleBatteryFlow(mac: String): Flow<AutomationSignalValue> =
    combine(
        EspCompanionRepository.connected,
        EspCompanionRepository.bleDevices,
    ) { connected, devices ->
        val normalized = mac.trim().lowercase()
        val bat = devices[normalized]?.batteryPercent
        when {
            !connected -> AutomationSignalValue.Unavailable
            bat == null -> AutomationSignalValue.Unavailable
            else -> AutomationSignalValue.Number(bat.toDouble())
        }
    }.distinctUntilChanged()

internal fun foregroundAppFlow(): Flow<AutomationSignalValue> =
    ForegroundAppMonitor.packageName
        .map { pkg ->
            val name = pkg?.trim().orEmpty()
            if (name.isEmpty()) {
                AutomationSignalValue.Unavailable
            } else {
                AutomationSignalValue.State(name)
            }
        }
        .distinctUntilChanged()

internal fun appThemeModeFlow(): Flow<AutomationSignalValue> =
    HeadUnitDayNightRepository.modeState.map { mode ->
        val value = when (mode) {
            HeadUnitDayNightRepository.Mode.LightManual -> "manual_day"
            HeadUnitDayNightRepository.Mode.DarkManual -> "manual_night"
            HeadUnitDayNightRepository.Mode.LightAuto -> "auto_day"
            HeadUnitDayNightRepository.Mode.DarkAuto -> "auto_night"
            null -> null
        }
        value?.let(AutomationSignalValue::State) ?: AutomationSignalValue.Unavailable
    }.distinctUntilChanged()

internal fun appThemeEffectiveFlow(): Flow<AutomationSignalValue> =
    HeadUnitDayNightRepository.modeState.map { mode ->
        val value = when (mode) {
            HeadUnitDayNightRepository.Mode.LightManual,
            HeadUnitDayNightRepository.Mode.LightAuto,
            -> "day"

            HeadUnitDayNightRepository.Mode.DarkManual,
            HeadUnitDayNightRepository.Mode.DarkAuto,
            -> "night"

            null -> null
        }
        value?.let(AutomationSignalValue::State) ?: AutomationSignalValue.Unavailable
    }.distinctUntilChanged()

internal fun huScreenBrightnessFlow(): Flow<AutomationSignalValue> {
    val context = AppContextHolder.appContextOrNull
        ?: return flowOf(AutomationSignalValue.Unavailable)
    return callbackFlow {
        HeadUnitBrightnessRepository.startObserving(context)
        val job = launch {
            HeadUnitBrightnessRepository.brightnessUiLevel.collect { level ->
                trySend(
                    level?.toDouble()?.takeIf(Double::isFinite)?.let(AutomationSignalValue::Number)
                        ?: AutomationSignalValue.Unavailable,
                )
            }
        }
        awaitClose {
            job.cancel()
            HeadUnitBrightnessRepository.stopObserving(context)
        }
    }.distinctUntilChanged()
}

internal fun huScreenAutoBrightnessFlow(): Flow<AutomationSignalValue> {
    val context = AppContextHolder.appContextOrNull
        ?: return flowOf(AutomationSignalValue.Unavailable)
    return callbackFlow {
        HeadUnitBrightnessRepository.startObserving(context)
        val job = launch {
            HeadUnitBrightnessRepository.autoBrightness.collect { enabled ->
                trySend(
                    enabled?.let { on -> AutomationSignalValue.State(if (on) "on" else "off") }
                        ?: AutomationSignalValue.Unavailable,
                )
            }
        }
        awaitClose {
            job.cancel()
            HeadUnitBrightnessRepository.stopObserving(context)
        }
    }.distinctUntilChanged()
}

internal fun platformVolumeFlow(
    volume: StateFlow<Int?>,
): Flow<AutomationSignalValue> {
    val context = AppContextHolder.appContextOrNull
        ?: return flowOf(AutomationSignalValue.Unavailable)
    return callbackFlow {
        PlatformAudioRepository.startObserving(context)
        val job = launch {
            volume.collect { level ->
                trySend(
                    level?.toDouble()?.takeIf(Double::isFinite)?.let(AutomationSignalValue::Number)
                        ?: AutomationSignalValue.Unavailable,
                )
            }
        }
        awaitClose {
            job.cancel()
            PlatformAudioRepository.stopObserving()
        }
    }.distinctUntilChanged()
}

internal fun platformHeadrestFlow(): Flow<AutomationSignalValue> {
    val context = AppContextHolder.appContextOrNull
        ?: return flowOf(AutomationSignalValue.Unavailable)
    return callbackFlow {
        PlatformAudioRepository.startObserving(context)
        val job = launch {
            PlatformAudioRepository.headrestMode.collect { mode ->
                val key = when (mode) {
                    PlatformAudioDomain.HEADREST_ONLY -> "only"
                    PlatformAudioDomain.HEADREST_ASSIST -> "assist"
                    PlatformAudioDomain.HEADREST_OFF -> "off"
                    else -> null
                }
                trySend(
                    key?.let(AutomationSignalValue::State) ?: AutomationSignalValue.Unavailable,
                )
            }
        }
        awaitClose {
            job.cancel()
            PlatformAudioRepository.stopObserving()
        }
    }.distinctUntilChanged()
}

internal fun wifiSnapshotFlow(): Flow<WifiStaSnapshot> {
    val context = AppContextHolder.appContextOrNull
        ?: return flowOf(
            WifiStaSnapshot(radioEnabled = false, associated = false, ssid = null),
        )
    return WifiStaController.snapshots(context)
}

internal fun geoDisplayFlow(): Flow<AutomationSignalValue> =
    GeoDisplayRepository.state
        .map { state ->
            val lat = state.latitude
            val lon = state.longitude
            if (
                state.indicator == LocIndicatorState.NONE ||
                state.indicator == LocIndicatorState.LOST ||
                !lat.isFinite() ||
                !lon.isFinite()
            ) {
                AutomationSignalValue.Unavailable
            } else {
                AutomationSignalValue.Position(lat, lon)
            }
        }
        .distinctUntilChanged()

internal fun activeTripDistanceFlow(): Flow<AutomationSignalValue> =
    TripRepository.activeTrip.map { trip ->
        val t = trip?.takeIf { it.isCurrentActive }
        t?.distanceKm?.toDouble()?.takeIf(Double::isFinite)?.let(AutomationSignalValue::Number)
            ?: AutomationSignalValue.Unavailable
    }.distinctUntilChanged()

internal fun activeTripAvgFuelFlow(): Flow<AutomationSignalValue> =
    TripRepository.activeTrip.map { trip ->
        val t = trip?.takeIf { it.isCurrentActive } ?: return@map AutomationSignalValue.Unavailable
        TripRepository.averageFuelConsumptionLitersPer100Km(t)
            ?.toDouble()
            ?.takeIf(Double::isFinite)
            ?.let(AutomationSignalValue::Number)
            ?: AutomationSignalValue.Unavailable
    }.distinctUntilChanged()

internal fun activeTripDurationFlow(): Flow<AutomationSignalValue> =
    TripRepository.activeTrip.map { trip ->
        val t = trip?.takeIf { it.isCurrentActive } ?: return@map AutomationSignalValue.Unavailable
        val seconds =
            (t.movingTimeMs + t.idleTimeMs + t.parkingTimeMs).coerceAtLeast(0L) / 1000.0
        AutomationSignalValue.Number(seconds)
    }.distinctUntilChanged()

internal fun activeTripMotorHoursFlow(): Flow<AutomationSignalValue> =
    TripRepository.activeTrip.map { trip ->
        val t = trip?.takeIf { it.isCurrentActive } ?: return@map AutomationSignalValue.Unavailable
        t.engineRunningTimeHours().toDouble().takeIf(Double::isFinite)
            ?.let(AutomationSignalValue::Number)
            ?: AutomationSignalValue.Unavailable
    }.distinctUntilChanged()

internal fun mediaNowPlayingFlow(
    pick: (MediaPlayerState) -> String,
): Flow<AutomationSignalValue> =
    SharedMediaControlService.playerStates
        .map { states ->
            val selected = selectMediaPlayerState(states)
            val text = selected?.let(pick)?.trim().orEmpty()
            if (selected == null || text.isEmpty()) {
                AutomationSignalValue.Unavailable
            } else {
                AutomationSignalValue.State(text)
            }
        }
        .distinctUntilChanged()

internal fun mediaPlaybackNumberFlow(
    pick: (MediaPlayerState, Long) -> Double,
): Flow<AutomationSignalValue> =
    SharedMediaControlService.playerStates
        .map { states ->
            val selected = selectMediaPlayerState(states)
                ?: return@map AutomationSignalValue.Unavailable
            val value = pick(selected, SystemClock.elapsedRealtime())
            if (!value.isFinite()) {
                AutomationSignalValue.Unavailable
            } else {
                AutomationSignalValue.Number(value)
            }
        }
        .distinctUntilChanged()

internal fun selectMediaPlayerState(
    states: Map<String, MediaPlayerState>,
): MediaPlayerState? {
    if (states.isEmpty()) return null
    val values = states.values
    return values.firstOrNull { it.isPlaying && (it.track.isNotBlank() || it.artist.isNotBlank()) }
        ?: values.firstOrNull { it.track.isNotBlank() || it.artist.isNotBlank() }
        ?: values.firstOrNull { it.isPlaying }
}

internal fun <T : Number> Flow<T?>.numberFlow(): Flow<AutomationSignalValue> =
    map { value ->
        value?.toDouble()?.takeIf(Double::isFinite)?.let(AutomationSignalValue::Number)
            ?: AutomationSignalValue.Unavailable
    }

internal fun Flow<UInt?>.uintNumberFlow(): Flow<AutomationSignalValue> =
    map { value ->
        value?.toDouble()?.let(AutomationSignalValue::Number) ?: AutomationSignalValue.Unavailable
    }

internal fun Flow<Wheels>.wheelNumberFlow(
    selector: (Wheels) -> Float?,
): Flow<AutomationSignalValue> =
    map { wheels ->
        selector(wheels)?.toDouble()?.takeIf(Double::isFinite)?.let(AutomationSignalValue::Number)
            ?: AutomationSignalValue.Unavailable
    }

internal fun Flow<MbCanBinaryState>.binaryFlow(): Flow<AutomationSignalValue> =
    map { state ->
        when (state) {
            MbCanBinaryState.Off -> AutomationSignalValue.State("off")
            MbCanBinaryState.On -> AutomationSignalValue.State("on")
            is MbCanBinaryState.Unavailable,
            MbCanBinaryState.Unknown,
            -> AutomationSignalValue.Unavailable
        }
    }

internal fun Flow<BodyComfortRawRead>.shadeRoofStateFlow(
    selector: (BodyComfortRawRead) -> Int?,
    allowTilt: Boolean,
): Flow<AutomationSignalValue> = map { raw ->
    val value = selector(raw)
    when {
        value == null -> AutomationSignalValue.Unavailable
        BodyComfortDomain.shadeRoofTilted(value) ->
            if (allowTilt) {
                AutomationSignalValue.State(BodyComfortDomain.STATE_TILT)
            } else {
                AutomationSignalValue.Unavailable
            }
        BodyComfortDomain.shadeRoofPercent(value) != null -> AutomationSignalValue.State("$value%")
        else -> AutomationSignalValue.Unavailable
    }
}

internal fun Flow<BodyComfortRawRead>.windowStateFlow(
    selector: (BodyComfortRawRead) -> Int?,
): Flow<AutomationSignalValue> = map { raw ->
    BodyComfortDomain.windowStateValue(selector(raw))
        ?.let(AutomationSignalValue::State)
        ?: AutomationSignalValue.Unavailable
}

internal fun Flow<MbCanSeatModeState>.seatModeFlow(): Flow<AutomationSignalValue> =
    map { state ->
        val value = when (state) {
            MbCanSeatModeState.Off -> "off"
            is MbCanSeatModeState.Heat -> "heat_${state.level}"
            is MbCanSeatModeState.Vent -> "vent_${state.level}"
            is MbCanSeatModeState.Unavailable,
            MbCanSeatModeState.Unknown,
            -> null
        }
        value?.let(AutomationSignalValue::State) ?: AutomationSignalValue.Unavailable
    }

internal fun Flow<AutomationSignalValue>.withAvailability(
    availability: Flow<Boolean>,
): Flow<AutomationSignalValue> =
    combine(availability) { value, available ->
        if (available) value else AutomationSignalValue.Unavailable
    }.distinctUntilChanged()
