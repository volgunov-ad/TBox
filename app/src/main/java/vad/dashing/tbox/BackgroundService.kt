package vad.dashing.tbox

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import dashingineering.jetour.tboxcore.TBoxClient
import dashingineering.jetour.tboxcore.types.TBoxClientCallback
import dashingineering.jetour.tboxcore.types.LogType
import vad.dashing.tbox.location.LocationMockManager
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vad.dashing.tbox.utils.CanFramesProcess
import vad.dashing.tbox.utils.CanFramesProcess.toFloat
import vad.dashing.tbox.utils.CanFramesProcess.toUInt
import vad.dashing.tbox.utils.CsnOperatorResolver
import vad.dashing.tbox.utils.MotorHoursBuffer
import vad.dashing.tbox.utils.ThemeObserver
import java.net.DatagramPacket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Date
import java.util.concurrent.Executors
import java.util.Locale
import kotlin.let

class BackgroundService : Service() {
    private lateinit var settingsManager: SettingsManager
    private lateinit var appDataManager: AppDataManager
    private val locationMockManager by lazy { LocationMockManager(this) }
    private lateinit var scope: CoroutineScope
    private val job = SupervisorJob()
    private lateinit var autoModemRestart: StateFlow<Boolean>
    private lateinit var autoTboxReboot: StateFlow<Boolean>
    private lateinit var autoSuspendTboxApp: StateFlow<Boolean>
    private lateinit var autoStopTboxApp: StateFlow<Boolean>
    private lateinit var autoSuspendTboxMdc: StateFlow<Boolean>
    private lateinit var autoStopTboxMdc: StateFlow<Boolean>
    private lateinit var autoSuspendTboxSwd: StateFlow<Boolean>
    private lateinit var autoPreventTboxRestart: StateFlow<Boolean>
    private lateinit var getCanFrame: StateFlow<Boolean>
    private lateinit var getCycleSignal: StateFlow<Boolean>
    private lateinit var getLocData: StateFlow<Boolean>
    private lateinit var widgetShowIndicator: StateFlow<Boolean>
    private lateinit var widgetShowLocIndicator: StateFlow<Boolean>
    private lateinit var mockLocation: StateFlow<Boolean>
    private lateinit var floatingDashboards: StateFlow<List<FloatingDashboardConfig>>
    private lateinit var canDataSaveCount: StateFlow<Int>
    private lateinit var fuelTankLitersSetting: StateFlow<Int>
    private lateinit var fuelPriceFuelIdSetting: StateFlow<Int>
    private lateinit var splitTripTimeMinutesSetting: StateFlow<Int>
    private val fuelPriceClient by lazy { FuelPriceClient() }

    private val serverPort = 50047
    private var themeObserver: ThemeObserver? = null
    private var tBoxClient: TBoxClient? = null
    @Volatile
    private var lastPacketAtMs: Long = 0
    private val sendRawMessageMutex = Mutex()
    private val tboxClientReconnectMutex = Mutex()
    /** Serializes [onStartCommand] handling so each intent awaits settings snapshot and runs in order. */
    private val commandRouterMutex = Mutex()
    private val tripsPersistMutex = Mutex()
    private val packetProcessingDispatcher =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "tbox-packet-processor").apply { isDaemon = true }
        }.asCoroutineDispatcher()

    private var mainJob: Job? = null
    private var periodicJob: Job? = null
    private var apnJob: Job? = null
    private var appCmdJob: Job? = null
    private var crtCmdJob: Job? = null
    private var ssmCmdJob: Job? = null
    private var swdCmdJob: Job? = null
    private var locCmdJob: Job? = null
    private var apnCmdJob: Job? = null
    private var humJob: Job? = null
    private var sendATJob: Job? = null
    private var modemModeJob: Job? = null
    private var checkConnectionJob: Job? = null
    private var tboxClientReconnectJob: Job? = null
    private var versionsJob: Job? = null
    private var generalStateBroadcastJob: Job? = null
    private var settingsListenerJob: Job? = null
    private var dataListenerJob: Job? = null
    private var getSMSJob: Job? = null
    /** Serializes delayed / repeated "open MainActivity" commands: each new request replaces the previous. */
    private var openMainActivityJob: Job? = null
    /** Cancels in-flight [ACTION_START] bootstrap if [ACTION_STOP] runs mid-startup. */
    private var serviceStartupJob: Job? = null
    private var infraBootstrapJob: Job? = null
    private var packetSilenceChecks: Int = 0

    /** Completes after settings [StateFlow]s are bound and initial trips are loaded from disk (or failed safely). */
    private val serviceInfraReady = CompletableDeferred<Unit>()

    private data class TimingMark(val label: String, val elapsedMs: Long)

    private val timingMarks = mutableListOf<TimingMark>()

    private fun timingReset() {
        timingMarks.clear()
    }

    private fun timingMark(label: String) {
        timingMarks.add(TimingMark(label, SystemClock.elapsedRealtime()))
    }

    private fun timingLog(tag: String) {
        if (timingMarks.isEmpty()) return
        val sb = StringBuilder()
        var prev = timingMarks[0].elapsedMs
        val base = prev
        for (m in timingMarks) {
            val delta = m.elapsedMs - prev
            val cum = m.elapsedMs - base
            if (sb.isNotEmpty()) sb.append(" | ")
            sb.append(m.label).append(" +").append(delta).append("ms (Σ ").append(cum).append("ms)")
            prev = m.elapsedMs
        }
        TboxRepository.addLog("DEBUG", tag, sb.toString())
        timingMarks.clear()
    }

    @Volatile
    var servicePhase: ServiceLifecyclePhase = ServiceLifecyclePhase.Idle
        internal set

    /** True after [reloadTripsFromDataStoreSuspend] has applied disk trips to [TripRepository]. */
    private val tripsFromDiskReady = AtomicBoolean(false)

    private var netUpdateTime: Long = 5000
    private var apnUpdateTime: Long = 10000
    private var netUpdateCount: Int = 0
    private var apn1UpdateCount: Int = 0
    private var apn2UpdateCount: Int = 0
    private var apnCheck: Boolean = false
    private var stopTboxAppLastTime = System.currentTimeMillis()
    private var stopTboxMdcLastTime = System.currentTimeMillis()
    private var suspendTboxAppLastTime = System.currentTimeMillis()
    private var suspendTboxMdcLastTime = System.currentTimeMillis()
    private var suspendTboxSwdLastTime = System.currentTimeMillis()
    private var preventRestartLastTime = System.currentTimeMillis()

    private val broadcastReceiver = TboxBroadcastReceiver()
    private val broadcastSenderLazy = lazy { TboxBroadcastSender(this, scope) }
    val broadcastSender get() = broadcastSenderLazy.value

    private val overlayControllerLazy = lazy {
        FloatingOverlayController(
            service = this,
            settingsManager = settingsManager,
            appDataManager = appDataManager,
            onRebootTbox = { crtRebootTbox() },
            onTripFinishAndStart = { scope.launch { finishActiveTripAndStartNew() } }
        )
    }
    private val overlayController get() = overlayControllerLazy.value

    private var motorHoursBuffer = MotorHoursBuffer(0.05f)
    private var motorHoursTripBuffer = MotorHoursBuffer(0.01f)

    /**
     * In-RAM state for automatic trips: each CAN RPM sample drives [onTripRpmSample]. Split-window
     * length comes from [splitTripTimeMinutesSetting] (same semantics as [TripRules]).
     */
    private var tripPrevRpmForStart = 0f
    /** True once we have seen RPM > 0 this service session; blocks spurious "new trip" on first samples. */
    private var tripRpmWasPositiveSinceService = false
    /**
     * After engine-off, the ended trip id if we might merge it back when RPM returns within the split window.
     */
    private var tripPendingSplitTripId: String? = null
    /** Wall clock when RPM last went to zero while a trip was active; used to measure off-engine pause. */
    private var tripRpmZeroAtMs: Long? = null
    private var tripLastSampleElapsedMs: Long = 0L
    private var tripLastOdometer: UInt? = null
    private var tripStartOdometer: UInt? = null
    /** Last fuel % used for step consumption between samples; aligned with [TripRecord.fuelBaselinePercent]. */
    private var tripLastFuelPercent: Float? = null
    private var tripLastPeriodicPersistAt = SystemClock.elapsedRealtime()
    private var tripLastPersistedSnapshot: TripRecord? = null
    /** First RPM sample after service start or reload: special-case resume vs new trip without double-counting. */
    private var tripFirstSampleAfterSessionStart = true
    /**
     * Set when cold-start resume reopened a trip that had [TripRecord.endTimeEpochMs]. Cleared on first RPM>0
     * this session. If the HU stops the service before the engine runs, [finalizeTripsOnServiceStop] restores
     * the previous end time and rolls back the resume-only idle delta to avoid double-counting on next boot.
     */
    private var tripColdResumeReopenedEndedTrip: ColdResumeReopenedEndedTrip? = null
    /** True when cold resume reopened an ended trip, but parked time should be applied only at first RPM>0. */
    private var tripColdResumeApplyParkedIdleOnEngineStart: Boolean = false

    private var isLastSMS: Boolean = false

    companion object {
        private const val APP_CODE = 0x2F.toByte()
        private const val MDC_CODE = 0x25.toByte()
        private const val LOC_CODE = 0x29.toByte()
        private const val CRT_CODE = 0x23.toByte()
        private const val SWD_CODE = 0x2D.toByte()
        private const val NTM_CODE = 0x24.toByte()
        private const val HUM_CODE = 0x30.toByte()
        private const val GATE_CODE = 0x37.toByte()
        private const val UDA_CODE = 0x38.toByte()
        private const val SELF_CODE = 0x50.toByte()
        private const val DEFAULT_TBOX_IP = "192.168.225.1"
        private var isRunning = false
        const val LOCATION_UPDATE_TIME = 1
        const val NOTIFICATION_ID = 50047
        const val CHANNEL_ID = "tbox_background_channel"

        const val ACTION_UPDATE_WIDGET = "vad.dashing.tbox.UPDATE_WIDGET"
        const val EXTRA_SIGNAL_LEVEL = "vad.dashing.tbox.SIGNAL_LEVEL"
        const val EXTRA_TBOX_STATUS = "vad.dashing.tbox.TBOX_STATUS"
        const val EXTRA_NET_TYPE = "vad.dashing.tbox.NET_TYPE"
        const val EXTRA_APN_STATUS = "vad.dashing.tbox.EXTRA_APN_STATUS"
        const val EXTRA_PIN = "vad.dashing.tbox.EXTRA_PIN"
        const val EXTRA_PUK = "vad.dashing.tbox.EXTRA_PUK"
        const val EXTRA_THEME = "vad.dashing.tbox.EXTRA_THEME"
        const val EXTRA_AT_CMD = "vad.dashing.tbox.EXTRA_AT_CMD"
        const val EXTRA_WIDGET_SHOW_INDICATOR = "vad.dashing.tbox.EXTRA_WIDGET_SHOW_INDICATOR"
        const val EXTRA_WIDGET_SHOW_LOC_INDICATOR = "vad.dashing.tbox.EXTRA_WIDGET_SHOW_LOC_INDICATOR"
        const val EXTRA_LOC_SET_POSITION = "vad.dashing.tbox.EXTRA_LOC_SET_POSITION"
        const val EXTRA_LOC_TRUE_POSITION = "vad.dashing.tbox.EXTRA_LOC_TRUE_POSITION"
        const val EXTRA_APP_NAME = "vad.dashing.tbox.EXTRA_APP_NAME"

        const val ACTION_START = "vad.dashing.tbox.START"
        /** Present when [ACTION_START] was triggered after [android.content.Intent.ACTION_BOOT_COMPLETED]. */
        const val EXTRA_START_FROM_BOOT = "vad.dashing.tbox.START_FROM_BOOT"
        /** Source broadcast action that initiated boot-time start (BOOT/LOCKED_BOOT/QUICKBOOT). */
        const val EXTRA_START_SOURCE_ACTION = "vad.dashing.tbox.START_SOURCE_ACTION"
        const val ACTION_STOP = "vad.dashing.tbox.STOP"
        const val ACTION_SEND_AT = "vad.dashing.tbox.SEND_AT"
        const val ACTION_MODEM_CHECK = "vad.dashing.tbox.MODEM_CHECK"
        const val ACTION_MODEM_OFF = "vad.dashing.tbox.MODEM_OFF"
        const val ACTION_MODEM_ON = "vad.dashing.tbox.MODEM_ON"
        const val ACTION_MODEM_FLY = "vad.dashing.tbox.MODEM_FLY"
        const val ACTION_TBOX_REBOOT = "vad.dashing.tbox.TBOX_REBOOT"
        const val ACTION_APN1_RESTART = "vad.dashing.tbox.APN1_RESTART"
        const val ACTION_APN1_FLY = "vad.dashing.tbox.APN1_FLY"
        const val ACTION_APN1_RECONNECT = "vad.dashing.tbox.APN1_RECONNECT"
        const val ACTION_APN2_RESTART = "vad.dashing.tbox.APN2_RESTART"
        const val ACTION_APN2_FLY = "vad.dashing.tbox.APN2_FLY"
        const val ACTION_APN2_RECONNECT = "vad.dashing.tbox.APN2_RECONNECT"
        const val ACTION_CLOSE = "vad.dashing.tbox.CLOSE"
        const val ACTION_OPEN = "vad.dashing.tbox.OPEN"
        const val ACTION_PIN = "vad.dashing.tbox.PIN"
        const val ACTION_PUK = "vad.dashing.tbox.PUK"
        const val ACTION_TBOX_APP_SUSPEND = "vad.dashing.tbox.TBOX_APP_SUSPEND"
        const val ACTION_TBOX_APP_RESUME = "vad.dashing.tbox.TBOX_APP_RESUME"
        const val ACTION_TBOX_APP_STOP = "vad.dashing.tbox.TBOX_APP_STOP"
        const val ACTION_GET_INFO = "vad.dashing.tbox.GET_INFO"
        const val ACTION_SUSPEND_OVERLAYS = "vad.dashing.tbox.SUSPEND_OVERLAYS"
        const val ACTION_RESUME_OVERLAYS = "vad.dashing.tbox.RESUME_OVERLAYS"
        const val ACTION_READ_ALL_SMS = "vad.dashing.tbox.READ_ALL_SMS"
        /** End current active trip and start a new one (same as manual "finish" in UI). */
        const val ACTION_TRIP_FINISH_AND_START = "vad.dashing.tbox.TRIP_FINISH_AND_START"
        /**
         * Reload trips from DataStore and reset in-RAM trip tracking buffers (odometer, fuel step,
         * persist snapshot). Used after settings backup import while the service is running.
         */
        const val ACTION_RELOAD_TRIPS_FROM_STORE = "vad.dashing.tbox.RELOAD_TRIPS_FROM_STORE"
        /**
         * Bring [MainActivity] to the foreground (singleTask). Optional delay via [EXTRA_OPEN_MAIN_DELAY_MS].
         * Repeated intents cancel the previous scheduled launch and start a new timer.
         * After the first launch, waits two seconds and, if [MainActivityForegroundTracker] still
         * reports [MainActivity] not in the foreground, retries once.
         */
        const val ACTION_OPEN_MAIN_ACTIVITY = "vad.dashing.tbox.OPEN_MAIN_ACTIVITY"
        const val EXTRA_OPEN_MAIN_DELAY_MS = "vad.dashing.tbox.EXTRA_OPEN_MAIN_DELAY_MS"
        /** Double-tap on «hide floating panels» tile: hide other overlays or restore them. */
        const val ACTION_TOGGLE_HIDE_OTHER_FLOATING_PANELS =
            "vad.dashing.tbox.TOGGLE_HIDE_OTHER_FLOATING_PANELS"
        const val EXTRA_FLOATING_PANEL_ORIGIN_ID = "vad.dashing.tbox.EXTRA_FLOATING_PANEL_ORIGIN_ID"
        /**
         * When true (default), [EXTRA_FLOATING_PANEL_ORIGIN_ID] is kept visible and the rest are hidden.
         * When false (embedded dashboard / MainScreen), all currently shown floating panels are hidden.
         */
        const val EXTRA_FLOATING_HIDE_EXCLUDE_ORIGIN =
            "vad.dashing.tbox.EXTRA_FLOATING_HIDE_EXCLUDE_ORIGIN"
        /** Flip `enabled` on other floating panels (or all when extra is true). */
        const val ACTION_TOGGLE_FLOATING_PANELS_ENABLED =
            "vad.dashing.tbox.TOGGLE_FLOATING_PANELS_ENABLED"
        const val EXTRA_TOGGLE_FLOATING_ENABLED_ALL =
            "vad.dashing.tbox.EXTRA_TOGGLE_FLOATING_ENABLED_ALL"

        private const val MOTOR_HOURS_PERSIST_INTERVAL_MS = 10 * 60 * 1000L
        private const val TRIPS_PERSIST_INTERVAL_MS = 10 * 60 * 1000L
        private const val OPEN_MAIN_ACTIVITY_VERIFY_DELAY_MS = 2000L
        /** No Tbox client reconnect attempts until this long after [serviceStartElapsed] (first init / first reply). */
        private const val TBOX_RECONNECT_WATCHDOG_GRACE_MS = 60_000L
        /** Pauses between client teardown/rebuild while [TboxRepository.tboxConnected] stays false (then last value repeats). */
        private val TBOX_RECONNECT_INTERVALS_MS = longArrayOf(60_000L, 120_000L, 600_000L, 600_000L)
        /** [SharingStarted] for settings collected in [startSettingsListener]; avoids eager DataStore until first subscriber. */
        private val settingsFlowWhileSubscribed = SharingStarted.WhileSubscribed(5_000L)
        private const val REFUEL_PRICE_COORDINATE_WAIT_MS = 5 * 60 * 1000L
        private const val REFUEL_PRICE_COORDINATE_POLL_MS = 5 * 1000L
    }

    private fun bindSettingsStateFlows(settingsSnap: BackgroundServiceSettingsSnapshot?) {
        val eager = SharingStarted.Eagerly
        val warmOnCollect = settingsFlowWhileSubscribed
        if (settingsSnap != null) {
            autoModemRestart = settingsManager.autoModemRestartFlow
                .stateIn(scope, eager, settingsSnap.autoModemRestart)
            autoTboxReboot = settingsManager.autoTboxRebootFlow
                .stateIn(scope, eager, settingsSnap.autoTboxReboot)
            autoSuspendTboxApp = settingsManager.autoSuspendTboxAppFlow
                .stateIn(scope, eager, settingsSnap.autoSuspendTboxApp)
            autoStopTboxApp = settingsManager.autoStopTboxAppFlow
                .stateIn(scope, eager, settingsSnap.autoStopTboxApp)
            autoSuspendTboxMdc = settingsManager.autoSuspendTboxMdcFlow
                .stateIn(scope, eager, settingsSnap.autoSuspendTboxMdc)
            autoStopTboxMdc = settingsManager.autoStopTboxMdcFlow
                .stateIn(scope, eager, settingsSnap.autoStopTboxMdc)
            autoSuspendTboxSwd = settingsManager.autoSuspendTboxSwdFlow
                .stateIn(scope, eager, settingsSnap.autoSuspendTboxSwd)
            autoPreventTboxRestart = settingsManager.autoPreventTboxRestartFlow
                .stateIn(scope, eager, settingsSnap.autoPreventTboxRestart)
            getCanFrame = settingsManager.getCanFrameFlow
                .stateIn(scope, eager, settingsSnap.getCanFrame)
            getCycleSignal = settingsManager.getCycleSignalFlow
                .stateIn(scope, eager, settingsSnap.getCycleSignal)
            getLocData = settingsManager.getLocDataFlow
                .stateIn(scope, warmOnCollect, settingsSnap.getLocData)
            widgetShowIndicator = settingsManager.widgetShowIndicatorFlow
                .stateIn(scope, eager, settingsSnap.widgetShowIndicator)
            widgetShowLocIndicator = settingsManager.widgetShowLocIndicatorFlow
                .stateIn(scope, eager, settingsSnap.widgetShowLocIndicator)
            mockLocation = settingsManager.mockLocationFlow
                .stateIn(scope, warmOnCollect, settingsSnap.mockLocation)
            floatingDashboards = settingsManager.floatingDashboardsFlow
                .stateIn(scope, warmOnCollect, settingsSnap.floatingDashboards)
            canDataSaveCount = settingsManager.canDataSaveCountFlow
                .stateIn(scope, eager, settingsSnap.canDataSaveCount)
            fuelTankLitersSetting = settingsManager.fuelTankLitersFlow
                .stateIn(scope, eager, settingsSnap.fuelTankLiters)
            fuelPriceFuelIdSetting = settingsManager.fuelPriceFuelIdFlow
                .stateIn(scope, eager, settingsSnap.fuelPriceFuelId)
            splitTripTimeMinutesSetting = settingsManager.splitTripTimeMinutesFlow
                .stateIn(scope, eager, settingsSnap.splitTripTimeMinutes)
        } else {
            autoModemRestart = settingsManager.autoModemRestartFlow
                .stateIn(scope, eager, false)
            autoTboxReboot = settingsManager.autoTboxRebootFlow
                .stateIn(scope, eager, false)
            autoSuspendTboxApp = settingsManager.autoSuspendTboxAppFlow
                .stateIn(scope, eager, false)
            autoStopTboxApp = settingsManager.autoStopTboxAppFlow
                .stateIn(scope, eager, false)
            autoSuspendTboxMdc = settingsManager.autoSuspendTboxMdcFlow
                .stateIn(scope, eager, false)
            autoStopTboxMdc = settingsManager.autoStopTboxMdcFlow
                .stateIn(scope, eager, false)
            autoSuspendTboxSwd = settingsManager.autoSuspendTboxSwdFlow
                .stateIn(scope, eager, false)
            autoPreventTboxRestart = settingsManager.autoPreventTboxRestartFlow
                .stateIn(scope, eager, false)
            getCanFrame = settingsManager.getCanFrameFlow
                .stateIn(scope, eager, true)
            getCycleSignal = settingsManager.getCycleSignalFlow
                .stateIn(scope, eager, false)
            getLocData = settingsManager.getLocDataFlow
                .stateIn(scope, warmOnCollect, true)
            widgetShowIndicator = settingsManager.widgetShowIndicatorFlow
                .stateIn(scope, eager, false)
            widgetShowLocIndicator = settingsManager.widgetShowLocIndicatorFlow
                .stateIn(scope, eager, false)
            mockLocation = settingsManager.mockLocationFlow
                .stateIn(scope, warmOnCollect, false)
            floatingDashboards = settingsManager.floatingDashboardsFlow
                .stateIn(scope, warmOnCollect, emptyList())
            canDataSaveCount = settingsManager.canDataSaveCountFlow
                .stateIn(scope, eager, 5)
            fuelTankLitersSetting = settingsManager.fuelTankLitersFlow
                .stateIn(scope, eager, 57)
            fuelPriceFuelIdSetting = settingsManager.fuelPriceFuelIdFlow
                .stateIn(scope, eager, FuelTypes.DEFAULT_FUEL_ID)
            splitTripTimeMinutesSetting = settingsManager.splitTripTimeMinutesFlow
                .stateIn(scope, eager, 5)
        }
    }

    override fun onCreate() {
        super.onCreate()

        timingReset()
        timingMark("onCreate_start")

        settingsManager = SettingsManager(this)
        appDataManager = AppDataManager(this)
        scope = CoroutineScope(Dispatchers.Default + job + exceptionHandler)

        infraBootstrapJob = scope.launch {
            try {
                val settingsSnap = try {
                    withContext(Dispatchers.IO) {
                        settingsManager.readBackgroundServiceSettingsSnapshot()
                    }
                } catch (e: Exception) {
                    Log.e("Background Service", "Initial settings snapshot read failed", e)
                    TboxRepository.addLog(
                        "ERROR",
                        "Background Service",
                        "Settings snapshot: ${e.message}"
                    )
                    null
                }
                bindSettingsStateFlows(settingsSnap)
                try {
                    coroutineScope {
                        val tripsJsonDeferred = async(Dispatchers.IO) {
                            appDataManager.tripsJsonFlow.first()
                        }
                        val favoritesJsonDeferred = async(Dispatchers.IO) {
                            appDataManager.tripFavoritesJsonFlow.first()
                        }
                        tripsFromDiskReady.set(false)
                        TripRepository.setTripsFromStore(
                            tripsListFromJson(tripsJsonDeferred.await()),
                            favoritesSetFromJson(favoritesJsonDeferred.await())
                        )
                        tripsFromDiskReady.set(true)
                    }
                } catch (e: Exception) {
                    Log.e("Background Service", "Initial trips load failed", e)
                    TboxRepository.addLog("ERROR", "Background Service", "Initial trips: ${e.message}")
                    tripsFromDiskReady.set(true)
                }
            } finally {
                withContext(NonCancellable) {
                    if (!::autoModemRestart.isInitialized) {
                        try {
                            bindSettingsStateFlows(null)
                        } catch (e: Exception) {
                            Log.e("Background Service", "Emergency settings bind failed", e)
                        }
                    }
                    if (!tripsFromDiskReady.get()) {
                        tripsFromDiskReady.set(true)
                    }
                    if (!serviceInfraReady.isCompleted) {
                        serviceInfraReady.complete(Unit)
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(TboxBroadcastReceiver.MAIN_ACTION)
            addAction(TboxBroadcastReceiver.GET_STATE)
            addAction(TboxBroadcastReceiver.SUBSCRIBE)
            addAction(TboxBroadcastReceiver.UNSUBSCRIBE)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // API 33+
                registerReceiver(broadcastReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                // API < 33
                registerReceiver(broadcastReceiver, filter)
            }

            Log.d("Background Service", "TboxBroadcastReceiver registered")
        } catch (e: Exception) {
            TboxRepository.addLog("ERROR", "Background Service", "Failed to register TboxBroadcastReceiver")
            Log.e("Background Service", "Failed to register TboxBroadcastReceiver", e)
        }
        timingMark("onCreate_receiver")

        scope.launch {
            try {
                settingsManager.ensureDefaultFloatingDashboards()
            } catch (e: Exception) {
                Log.e("Background Service", "ensureDefaultFloatingDashboards failed", e)
            }
            try {
                setupThemeObserver()
                Log.d("Theme Service", "Service created successfully")
            } catch (e: Exception) {
                TboxRepository.addLog("ERROR", "Theme Service", "Failed to create service")
                Log.e("Theme Service", "Failed to create service", e)
            }
        }
        createNotificationChannel()
        timingMark("onCreate_done")
        timingLog("Timings.onCreate")
    }

    private suspend fun ensureServiceInfraReady() {
        serviceInfraReady.await()
    }

    private fun setupThemeObserver() {
        try {
            themeObserver = ThemeObserver(this) { themeMode ->
                handleThemeChange(themeMode)
            }
            themeObserver?.startObserving()
        } catch (e: Exception) {
            TboxRepository.addLog("ERROR", "Theme Service", "Failed to setup theme observer")
            Log.e("Theme Service", "Failed to setup theme observer", e)
            // Используем тему по умолчанию
            handleThemeChange(1)
        }
    }

    private fun handleThemeChange(themeMode: Int) {
        try {
            TboxRepository.updateCurrentTheme(themeMode)
        } catch (e: Exception) {
            TboxRepository.addLog("ERROR", "Theme Service", "Error handling theme change")
            Log.e("Theme Service", "Error handling theme change", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch(Dispatchers.Main.immediate + exceptionHandler) {
            commandRouterMutex.withLock {
                val kickoffStart = intent?.action == ACTION_START && !isRunning
                if (kickoffStart) {
                    isRunning = true
                    val notification = withContext(Dispatchers.Default) {
                        createNotification("Start service")
                    }
                    startForeground(NOTIFICATION_ID, notification)
                    val startSourceAction = intent?.getStringExtra(EXTRA_START_SOURCE_ACTION)
                        ?.takeIf { it.isNotBlank() }
                    if (startSourceAction != null) {
                        TboxRepository.addLog(
                            "INFO",
                            "Service",
                            "Start service (source: $startSourceAction)"
                        )
                    } else {
                        TboxRepository.addLog("INFO", "Service", "Start service")
                    }
                }
                handleStartCommandIntent(intent, flags, startId, kickoffStart)
            }
        }
        return START_STICKY
    }

    private suspend fun handleStartCommandIntent(
        intent: Intent?,
        flags: Int,
        startId: Int,
        kickoffStart: Boolean,
    ) {
        ensureServiceInfraReady()
        when (intent?.action) {
            ACTION_START -> {
                if (!kickoffStart) return
                val startFromBoot = intent.getBooleanExtra(EXTRA_START_FROM_BOOT, false)
                serviceStartupJob?.cancel()
                serviceStartupJob = scope.launch(exceptionHandler) {
                    try {
                        timingReset()
                        timingMark("startup_begin")
                        if (!isRunning) return@launch
                        servicePhase = ServiceLifecyclePhase.Starting
                        TripRepository.setTripsProcessingEnabled(false)
                        reloadTripsFromDataStoreSuspend()
                        if (!isRunning) return@launch
                        TboxRepository.updateServiceStartTime()
                        val splitWindowMs =
                            splitTripTimeMinutesSetting.value.toLong() * 60_000L
                        resetTripStateForNewServiceSession(splitWindowMs)
                        applyTripResumeIfLastTripContinues(splitWindowMs)
                        if (!isRunning) return@launch
                        timingMark("startup_trips_ready")
                        connectTboxClient()
                        timingMark("startup_tbox_connected")
                        startSettingsListener()
                        yield()
                        startNetUpdater()
                        yield()
                        startAPNUpdater()
                        yield()
                        startCheckConnection()
                        yield()
                        startTboxClientReconnectWatchdog()
                        yield()
                        startPeriodicJob()
                        yield()
                        startDataListener()
                        timingMark("startup_listeners")
                        if (startFromBoot) {
                            maybeOpenMainScreenAfterBootSuspend()
                        }
                        TripRepository.setTripsProcessingEnabled(true)
                        servicePhase = ServiceLifecyclePhase.Running
                        timingMark("startup_running")
                        timingLog("Timings.startup")
                    } catch (e: CancellationException) {
                        servicePhase = ServiceLifecyclePhase.Idle
                        TripRepository.setTripsProcessingEnabled(false)
                        throw e
                    } catch (e: Exception) {
                        Log.e("Background Service", "Service startup pipeline failed", e)
                        TboxRepository.addLog(
                            "ERROR",
                            "Service",
                            "Startup failed: ${e.message}"
                        )
                        servicePhase = ServiceLifecyclePhase.Idle
                        TripRepository.setTripsProcessingEnabled(false)
                    }
                }
            }
            ACTION_RELOAD_TRIPS_FROM_STORE -> {
                if (isRunning) {
                    TripRepository.setTripsProcessingEnabled(false)
                    try {
                        reloadTripsFromDataStoreSuspend()
                        if (!isRunning) return
                        val splitWindowMs =
                            splitTripTimeMinutesSetting.value.toLong() * 60_000L
                        resetTripStateForNewServiceSession(splitWindowMs)
                        // Do not call applyTripResumeIfLastTripContinues: imported DataStore snapshot
                        // is authoritative; resume logic could reopen a recently ended trip within split window.
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("Background Service", "Reload trips failed", e)
                        TboxRepository.addLog(
                            "ERROR",
                            "Service",
                            "Reload trips: ${e.message}"
                        )
                    } finally {
                        if (isRunning) {
                            TripRepository.setTripsProcessingEnabled(true)
                        }
                    }
                }
            }
            ACTION_STOP -> {
                if (isRunning) {
                    servicePhase = ServiceLifecyclePhase.Stopping
                    TripRepository.setTripsProcessingEnabled(false)
                    serviceStartupJob?.cancel()
                    serviceStartupJob = null
                    tripsFromDiskReady.set(false)
                    openMainActivityJob?.cancel()
                    openMainActivityJob = null
                    isRunning = false
                    TboxRepository.addLog("INFO", "Service", "Stop service")
                    stopNetUpdater()
                    stopAPNUpdater()
                    stopCheckConnection()
                    stopTboxClientReconnectWatchdog()
                    stopPeriodicJob()
                    stopSettingsListener()
                    stopDataListener()
                    scope.launch { finalizeTripsOnServiceStop() }
                    stopStateBroadcastListener()
                    stopReadAllSMS()
                    disconnectTboxClient()
                    val notification = withContext(Dispatchers.Default) {
                        createNotification("Stop service")
                    }
                    startForeground(NOTIFICATION_ID, notification)
                    overlayController.closeAllOverlays()
                    servicePhase = ServiceLifecyclePhase.Idle
                }
            }
            ACTION_SEND_AT -> {
                val atCmd = intent.getStringExtra(EXTRA_AT_CMD) ?: "ATI"
                mdcSendAT((atCmd).toByteArray())
            }
            ACTION_MODEM_CHECK -> modemMode(-1)
            ACTION_MODEM_OFF -> modemMode(0)
            ACTION_MODEM_ON -> modemMode(1)
            ACTION_MODEM_FLY -> modemMode(4)
            ACTION_TBOX_REBOOT -> crtRebootTbox()
            ACTION_APN1_RESTART -> mdcSendAPNManage(byteArrayOf(0x00, 0x00, 0x01, 0x00))
            ACTION_APN1_FLY -> mdcSendAPNManage(byteArrayOf(0x00, 0x00, 0x02, 0x00))
            ACTION_APN1_RECONNECT -> mdcSendAPNManage(byteArrayOf(0x00, 0x00, 0x03, 0x00))
            ACTION_APN2_RESTART -> mdcSendAPNManage(byteArrayOf(0x00, 0x01, 0x01, 0x00))
            ACTION_APN2_FLY -> mdcSendAPNManage(byteArrayOf(0x00, 0x01, 0x02, 0x00))
            ACTION_APN2_RECONNECT -> mdcSendAPNManage(byteArrayOf(0x00, 0x01, 0x03, 0x00))
            ACTION_PIN -> {
                val pin = intent.getStringExtra(EXTRA_PIN) ?: ""
                mdcSendAT("AT+CPIN=\"$pin\"".toByteArray())
            }
            ACTION_PUK -> {
                val pin = intent.getStringExtra(EXTRA_PIN) ?: ""
                val puk = intent.getStringExtra(EXTRA_PUK) ?: ""
                mdcSendAT("AT+CPIN=\"$puk\",\"$pin\"".toByteArray())
            }
            ACTION_TBOX_APP_SUSPEND -> {
                val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: ""
                sendControlTboxApplication(appName, "SUSPEND")
            }
            ACTION_TBOX_APP_RESUME -> {
                val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: ""
                sendControlTboxApplication(appName, "RESUME")
            }
            ACTION_TBOX_APP_STOP -> {
                val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: ""
                sendControlTboxApplication(appName, "STOP")
            }
            ACTION_GET_INFO -> getInfo()
            ACTION_SUSPEND_OVERLAYS -> overlayController.suspendOverlays()
            ACTION_RESUME_OVERLAYS -> {
                overlayController.resumeOverlays()
                scope.launch {
                    overlayController.ensureFloatingDashboards(floatingDashboards.value)
                }
            }
            ACTION_CLOSE -> crtCmd(0x26,
                ByteArray(45).apply {
                    this[0] = 0x02 },
                "Close", "INFO")
            ACTION_OPEN -> crtCmd(0x26,
                ByteArray(45).apply {
                    this[0] = 0x02
                    this[9] = 0x01 },
                "Open", "INFO")
            ACTION_READ_ALL_SMS -> readAllSMS()
            ACTION_TRIP_FINISH_AND_START -> {
                if (isRunning) {
                    scope.launch {
                        finishActiveTripAndStartNew()
                    }
                }
            }
            ACTION_OPEN_MAIN_ACTIVITY -> {
                val delayMs = intent.getLongExtra(EXTRA_OPEN_MAIN_DELAY_MS, 0L).coerceAtLeast(0L)
                scheduleOpenMainActivity(delayMs)
            }
            ACTION_TOGGLE_HIDE_OTHER_FLOATING_PANELS -> {
                val excludeOrigin = intent.getBooleanExtra(EXTRA_FLOATING_HIDE_EXCLUDE_ORIGIN, true)
                val originId = intent.getStringExtra(EXTRA_FLOATING_PANEL_ORIGIN_ID).orEmpty()
                if (!(excludeOrigin && originId.isBlank())) {
                    scope.launch {
                        overlayController.toggleHideOtherFloatingPanels(
                            originPanelId = originId,
                            currentlyShownIds = TboxRepository.floatingDashboardShownIds.value,
                            excludeOriginPanel = excludeOrigin
                        )
                        overlayController.syncFloatingDashboards(floatingDashboards.value)
                        overlayController.ensureFloatingDashboards(floatingDashboards.value)
                    }
                }
            }
            ACTION_TOGGLE_FLOATING_PANELS_ENABLED -> {
                val toggleAll = intent.getBooleanExtra(EXTRA_TOGGLE_FLOATING_ENABLED_ALL, false)
                val originId = intent.getStringExtra(EXTRA_FLOATING_PANEL_ORIGIN_ID).orEmpty()
                if (toggleAll || originId.isNotBlank()) {
                    scope.launch {
                        val updated = withContext(Dispatchers.IO) {
                            val current = settingsManager.floatingDashboardsFlow.first()
                            val toggled = if (toggleAll) {
                                current.map { it.copy(enabled = !it.enabled) }
                            } else {
                                current.map { cfg ->
                                    if (cfg.id != originId) cfg.copy(enabled = !cfg.enabled) else cfg
                                }
                            }
                            settingsManager.saveFloatingDashboards(toggled)
                            toggled
                        }
                        overlayController.clearHiddenFloatingPanelIds()
                        overlayController.syncFloatingDashboards(updated)
                        overlayController.ensureFloatingDashboards(updated)
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "TBox Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background TBox monitoring"
            setShowBadge(false)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(text: String?): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TBox Monitor")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun connectTboxClient() {
        if (tBoxClient != null) return
        val remoteIp = DEFAULT_TBOX_IP
        val remoteAddress = try {
            InetAddress.getByName(remoteIp)
        } catch (e: Exception) {
            TboxRepository.addLog("ERROR", "TBox Proxy", "Invalid remote IP: $remoteIp")
            Log.e("TBox Proxy", "Invalid remote IP", e)
            return
        }
        val callback = object : TBoxClientCallback {
            override fun onDataReceived(data: ByteArray) {
                lastPacketAtMs = System.currentTimeMillis()
                scope.launch(packetProcessingDispatcher) {
                    try {
                        val packet = DatagramPacket(data, data.size, remoteAddress, serverPort)
                        responseWork(packet)
                        if (!TboxRepository.tboxConnected.value) {
                            onTboxConnected(true)
                        }
                    } catch (e: Exception) {
                        TboxRepository.addLog("ERROR", "TBox Proxy", "Error handling incoming data")
                        Log.e("TBox Proxy", "Error handling incoming data", e)
                    }
                }
            }

            override fun onLogMessage(type: LogType, tag: String, message: String) {
                when (type) {
                    LogType.ERROR -> TboxRepository.addLog("ERROR", "TBox Proxy/$tag", message)
                    LogType.WARN -> TboxRepository.addLog("WARN", "TBox Proxy/$tag", message)
                    else -> Unit
                }
            }

            override fun onConnectionChanged(connected: Boolean) {
                TboxRepository.addLog(
                    "INFO",
                    "TBox Proxy",
                    "Bridge connection state: ${if (connected) "connected" else "disconnected"}"
                )
                if (!connected && TboxRepository.tboxConnected.value) {
                    onTboxConnected(false)
                }
            }
        }

        try {
            tBoxClient = TBoxClient(
                context = applicationContext,
                remotePort = serverPort,
                remoteAddress = remoteIp,
                callback = callback
            ).also {
                it.initialize()
            }
            lastPacketAtMs = System.currentTimeMillis()
            TboxRepository.addLog("INFO", "TBox Proxy", "Client initialized for $remoteIp")
        } catch (e: Exception) {
            TboxRepository.addLog("ERROR", "TBox Proxy", "Failed to initialize client for $remoteIp")
            Log.e("TBox Proxy", "Failed to initialize client", e)
            tBoxClient = null
        }
    }

    private fun disconnectTboxClient() {
        try {
            tBoxClient?.destroy()
        } catch (e: Exception) {
            TboxRepository.addLog("ERROR", "TBox Proxy", "Failed to destroy client")
            Log.e("TBox Proxy", "Failed to destroy client", e)
        } finally {
            tBoxClient = null
        }
    }

    private fun startNetUpdater() {
        if (mainJob?.isActive == true) return
        mainJob = scope.launch {
            try {
                delay(2000)
                Log.d("Net Updater", "Start updating network state")
                while (isActive) {
                    TboxRepository.addLog("DEBUG", "MDC send", "Update network")
                    sendTboxMessage(
                        MDC_CODE,
                        SELF_CODE,
                        0x07,
                        byteArrayOf(0x01, 0x00), false
                    )
                    netUpdateCount += 1
                    if (netUpdateCount > 2) {
                        TboxRepository.updateNetState(NetState())
                    }
                    delay(netUpdateTime)
                }
            } catch (e: CancellationException) {
                // Нормальная отмена - не логируем
                throw e
            } catch (e: Exception) {
                TboxRepository.addLog("ERROR", "Net Updater", "Fatal error in Net Updater job")
                Log.e("Net Updater", "Fatal error in Net updater", e)
            }
        }
    }

    private fun stopNetUpdater() {
        mainJob?.cancel()
        mainJob = null
    }

    private fun startAPNUpdater() {
        if (apnJob?.isActive == true) return
        apnJob = scope.launch {
            try {
                Log.d("APNUpdater", "Start updating APN state")
                while (isActive) {
                    if (TboxRepository.tboxConnected.value) {
                        if (!apnCheck) {
                            delay(1000)
                            continue
                        }
                        TboxRepository.addLog("DEBUG", "MDC send", "Update APN1")
                        sendTboxMessage(
                            MDC_CODE,
                            SELF_CODE,
                            0x11,
                            byteArrayOf(0x00, 0x00, 0x00, 0x00), false
                        )
                        apn1UpdateCount += 1
                        if (apn1UpdateCount > 2) {
                            TboxRepository.updateAPNState(APNState())
                        }
                        delay(500)
                        TboxRepository.addLog("DEBUG", "MDC send", "Update APN2")
                        sendTboxMessage(
                            MDC_CODE,
                            SELF_CODE,
                            0x11,
                            byteArrayOf(0x00, 0x00, 0x01, 0x00), false
                        )
                        apn2UpdateCount += 1
                        if (apn2UpdateCount > 2) {
                            TboxRepository.updateAPN2State(APNState())
                        }

                        if (TboxRepository.apnState.value.apnStatus == true ||
                            TboxRepository.apn2State.value.apnStatus == true
                        ) {
                            TboxRepository.updateAPNStatus(true)
                        } else {
                            TboxRepository.updateAPNStatus(false)
                        }
                        delay(apnUpdateTime)
                    }
                    else {
                        delay(1000)
                        continue
                    }
                }
            } catch (e: CancellationException) {
                // Нормальная отмена - не логируем
                throw e
            } catch (e: Exception) {
                TboxRepository.addLog("ERROR", "APN Updater", "Fatal error in APN Updater job")
                Log.e("APN Updater", "Fatal error in APN updater", e)
            }
        }
    }

    private fun stopAPNUpdater() {
        apnJob?.cancel()
        apnJob = null
    }

    private fun startCheckConnection() {
        if (checkConnectionJob?.isActive == true) return
        checkConnectionJob = scope.launch {
            try {
                var rebootTimeout = 60000L
                var modemCheckTimeout = 15000L
                delay(10000)
                Log.d("Connection checker", "Start check connection")
                while (isActive) {
                    delay(modemCheckTimeout)
                    if (!TboxRepository.tboxConnected.value) {
                        modemCheckTimeout = 15000
                        continue
                    }
                    if (checkConnection()) {
                        modemCheckTimeout = 15000
                        rebootTimeout = 600000
                        continue
                    }
                    if (autoModemRestart.value) {
                        if (!checkConnection()) {
                            delay(10000)
                            if (!TboxRepository.tboxConnected.value) {
                                continue
                            }
                            if (checkConnection()) {
                                modemCheckTimeout = 15000
                                rebootTimeout = 600000
                                continue
                            }
                            TboxRepository.addLog("WARN", "Net connection checker",
                                "No network connection. Restart modem")
                            modemMode(0, needCheck = false)
                            delay(5000)
                            modemMode(1, timeout = 1000)
                            delay(5000)
                            if (TboxRepository.modemStatus.value != 1) {
                                modemMode(1)
                            }
                            delay(10000)
                            modemCheckTimeout = 300000
                            if (!TboxRepository.tboxConnected.value) {
                                continue
                            }

                            if (!checkConnection()) {
                                if (autoTboxReboot.value) {
                                    TboxRepository.addLog("WARN", "Net connection checker",
                                        "No network connection. Restart TBox")
                                    crtRebootTbox()
                                    delay(rebootTimeout)
                                    rebootTimeout = if (rebootTimeout == 60000L){
                                        600000
                                    } else {
                                        1800000
                                    }
                                }
                            } else {
                                TboxRepository.addLog("INFO", "Net connection checker",
                                    "Network connection restored")
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                // Нормальная отмена - не логируем
                throw e
            } catch (e: Exception) {
                TboxRepository.addLog("ERROR", "Connection checker", "Fatal error in Connection checker job")
                Log.e("Connection checker", "Fatal error in connection checker", e)
            }
        }
    }

    private fun checkConnection(): Boolean {
        return TboxRepository.netState.value.netStatus in listOf("2G", "3G", "4G") &&
            TboxRepository.apnStatus.value
    }

    private fun stopCheckConnection() {
        checkConnectionJob?.cancel()
        checkConnectionJob = null
    }

    /**
     * While the service runs: if [TboxRepository.tboxConnected] stays false, periodically
     * [disconnectTboxClient] + [connectTboxClient] with gaps 30s / 45s / 60s / 120s… from the
     * offline episode start (cold start counts from service start). No attempts until
     * [TBOX_RECONNECT_WATCHDOG_GRACE_MS] after service start.
     */
    private fun startTboxClientReconnectWatchdog() {
        if (tboxClientReconnectJob?.isActive == true) return
        val serviceStartElapsed = SystemClock.elapsedRealtime()
        tboxClientReconnectJob = scope.launch(exceptionHandler) {
            var hadSuccessfulTboxConnection = false
            var disconnectEpisodeStartElapsed = -1L
            var reconnectsDoneInEpisode = 0
            var nextAttemptElapsedDeadline = -1L
            while (isActive) {
                delay(1000)
                if (!isRunning) break

                val now = SystemClock.elapsedRealtime()

                if (TboxRepository.tboxConnected.value) {
                    hadSuccessfulTboxConnection = true
                    disconnectEpisodeStartElapsed = -1L
                    reconnectsDoneInEpisode = 0
                    nextAttemptElapsedDeadline = -1L
                    continue
                }

                if (disconnectEpisodeStartElapsed < 0L) {
                    disconnectEpisodeStartElapsed =
                        if (hadSuccessfulTboxConnection) now else serviceStartElapsed
                    val firstGapEnd =
                        disconnectEpisodeStartElapsed + TBOX_RECONNECT_INTERVALS_MS[0]
                    val earliestWork = serviceStartElapsed + TBOX_RECONNECT_WATCHDOG_GRACE_MS
                    nextAttemptElapsedDeadline = max(firstGapEnd, earliestWork)
                }

                if (now < nextAttemptElapsedDeadline) continue

                tboxClientReconnectMutex.withLock {
                    if (!isRunning) return@withLock
                    if (TboxRepository.tboxConnected.value) return@withLock
                    val nextGapIdx = minOf(
                        reconnectsDoneInEpisode + 1,
                        TBOX_RECONNECT_INTERVALS_MS.size - 1
                    )
                    val nextPauseMs = TBOX_RECONNECT_INTERVALS_MS[nextGapIdx]
                    TboxRepository.addLog(
                        "WARN",
                        "TBox Proxy",
                        "Tbox offline; reconnecting client (episode attempt ${reconnectsDoneInEpisode + 1}, " +
                            "next pause ${nextPauseMs}ms)"
                    )
                    disconnectTboxClient()
                    connectTboxClient()
                    reconnectsDoneInEpisode += 1
                    nextAttemptElapsedDeadline =
                        SystemClock.elapsedRealtime() + TBOX_RECONNECT_INTERVALS_MS[
                            minOf(
                                reconnectsDoneInEpisode,
                                TBOX_RECONNECT_INTERVALS_MS.size - 1
                            )
                        ]
                }
            }
        }
    }

    private fun stopTboxClientReconnectWatchdog() {
        tboxClientReconnectJob?.cancel()
        tboxClientReconnectJob = null
    }

    private fun stopStateBroadcastListener() {
        generalStateBroadcastJob?.cancel()
        generalStateBroadcastJob = null
    }

    private fun startDataListener() {
        dataListenerJob = scope.launch {
            // Запускаем коллектинг в параллельных потоках для независимой работы
            launch {
                var prevRpm = 0f
                var lastMotorHoursPeriodicPersistAt = SystemClock.elapsedRealtime()
                CanDataRepository.engineRPM
                    .drop(1)
                    .collect { rpm ->
                    try {
                        val r = rpm ?: 0f
                        val motorHours = motorHoursBuffer.updateValue(r)
                        val motorHoursTrip = motorHoursTripBuffer.updateValue(r)
                        if (motorHours != 0f) {
                            CarDataRepository.addMotorHours(motorHours)
                        }
                        if (motorHoursTrip != 0f) {
                            CanDataRepository.addMotorHoursTrip(motorHoursTrip)
                        }
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastMotorHoursPeriodicPersistAt >= MOTOR_HOURS_PERSIST_INTERVAL_MS &&
                            CarDataRepository.needsPersistence()
                        ) {
                            lastMotorHoursPeriodicPersistAt = now
                            persistMotorHoursToStore()
                        }
                        if (prevRpm > 0f && r == 0f) {
                            persistMotorHoursToStore()
                        }
                        onTripRpmSample(r, prevRpm, now)
                        prevRpm = r
                    } catch (e: Exception) {
                        TboxRepository.addLog("ERROR", "Data Listener",
                            "Fatal error in motor hours")
                        Log.e("Data Listener", "Fatal error in motor hours", e)
                    }
                }
            }
        }
    }

    /** True when [rpm] is a running engine and the previous sample was stopped or unknown (treated as 0). */
    private fun isTripEngineStartEdge(prevRpm: Float, rpm: Float): Boolean =
        rpm > 0f && prevRpm <= 0f

    /**
     * If the active trip has no start odometer yet but [CanDataRepository.odometer] is available,
     * persist it once and align [tripStartOdometer]/[tripLastOdometer] for distance math.
     */
    private fun maybeBackfillActiveTripOdometerStart() {
        TripRepository.updateActiveTrip { cur ->
            if (cur.odometerStartKm != null) return@updateActiveTrip cur
            val odo = CanDataRepository.odometer.value ?: return@updateActiveTrip cur
            tripStartOdometer = odo
            if (tripLastOdometer == null) tripLastOdometer = odo
            cur.copy(odometerStartKm = odo)
        }
    }

    /** Updates consumption, refuel count, persisted fuel baseline; uses [tripLastFuelPercent] as prior sample. */
    private fun applyActiveTripFuelStep(tankL: Float) {
        val pctNow = CanDataRepository.fuelLevelPercentageFiltered.value?.toFloat() ?: return
        var refuelTripId: String? = null
        var refueledLiters = 0f
        TripRepository.updateActiveTrip { cur ->
            val step = TripFuelAccounting.applyFuelPercentStep(
                currentConsumedLiters = cur.fuelConsumedLiters,
                lastPercent = tripLastFuelPercent,
                percentNow = pctNow,
                tankLiters = tankL
            )
            tripLastFuelPercent = step.baselinePercent
            if (step.refuelDetected && step.refueledLitersThisStep > 0f) {
                refuelTripId = cur.id
                refueledLiters = step.refueledLitersThisStep
            }
            cur.copy(
                fuelConsumedLiters = step.consumedLiters,
                refuelCount = cur.refuelCount + if (step.refuelDetected) 1 else 0,
                fuelRefueledLiters = cur.fuelRefueledLiters + step.refueledLitersThisStep,
                fuelBaselinePercent = step.baselinePercent,
            )
        }
        val tripId = refuelTripId ?: return
        scheduleRefuelCostUpdate(tripId, refueledLiters, fuelPriceFuelIdSetting.value)
    }

    private fun scheduleRefuelCostUpdate(tripId: String, refueledLiters: Float, fuelId: Int) {
        scope.launch {
            val coordinates = awaitFuelCoordinates() ?: run {
                TboxRepository.addLog("WARN", "Fuel price", "No coordinates for refuel price within 5 minutes")
                return@launch
            }
            val price = try {
                withContext(Dispatchers.IO) {
                    fuelPriceClient.fetchPrice(coordinates, fuelId)
                }
            } catch (e: Exception) {
                TboxRepository.addLog("WARN", "Fuel price", "Price request failed: ${e.message}")
                null
            } ?: return@launch

            val cost = FuelCostAccounting.refuelCostRub(refueledLiters, price.pricePerLiterRub)
            val trip = TripRepository.trips.value.firstOrNull { it.id == tripId } ?: return@launch
            TripRepository.replaceTrip(
                trip.copy(fuelRefueledCostRub = trip.fuelRefueledCostRub + cost)
            )
            TboxRepository.addLog(
                "INFO",
                "Fuel price",
                "Refuel cost +${String.format(Locale.US, "%.2f", cost)} RUB (${FuelTypes.optionFor(fuelId).label})"
            )
            maybePersistTrips(force = true)
        }
    }

    private suspend fun awaitFuelCoordinates(): FuelCoordinates? =
        withTimeoutOrNull(5 * 60 * 1000L) {
            while (true) {
                currentFuelCoordinatesOrNull()?.let { return@withTimeoutOrNull it }
                delay(1_000L)
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }

    private fun currentFuelCoordinatesOrNull(): FuelCoordinates? {
        val loc = TboxRepository.locValues.value
        if (!loc.locateStatus) return null
        if (loc.latitude == 0.0 && loc.longitude == 0.0) return null
        return FuelCoordinates(latitude = loc.latitude, longitude = loc.longitude)
    }

    /**
     * One decision per RPM sample: maintain active trip, end on engine off, optionally reopen the same
     * trip after a short stop, or start a new trip after a longer gap. Moving vs idle time uses speed
     * (not RPM) for samples while the trip is active and RPM > 0.
     */
    private fun onTripRpmSample(rpm: Float, prevRpm: Float, nowElapsedMs: Long) {
        if (!TripRepository.isTripsProcessingEnabled()) return
        if (!tripsFromDiskReady.get()) return
        synchronized(TripRepository.lock) {
            val wallNow = System.currentTimeMillis()
            val splitWindowMs = splitTripTimeMinutesSetting.value * 60_000L
            val tankL = fuelTankLitersSetting.value.coerceAtLeast(1).toFloat()

            if (TripRepository.activeTrip.value != null) {
                maybeBackfillActiveTripOdometerStart()
            }

            if (rpm > 0f) {
                if (tripColdResumeApplyParkedIdleOnEngineStart) {
                    val cold = tripColdResumeReopenedEndedTrip
                    if (cold != null) {
                        TripRepository.updateActiveTrip { cur ->
                            cur.copy(parkingTimeMs = cur.parkingTimeMs + cold.parkedMsAddedToIdle)
                        }
                    }
                    tripColdResumeApplyParkedIdleOnEngineStart = false
                }
                tripRpmWasPositiveSinceService = true
                tripColdResumeReopenedEndedTrip = null
            }

            // Branch: first sample after ACTION_START / reload — align buffers with store or create trip.
            if (tripFirstSampleAfterSessionStart) {
                tripFirstSampleAfterSessionStart = false
                if (TripRepository.activeTrip.value != null) {
                    // Resumed or still-active trip from store: do not advance moving/idle until next sample.
                    tripLastSampleElapsedMs = nowElapsedMs
                    tripPrevRpmForStart = rpm
                    if (tripLastFuelPercent == null) {
                        val resumed = TripRepository.activeTrip.value
                        tripLastFuelPercent = resumed?.fuelBaselinePercent
                            ?: CanDataRepository.fuelLevelPercentageFiltered.value?.toFloat()
                    }
                    if (rpm == 0f) {
                        applyActiveTripFuelStep(tankL)
                    } else {
                        TripRepository.updateActiveTrip { cur ->
                            cur.copy(engineStartCount = cur.engineStartCount + 1)
                        }
                    }
                    return@synchronized
                }
                // No active trip in store: start one only if engine already running at first sample.
                if (rpm > 0f) {
                    val p = CanDataRepository.fuelLevelPercentageFiltered.value?.toFloat()
                    val odoStart = CanDataRepository.odometer.value
                    TripRepository.startTrip(
                        TripRecord(
                            startTimeEpochMs = TboxRepository.serviceStartTime.value.time,
                            endTimeEpochMs = null,
                            odometerStartKm = odoStart,
                            fuelBaselinePercent = p,
                            engineStartCount = 1,
                        )
                    )
                    tripStartOdometer = odoStart
                    tripLastOdometer = tripStartOdometer
                    tripLastFuelPercent = p
                    tripPendingSplitTripId = null
                    tripRpmZeroAtMs = null
                    tripLastSampleElapsedMs = nowElapsedMs
                    maybePersistTrips(force = true)
                    tripPrevRpmForStart = rpm
                    return@synchronized
                }
            }

            // Engine back on after off: same trip if pause ≤ split window; add off time to parking (cumulative).
            if (rpm > 0f && tripRpmZeroAtMs != null) {
                val zeroStart = tripRpmZeroAtMs!!
                val pauseMs = wallNow - zeroStart
                if (pauseMs <= splitWindowMs) {
                    val pendingId = tripPendingSplitTripId
                    if (pendingId != null) {
                        val trip = TripRepository.trips.value.firstOrNull { it.id == pendingId }
                        if (trip != null && !trip.isActive) {
                            TripRepository.replaceTrip(trip.copy(endTimeEpochMs = null))
                            TripRepository.updateActiveTrip { cur ->
                                cur.copy(parkingTimeMs = cur.parkingTimeMs + pauseMs.coerceAtLeast(0L))
                            }
                        }
                    }
                } else {
                    tripPendingSplitTripId = null
                }
                tripRpmZeroAtMs = null
            }

            if (TripRepository.activeTrip.value != null && rpm == 0f) {
                applyActiveTripFuelStep(tankL)
            }

            // Active trip and engine running: accumulate distance and moving vs idle from elapsed CAN period.
            var active = TripRepository.activeTrip.value
            if (active != null && rpm > 0f) {
                val dt = if (tripLastSampleElapsedMs == 0L) {
                    0L
                } else {
                    (nowElapsedMs - tripLastSampleElapsedMs).coerceAtLeast(0L)
                }
                val speed = CanDataRepository.carSpeed.value ?: 0f
                val eng = CanDataRepository.engineTemperature.value
                val gb = CanDataRepository.gearBoxOilTemperature.value
                val out = CanDataRepository.outsideTemperature.value
                val odo = CanDataRepository.odometer.value
                val addEngineStart = if (isTripEngineStartEdge(prevRpm, rpm)) 1 else 0
                TripRepository.updateActiveTrip { cur ->
                    var d = cur.distanceKm
                    val lastO = tripLastOdometer
                    if (odo != null && lastO != null && odo >= lastO) {
                        d += (odo - lastO).toFloat()
                    }
                    tripLastOdometer = odo ?: tripLastOdometer
                    var mov = cur.movingTimeMs
                    var idl = cur.idleTimeMs
                    if (speed > 0f) {
                        mov += dt
                    } else {
                        idl += dt
                    }
                    val outside = TripRepository.mergeOutsideTemp(
                        cur.minOutsideTemp,
                        cur.maxOutsideTemp,
                        out
                    )
                    cur.copy(
                        distanceKm = d,
                        movingTimeMs = mov,
                        idleTimeMs = idl,
                        maxSpeed = max(cur.maxSpeed, speed),
                        maxEngineTemp = TripRepository.updateMaxEngineTemp(cur.maxEngineTemp, eng),
                        maxGearboxOilTemp = TripRepository.updateMaxGearboxTemp(cur.maxGearboxOilTemp, gb),
                        minOutsideTemp = outside.first,
                        maxOutsideTemp = outside.second,
                        engineStartCount = cur.engineStartCount + addEngineStart,
                    )
                }
                applyActiveTripFuelStep(tankL)
            }

            // Engine-off edge: end active trip; same trip may reopen above if RPM returns within split window.
            active = TripRepository.activeTrip.value
            if (active != null && prevRpm > 0f && rpm == 0f) {
                val endedTripId = active.id
                TripRepository.updateActiveTrip { it.copy(endTimeEpochMs = wallNow) }
                tripRpmZeroAtMs = wallNow
                // Must capture id before updateActiveTrip clears _activeTrip (ended trip is not "active").
                tripPendingSplitTripId = endedTripId
                maybePersistTrips(force = true)
            }

            // No active trip but engine running: new trip only after a stable zero-RPM edge (avoids duplicates).
            if (rpm > 0f && TripRepository.activeTrip.value == null) {
                val canStart = prevRpm <= 0f && tripPrevRpmForStart <= 0f && tripRpmWasPositiveSinceService
                if (canStart) {
                    val startMs = if (!tripRpmWasPositiveSinceService) {
                        TboxRepository.serviceStartTime.value.time
                    } else {
                        wallNow
                    }
                    val p = CanDataRepository.fuelLevelPercentageFiltered.value?.toFloat()
                    val odoStart = CanDataRepository.odometer.value
                    TripRepository.startTrip(
                        TripRecord(
                            startTimeEpochMs = startMs,
                            endTimeEpochMs = null,
                            odometerStartKm = odoStart,
                            fuelBaselinePercent = p,
                        )
                    )
                    tripStartOdometer = odoStart
                    tripLastOdometer = tripStartOdometer
                    tripLastFuelPercent = p
                    tripPendingSplitTripId = null
                    tripRpmZeroAtMs = null
                    tripLastSampleElapsedMs = nowElapsedMs
                    maybePersistTrips(force = true)
                }
            }

            tripLastSampleElapsedMs = nowElapsedMs
            tripPrevRpmForStart = rpm

            // Throttled JSON persist so DataStore is not written on every RPM tick.
            val rtNow = SystemClock.elapsedRealtime()
            if (rtNow - tripLastPeriodicPersistAt >= TRIPS_PERSIST_INTERVAL_MS) {
                tripLastPeriodicPersistAt = rtNow
                val curActive = TripRepository.activeTrip.value
                val shouldWrite = TripRepository.needsPersistence() &&
                    (curActive == null ||
                        tripLastPersistedSnapshot == null ||
                        TripRepository.tripChangedEnough(tripLastPersistedSnapshot!!, curActive))
                if (shouldWrite) {
                    maybePersistTrips(force = false)
                    tripLastPersistedSnapshot = curActive
                }
            }
        }
    }

    /**
     * Loads trips and favorites from disk in parallel. Clears [tripsFromDiskReady] until
     * [TripRepository] has the new snapshot so [responseWork] does not run trip/CAN side effects mid-load.
     */
    private suspend fun reloadTripsFromDataStoreSuspend() {
        tripsFromDiskReady.set(false)
        try {
            coroutineScope {
                val tripsJsonDeferred = async(Dispatchers.IO) {
                    appDataManager.tripsJsonFlow.first()
                }
                val favoritesJsonDeferred = async(Dispatchers.IO) {
                    appDataManager.tripFavoritesJsonFlow.first()
                }
                TripRepository.setTripsFromStore(
                    tripsListFromJson(tripsJsonDeferred.await()),
                    favoritesSetFromJson(favoritesJsonDeferred.await())
                )
            }
            tripsFromDiskReady.set(true)
        } catch (e: CancellationException) {
            tripsFromDiskReady.set(false)
            throw e
        } catch (e: Exception) {
            tripsFromDiskReady.set(false)
            throw e
        }
    }

    private fun maybePersistTrips(force: Boolean) {
        if (!force && !TripRepository.needsPersistence()) return
        val tripsJson = tripsListToJson(TripRepository.trips.value)
        val favJson = favoritesSetToJson(TripRepository.favoriteIds.value)
        scope.launch {
            tripsPersistMutex.withLock {
                appDataManager.saveTripsJson(tripsJson)
                appDataManager.saveTripFavoritesJson(favJson)
                TripRepository.markPersisted(tripsJson, favJson)
            }
        }
    }

    /**
     * Clears sample buffers for a new service session. If the last persisted trip ended recently
     * (within split window) but [applyTripResumeIfLastTripContinues] has not run yet, seed
     * [tripPendingSplitTripId] so the first RPM>0 sample can merge engine-off idle like in-session logic.
     */
    private fun resetTripStateForNewServiceSession(splitWindowMs: Long) {
        synchronized(TripRepository.lock) {
            tripColdResumeReopenedEndedTrip = null
            tripColdResumeApplyParkedIdleOnEngineStart = false
            tripPrevRpmForStart = 0f
            tripRpmWasPositiveSinceService = false
            tripPendingSplitTripId = null
            val nowWall = System.currentTimeMillis()
            val lastStored = TripRepository.trips.value.lastOrNull()
            if (lastStored != null && !lastStored.isActive) {
                val end = lastStored.endTimeEpochMs
                if (end != null && nowWall >= end && nowWall - end <= splitWindowMs) {
                    tripPendingSplitTripId = lastStored.id
                }
            }
            tripRpmZeroAtMs = null
            tripLastSampleElapsedMs = 0L
            tripLastOdometer = null
            tripStartOdometer = null
            tripLastFuelPercent = null
            tripLastPeriodicPersistAt = SystemClock.elapsedRealtime()
            tripLastPersistedSnapshot = TripRepository.activeTrip.value
            tripFirstSampleAfterSessionStart = true
        }
    }

    /**
     * If the last stored trip should continue (active or ended within split window), resume it
     * and seed odometer/fuel buffers so [onTripRpmSample] extends the same trip. Parked time before
     * resume is added to [TripRecord.parkingTimeMs] on first RPM>0 sample.
     * If that reopen was from a finished trip and the HU stops the service before RPM>0, [finalizeTripsOnServiceStop]
     * restores the stored end time and rolls back that idle delta so the next boot counts one continuous off segment.
     */
    private fun applyTripResumeIfLastTripContinues(splitWindowMs: Long) {
        synchronized(TripRepository.lock) {
            val resumeResult = TripRepository.tryResumeLastTripAfterServiceStart(splitWindowMs)
            if (!resumeResult.resumed) return
            tripColdResumeReopenedEndedTrip = resumeResult.reopenedEndedTrip
            tripColdResumeApplyParkedIdleOnEngineStart = resumeResult.reopenedEndedTrip != null
            tripLastOdometer = CanDataRepository.odometer.value
            tripStartOdometer = tripLastOdometer
            val active = TripRepository.activeTrip.value
            val storedBaseline = active?.fuelBaselinePercent
            val live = CanDataRepository.fuelLevelPercentageFiltered.value?.toFloat()
            tripLastFuelPercent = storedBaseline ?: live
            tripRpmZeroAtMs = null
            tripPendingSplitTripId = null
        }
        maybePersistTrips(force = true)
    }

    private suspend fun finishActiveTripAndStartNew() {
        val wallNow = System.currentTimeMillis()
        synchronized(TripRepository.lock) {
            TripRepository.activeTrip.value?.let { cur ->
                TripRepository.replaceTrip(cur.copy(endTimeEpochMs = wallNow))
            }
            tripColdResumeReopenedEndedTrip = null
            tripColdResumeApplyParkedIdleOnEngineStart = false
            val p = CanDataRepository.fuelLevelPercentageFiltered.value?.toFloat()
            val odoStart = CanDataRepository.odometer.value
            val rpmNow = CanDataRepository.engineRPM.value ?: 0f
            TripRepository.startTrip(
                TripRecord(
                    startTimeEpochMs = wallNow,
                    endTimeEpochMs = null,
                    odometerStartKm = odoStart,
                    fuelBaselinePercent = p,
                    engineStartCount = if (rpmNow > 0f) 1 else 0,
                )
            )
            tripStartOdometer = odoStart
            tripLastOdometer = tripStartOdometer
            tripLastFuelPercent = p
            tripRpmZeroAtMs = null
            tripPendingSplitTripId = null
            tripPrevRpmForStart = rpmNow
            tripRpmWasPositiveSinceService = rpmNow > 0f
        }
        val tripsJson = tripsListToJson(TripRepository.trips.value)
        val favJson = favoritesSetToJson(TripRepository.favoriteIds.value)
        tripsPersistMutex.withLock {
            appDataManager.saveTripsJson(tripsJson)
            appDataManager.saveTripFavoritesJson(favJson)
            TripRepository.markPersisted(tripsJson, favJson)
        }
    }

    private suspend fun finalizeTripsOnServiceStop() {
        val wallNow = System.currentTimeMillis()
        synchronized(TripRepository.lock) {
            val cold = tripColdResumeReopenedEndedTrip
            val active = TripRepository.activeTrip.value
            if (active != null && cold != null && active.id == cold.tripId) {
                val rpmNow = CanDataRepository.engineRPM.value ?: 0f
                if (rpmNow <= 0f) {
                    // HU off before engine start after cold resume: keep original trip end and times
                    // so the next session can add one parked segment (HU off + sit) without double count.
                    TripRepository.replaceTrip(
                        active.copy(
                            endTimeEpochMs = cold.previousEndTimeEpochMs,
                            idleTimeMs = cold.previousIdleTimeMs,
                            parkingTimeMs = cold.previousParkingTimeMs,
                        )
                    )
                } else {
                    TripRepository.replaceTrip(active.copy(endTimeEpochMs = wallNow))
                }
            } else if (active != null) {
                TripRepository.replaceTrip(active.copy(endTimeEpochMs = wallNow))
            }
            tripColdResumeReopenedEndedTrip = null
            tripColdResumeApplyParkedIdleOnEngineStart = false
            tripPrevRpmForStart = 0f
            tripRpmWasPositiveSinceService = false
            tripPendingSplitTripId = null
            tripRpmZeroAtMs = null
            tripLastSampleElapsedMs = 0L
            tripLastOdometer = null
            tripStartOdometer = null
            tripLastFuelPercent = null
            tripLastPersistedSnapshot = null
            tripFirstSampleAfterSessionStart = true
        }
        if (TripRepository.needsPersistence()) {
            val tripsJson = tripsListToJson(TripRepository.trips.value)
            val favJson = favoritesSetToJson(TripRepository.favoriteIds.value)
            tripsPersistMutex.withLock {
                appDataManager.saveTripsJson(tripsJson)
                appDataManager.saveTripFavoritesJson(favJson)
                TripRepository.markPersisted(tripsJson, favJson)
            }
        }
    }

    private suspend fun persistMotorHoursToStore() {
        val v = CarDataRepository.motorHours.value
        appDataManager.saveMotorHours(v)
        CarDataRepository.markPersisted(v)
    }

    private fun stopDataListener() {
        dataListenerJob?.cancel()
        dataListenerJob = null
    }

    private fun startSettingsListener() {
        settingsListenerJob = scope.launch {
            // Запускаем коллектинг в параллельных потоках для независимой работы
            launch {
                getLocData.collect { isGetLocData ->
                    if (!isGetLocData) {
                        TboxRepository.updateLocValues(LocValues())
                        TboxRepository.updateIsLocValuesTrue(false)
                        locSubscribe(false)
                    }
                }
            }

            launch {
                mockLocation
                    .drop(1) // Пропускаем начальное значение
                    .collect { isMockLocation ->
                    if (!isMockLocation) {
                        locationMockManager.stopMockLocation()
                    }
                }
            }

            launch {
                floatingDashboards
                    .drop(1) // Пропускаем начальное значение
                    .collect { configs ->
                        overlayController.syncFloatingDashboards(configs)
                    }
            }
        }
    }

    private fun stopSettingsListener() {
        settingsListenerJob?.cancel()
        settingsListenerJob = null
    }

    private fun startPeriodicJob() {
        if (periodicJob?.isActive == true) return
        periodicJob = scope.launch {
            try {
                Log.d("1s Job", "Start periodic job")
                delay(5000)
                overlayController.ensureFloatingDashboards(floatingDashboards.value)
                sendWidgetUpdate()
                var widgetUpdateTime = System.currentTimeMillis()
                var floatingDashboardCheckTime = System.currentTimeMillis()
                //var crtGetPowVolInfoTime = System.currentTimeMillis()
                var crtGetCanFrameTime = System.currentTimeMillis()
                var crtGetCycleSignalTime = System.currentTimeMillis()
                var crtGetLocDataTime = System.currentTimeMillis()
                var locErrorCount = 0
                var tboxAppCheckTime = System.currentTimeMillis()
                var tboxMdcCheckTime = System.currentTimeMillis()
                val periodicTasksReadyAt = System.currentTimeMillis() + 15000
                while (isActive) {
                    val now = System.currentTimeMillis()
                    if (TboxRepository.tboxConnected.value) {
                        if (now - lastPacketAtMs > netUpdateTime * 2) {
                            packetSilenceChecks += 1
                            if (packetSilenceChecks >= 3) {
                                onTboxConnected(false)
                                packetSilenceChecks = 0
                            }
                        } else {
                            packetSilenceChecks = 0
                        }
                    } else {
                        packetSilenceChecks = 0
                    }

                    if (now < periodicTasksReadyAt) {
                        delay(1000)
                        continue
                    }

                    /*if (mockLocation.value) {
                        locationMockManager.setMockLocation(LocValues(
                            rawValue = "",
                            locateStatus = true,
                            utcTime = UtcTime(),
                            longitude = 35.0,
                            latitude = 54.0,
                            altitude = 10.0,
                            visibleSatellites = 32,
                            usingSatellites = 15,
                            speed = 0.0f,
                            trueDirection = 150.0f,
                            magneticDirection = 150.0f,
                            updateTime = Date()))
                    }*/

                    if (System.currentTimeMillis() - widgetUpdateTime > 5000) {
                        sendWidgetUpdate()
                        widgetUpdateTime = System.currentTimeMillis()
                    }

                    if (System.currentTimeMillis() - floatingDashboardCheckTime > 60000) {
                        floatingDashboardCheckTime = System.currentTimeMillis()
                        overlayController.ensureFloatingDashboards(floatingDashboards.value)
                    }

                    val currentTime = Date().time

                    /*if (updateVoltages.value) {
                        val delta = currentTime - TboxRepository.voltages.value.updateTime.time
                        if (delta > 30000) {
                            TboxRepository.updateVoltages(VoltagesState(0.0, 0.0, 0.0))
                            if (TboxRepository.tboxConnected.value && Date().time - crtGetPowVolInfoTime.time > 10000) {
                                crtGetPowVolInfo()
                                crtGetPowVolInfoTime = Date()
                            }
                        }
                    }*/

                    if (getCanFrame.value) {
                        val delta = currentTime - (TboxRepository.canFrameTime.value?.time ?: 0)
                        if (delta > 60000) {
                            if (TboxRepository.tboxConnected.value &&
                                System.currentTimeMillis() - crtGetCanFrameTime > 10000
                            ) {
                                crtGetCanFrame()
                                crtGetCanFrameTime = System.currentTimeMillis()
                            }
                        }
                    }
                    /*if (getCycleSignal.value) {
                        val delta = currentTime - (TboxRepository.cycleSignalTime.value?.time ?: 0)
                        if (delta > 60000) {
                            if (TboxRepository.tboxConnected.value && System.currentTimeMillis() - crtGetCycleSignalTime > 10000) {
                                crtGetCycleSignal()
                                crtGetCycleSignalTime = System.currentTimeMillis()
                            }
                        }
                    }*/
                    if (getLocData.value) {
                        val delta = currentTime - (TboxRepository.locationUpdateTime.value?.time
                            ?: 0)
                        if (delta > 10000) {
                            TboxRepository.updateLocValues(LocValues())
                            TboxRepository.updateIsLocValuesTrue(false)
                            if (TboxRepository.tboxConnected.value && System.currentTimeMillis() - crtGetLocDataTime > 10000) {
                                locSubscribe(true)
                                crtGetLocDataTime = System.currentTimeMillis()
                            }
                        } else if (TboxRepository.locValues.value.locateStatus) {
                            if (getCanFrame.value) {
                                CanDataRepository.carSpeed.value?.let { speed ->
                                    val min = speed - 10f
                                    val max = speed + 10f
                                    TboxRepository.locValues.value.speed.let { locSpeed ->
                                        if (locSpeed >= min && locSpeed <= max) {
                                            TboxRepository.updateIsLocValuesTrue(true)
                                            locErrorCount = 0
                                        } else {
                                            if (locErrorCount < 4) {
                                                locErrorCount += 1
                                            } else {
                                                TboxRepository.updateIsLocValuesTrue(false)
                                            }
                                        }
                                    }
                                }
                            } else {
                                TboxRepository.updateIsLocValuesTrue(TboxRepository.locValues.value.locateStatus)
                            }
                        }
                    }

                    /*if (TboxRepository.tboxConnected.value) {
                        if (CanDataRepository.engineRPM.value == 800f) {
                            CanDataRepository.updateEngineRPM(850f)
                        } else {
                            CanDataRepository.updateEngineRPM(800f)
                        }
                    } else {
                        CanDataRepository.updateEngineRPM(0f)
                    }*/

                    if (TboxRepository.tboxConnected.value) {
                        if (autoSuspendTboxSwd.value) {
                            // Отправка команды suspend swd, если она не была подтверждена,
                            // но не чаще 1 раза в 15 секунд
                            val suspendTboxSwdTimeDiff = System.currentTimeMillis() - suspendTboxSwdLastTime
                            if (!TboxRepository.tboxSwdSuspended.value && suspendTboxSwdTimeDiff > 15000) {
                                sendControlTboxApplication("SWD", "SUSPEND")
                                suspendTboxSwdLastTime = System.currentTimeMillis()
                            } else if (suspendTboxSwdTimeDiff > 900000) {
                                // Отправка команды, через каждые 15 минут
                                sendControlTboxApplication("SWD", "SUSPEND")
                                suspendTboxSwdLastTime = System.currentTimeMillis()
                            }
                        }
                        if (autoSuspendTboxApp.value) {
                            // Отправка команды suspend app, если она не была подтверждена,
                            // но не чаще 1 раза в 15 секунд
                            val suspendTboxAppTimeDiff = System.currentTimeMillis() - suspendTboxAppLastTime
                            if (!TboxRepository.tboxAppSuspended.value && suspendTboxAppTimeDiff > 15000) {
                                sendControlTboxApplication("APP", "SUSPEND")
                                suspendTboxAppLastTime = System.currentTimeMillis()
                            } else if (suspendTboxAppTimeDiff > 900000) {
                                // Отправка команды, через каждые 15 минут
                                sendControlTboxApplication("APP", "SUSPEND")
                                suspendTboxAppLastTime = System.currentTimeMillis()
                            }
                        }
                        if (autoSuspendTboxMdc.value) {
                            // Отправка команды suspend mdc, если она не была подтверждена,
                            // но не чаще 1 раза в 15 секунд
                            val suspendTboxMdcTimeDiff = System.currentTimeMillis() - suspendTboxMdcLastTime
                            if (!TboxRepository.tboxMdcSuspended.value && suspendTboxMdcTimeDiff > 15000) {
                                sendControlTboxApplication("MDC", "SUSPEND")
                                suspendTboxMdcLastTime = System.currentTimeMillis()
                            } else if (suspendTboxMdcTimeDiff > 900000) {
                                // Отправка команды, через каждые 15 минут
                                sendControlTboxApplication("MDC", "SUSPEND")
                                suspendTboxMdcLastTime = System.currentTimeMillis()
                            }
                        }

                        if (autoStopTboxApp.value) {
                            // Проверка работы app
                            val tboxAppCheckTimeDiff = System.currentTimeMillis() - tboxAppCheckTime
                            if (tboxAppCheckTimeDiff > 60000) {
                                sendControlTboxApplication("APP", "VERSION")
                                TboxRepository.updateTboxAppVersionAnswer(false)
                                tboxAppCheckTime = System.currentTimeMillis()
                            }
                            // Если команда проверки версии app была отправлена больше 10 секунд назад и нет
                            // ответа, то команда остановки app считается выполненной
                            if (tboxAppCheckTimeDiff > 10000 && !TboxRepository.tboxAppVersionAnswer.value) {
                                TboxRepository.updateTboxAppStoped(true)
                            } else if (tboxAppCheckTimeDiff > 10000 && TboxRepository.tboxAppVersionAnswer.value){
                                TboxRepository.updateTboxAppStoped(false)
                            }
                            // Отправка команды stop app, если она не была подтверждена,
                            // но не чаще 1 раза в 15 секунд
                            val stopTboxAppTimeDiff = System.currentTimeMillis() - stopTboxAppLastTime
                            if (!TboxRepository.tboxAppStoped.value && stopTboxAppTimeDiff > 15000) {
                                sendControlTboxApplication("APP", "STOP")
                                stopTboxAppLastTime = System.currentTimeMillis()
                            } else if (stopTboxAppTimeDiff > 900000) {
                                // Отправка команды, через каждые 15 минут
                                sendControlTboxApplication("APP", "STOP")
                                stopTboxAppLastTime = System.currentTimeMillis()
                            }
                        }
                        if (autoStopTboxMdc.value) {
                            // Проверка работы mdc
                            val tboxMdcCheckTimeDiff = System.currentTimeMillis() - tboxMdcCheckTime
                            if (tboxMdcCheckTimeDiff > 60000) {
                                sendControlTboxApplication("MDC", "VERSION")
                                TboxRepository.updateTboxMdcVersionAnswer(false)
                                tboxMdcCheckTime = System.currentTimeMillis()
                            }
                            // Если команда проверки версии mdc была отправлена больше 10 секунд назад и нет
                            // ответа, то команда остановки mdc считается выполненной
                            if (tboxMdcCheckTimeDiff > 10000 && !TboxRepository.tboxMdcVersionAnswer.value) {
                                TboxRepository.updateTboxMdcStoped(true)
                            } else if (tboxMdcCheckTimeDiff > 10000 && TboxRepository.tboxMdcVersionAnswer.value){
                                TboxRepository.updateTboxMdcStoped(false)
                            }
                            // Отправка команды stop Mdc, если она не была подтверждена,
                            // но не чаще 1 раза в 15 секунд
                            val stopTboxMdcTimeDiff = System.currentTimeMillis() - stopTboxMdcLastTime
                            if (!TboxRepository.tboxMdcStoped.value && stopTboxMdcTimeDiff > 15000) {
                                sendControlTboxApplication("MDC", "STOP")
                                stopTboxMdcLastTime = System.currentTimeMillis()
                            } else if (stopTboxMdcTimeDiff > 900000) {
                                // Отправка команды, через каждые 15 минут
                                sendControlTboxApplication("MDC", "STOP")
                                stopTboxMdcLastTime = System.currentTimeMillis()
                            }
                        }

                        if (autoPreventTboxRestart.value) {
                            // Отправка команд предотвращения перезагрузки, если они не были подтверждены,
                            // но не чаще 1 раза в 15 секунд
                            val preventRestartTimeDiff = System.currentTimeMillis() - preventRestartLastTime
                            if (!TboxRepository.preventRestartSend.value && preventRestartTimeDiff > 15000) {
                                swdPreventRestart()
                                preventRestartLastTime = System.currentTimeMillis()
                            }
                            // Отправка команды, через каждые 15 минут
                            if (preventRestartTimeDiff > 900000) {
                                swdPreventRestart()
                                preventRestartLastTime = System.currentTimeMillis()
                            }
                        }
                    }

                    delay(1000)
                }
            } catch (e: CancellationException) {
                // Нормальная отмена - не логируем
                throw e
            } catch (e: Exception) {
                TboxRepository.addLog("ERROR", "1s Job", "Fatal error in periodic job")
                Log.e("1s Job", "Fatal error in periodic job", e)
            }
        }
    }

    private fun stopPeriodicJob() {
        periodicJob?.cancel()
        periodicJob = null
    }

    private fun modemMode(mode: Int, rst: Boolean = false, timeout: Long = 5000, needCheck: Boolean = true) {
        if (!TboxRepository.tboxConnected.value) {
            return
        }
        if (modemModeJob?.isActive == true) return
        modemModeJob = scope.launch {
            try {
                if (mode != -1) {
                    if (!sendATJob.awaitCompletionWithTimeout(5000)) {
                        return@launch
                    }
                    val modeText = when (mode) {
                        0 -> "OFF"
                        1 -> "ON"
                        4 -> "FLY"
                        else -> "$mode"
                    }
                    TboxRepository.addLog("INFO", "Modem mode",
                        "Set modem mode to $modeText")
                    if (rst) {
                        mdcSendAT("AT+CFUN=$mode,1".toByteArray())
                    } else {
                        mdcSendAT("AT+CFUN=$mode".toByteArray())
                    }
                    TboxRepository.updateModemStatus(mode)
                    if (needCheck) {
                        delay(timeout)
                    }
                }
                if (needCheck) {
                    if (!sendATJob.awaitCompletionWithTimeout(5000)) {
                        return@launch
                    }
                    mdcSendAT("AT+CFUN?".toByteArray())
                }
            } catch (e: CancellationException) {
                // Нормальная отмена - не логируем
                throw e
            } catch (e: Exception) {
                TboxRepository.addLog("ERROR", "Modem mode", "Fatal error in modem mode job")
                Log.e("Modem mode", "Fatal error in modem mode job", e)
            }
        }
    }

    private fun mdcSendAT(cmd: ByteArray) {
        if (!TboxRepository.tboxConnected.value) {
            return
        }
        if (sendATJob?.isActive == true) return
        sendATJob = scope.launch {
            try {
                val cmds = String(cmd, charset = Charsets.UTF_8).trimEnd()
                val cmdEnd = cmd + "\r\n".toByteArray()
                //val cmdEnd = cmd
                TboxRepository.addLog("DEBUG", "MDC send", "AT command send: $cmds")
                TboxRepository.addATLog("Send: $cmds")
                sendTboxMessage(MDC_CODE, SELF_CODE, 0x0E,
                    byteArrayOf(
                        (cmdEnd.size + 10 shr 8).toByte(), (cmdEnd.size + 10 and 0xFF).toByte(),
                        0xFF.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00) + cmdEnd, false)
            } catch (e: CancellationException) {
                // Нормальная отмена - не логируем
                throw e
            } catch (e: Exception) {
                TboxRepository.addLog("ERROR", "AT command", "Fatal error in AT command job")
                Log.e("AT command", "Fatal error in AT command job", e)
            }
        }
    }

    private fun readAllSMS() {
        if (!TboxRepository.tboxConnected.value) {
            return
        }
        if (getSMSJob?.isActive == true) return
        getSMSJob = scope.launch {
            isLastSMS = false
            try {
                if (!sendATJob.awaitCompletionWithTimeout(5000)) {
                    return@launch
                }
                mdcSendAT("AT+CMGF=1".toByteArray())
                delay(2000)
                if (!sendATJob.awaitCompletionWithTimeout(5000)) {
                    return@launch
                }
                mdcSendAT("AT+CSCS=\"UCS2\"".toByteArray())
                delay(1000)

                for (i in 0 until 50) {
                    delay(2000)
                    if (isLastSMS) return@launch

                    if (!sendATJob.awaitCompletionWithTimeout(5000)) {
                        return@launch
                    }
                    mdcSendAT("AT+CMGR=$i".toByteArray())
                }
            } catch (e: CancellationException) {
                // Нормальная отмена - не логируем
                throw e
            } catch (e: Exception) {
                TboxRepository.addLog("ERROR", "AT command", "Fatal error in AT command job")
                Log.e("AT command", "Fatal error in AT command job", e)
            }
        }
    }

    private fun stopReadAllSMS() {
        getSMSJob?.cancel()
        getSMSJob = null
    }

    private fun crtCmd (cmd: Byte, data: ByteArray, description: String, logLevel: String = "DEBUG") {
        if (!TboxRepository.tboxConnected.value) {
            return
        }
        //if (crtCmdJob?.isActive == true) return
        scope.launch {
            try {
                TboxRepository.addLog(logLevel, "CRT send", description)
                sendTboxMessage(CRT_CODE, SELF_CODE, cmd, data, false)
                delay(100)
            } catch (e: CancellationException) {
                // Нормальная отмена - не логируем
                throw e
            } catch (e: Exception) {
                TboxRepository.addLog("ERROR", "CRT command", "Fatal error in CRT command job")
                Log.e("CRT command", "Fatal error in CRT command job", e)
            }
        }
    }

    fun crtRebootTbox() {
        crtCmd(0x2B, byteArrayOf(0x02), "Restart TBox", "INFO")
    }

    private fun getInfo(
        checkAppVersion: Boolean = true,
        checkCrtVersion: Boolean = true,
        checkMdcVersion: Boolean = true,
        checkLocVersion: Boolean = true,
        checkSwdVersion: Boolean = true,
        checkGateVersion: Boolean = true,
        checkSW: Boolean = true,
        checkHW: Boolean = true,
        checkVIN: Boolean = true,
        needClearInfo: Boolean = true
    ) {
        if (versionsJob?.isActive == true) return
        versionsJob = scope.launch {
            try {
                if (needClearInfo) {
                    clearInfo()
                }

                if (checkCrtVersion) {
                    TboxRepository.addLog("DEBUG", "CRT", "Get CRT Version")
                    sendTboxMessage(
                        CRT_CODE, SELF_CODE, 0x01,
                        byteArrayOf(0x00, 0x00), false
                    )
                    delay(150)
                }

                if (checkMdcVersion) {
                    TboxRepository.addLog("DEBUG", "MDC", "Get MDC Version")
                    sendTboxMessage(
                        MDC_CODE, SELF_CODE, 0x01,
                        byteArrayOf(0x00, 0x00), false
                    )
                    delay(150)
                }

                if (checkLocVersion) {
                    TboxRepository.addLog("DEBUG", "LOC", "Get LOC Version")
                    sendTboxMessage(
                        LOC_CODE, SELF_CODE, 0x01,
                        byteArrayOf(0x00, 0x00), false
                    )
                    delay(150)
                }

                if (checkSwdVersion) {
                    TboxRepository.addLog("DEBUG", "SWD", "Get SWD Version")
                    sendTboxMessage(
                        SWD_CODE, SELF_CODE, 0x01,
                        byteArrayOf(0x00, 0x00), false
                    )
                    delay(150)
                }

                if (checkAppVersion) {
                    TboxRepository.addLog("DEBUG", "APP", "Get APP Version")
                    sendTboxMessage(
                        APP_CODE, SELF_CODE, 0x01,
                        byteArrayOf(0x00, 0x00), false
                    )
                    delay(150)
                }

                if (checkGateVersion) {
                    TboxRepository.addLog("DEBUG", "GATE", "Get GATE Version")
                    sendTboxMessage(
                        GATE_CODE, SELF_CODE, 0x01,
                        byteArrayOf(0x00, 0x00), false
                    )
                    delay(150)
                }

                if (checkSW) {
                    TboxRepository.addLog("DEBUG", "TBox", "Get SW Version")
                    sendTboxMessage(
                        CRT_CODE, SELF_CODE, 0x12,
                        byteArrayOf(0x00, 0x00, 0x01, 0x04.toByte()), false
                    ) // CRT - SW
                    delay(150)
                }

                if (checkHW) {
                    TboxRepository.addLog("DEBUG", "TBox", "Get HW Version")
                    sendTboxMessage(
                        CRT_CODE, SELF_CODE, 0x12,
                        byteArrayOf(0x00, 0x00, 0x01, 0x05.toByte()), false
                    ) // CRT - HW
                    delay(150)
                }

                if (checkVIN) {
                    TboxRepository.addLog("DEBUG", "TBox", "Get VIN code")
                    sendTboxMessage(
                        CRT_CODE, SELF_CODE, 0x12,
                        byteArrayOf(0x00, 0x00, 0x01, 0x0F.toByte()), false
                    ) // CRT VIN code
                    delay(150)
                }
            } catch (e: CancellationException) {
                // Нормальная отмена - не логируем
                throw e
            } catch (e: Exception) {
                TboxRepository.addLog("ERROR", "Versions", "Fatal error in Versions job")
                Log.e("Versions", "Fatal error in Versions job", e)
            }
        }
    }

    private suspend fun clearInfo() {
        settingsManager.saveCustomString("app_version", "")
        settingsManager.saveCustomString("crt_version", "")
        settingsManager.saveCustomString("loc_version", "")
        settingsManager.saveCustomString("mdc_version", "")
        settingsManager.saveCustomString("swd_version", "")
        settingsManager.saveCustomString("sw_version", "")
        settingsManager.saveCustomString("hw_version", "")
        settingsManager.saveCustomString("vin_code", "")
    }

    private fun crtGetCanFrame() {
        crtCmd(0x15, byteArrayOf(0x01, 0x02), "Send GetCanFrame command")
    }

    private fun crtGetCycleSignal() {
        crtCmd(0x16, byteArrayOf(0x01, 0x01), "Send GetCycleSignal command")
    }

    private fun crtGetPowVolInfo() {
        crtCmd(0x10, byteArrayOf(0x01, 0x01), "Send GetPowVolInfo command")
    }

    private fun crtGetHdmData() {
        crtCmd(0x14, byteArrayOf(0x03, 0x00), "Send GetHdmData command")
        /*val didList = listOf(0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(),
            0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0x0A.toByte(), 0x0B.toByte(),
            0x09.toByte(), 0x0C.toByte(), 0x0D.toByte(), 0x0E.toByte(), 0x0F.toByte(),
            0x10.toByte(), 0x11.toByte(), 0x12.toByte(), 0x13.toByte(), 0x14.toByte(),
            0x15.toByte(), 0x16.toByte(), 0x17.toByte(), 0x18.toByte(), 0x19.toByte(),
            0x1A.toByte(), 0x1C.toByte(), 0x1B.toByte(), 0x1D.toByte(), 0x1E.toByte(),
            0x1F.toByte(), 0x20.toByte(), 0x21.toByte(), 0x22.toByte(), 0x23.toByte(),
            0x24.toByte(), 0x80.toByte(), 0x81.toByte(), 0x82.toByte(), 0x83.toByte())
        for (i in didList) {
            crtCmd(0x12, byteArrayOf(0x00, 0x00, 0x01, i.toByte()), "Send GetDid command HdmData - byte: 0x${i.toString(16).uppercase()}")
        }*/
    }

    private fun mdcSendAPNManage(cmd: ByteArray) {
        if (!TboxRepository.tboxConnected.value) {
            return
        }
        if (apnCmdJob?.isActive == true) return
        apnCmdJob = scope.launch {
            sendTboxMessage(MDC_CODE, SELF_CODE, 0x10, cmd)
        }
    }

    /*private fun appSuspendTboxApp() {
        if (!TboxRepository.tboxConnected.value) {
            return
        }
        if (appCmdJob?.isActive == true) return
        appCmdJob = scope.launch {
            TboxRepository.addLog("DEBUG", "APP send", "Suspend APP")
            sendUdpMessage(APP_CODE, SELF_CODE, 0x02, byteArrayOf(0x00), false)
        }
    }

    private fun appStopTboxApp() {
        if (!TboxRepository.tboxConnected.value) {
            return
        }
        if (appCmdJob?.isActive == true) return
        appCmdJob = scope.launch {
            TboxRepository.addLog("DEBUG", "APP send", "Stop APP")
            sendUdpMessage(APP_CODE, SELF_CODE, 0x04, byteArrayOf(0x00), false)
        }
    }*/

    private fun swdPreventRestart() {
        if (!TboxRepository.tboxConnected.value) {
            return
        }
        if (swdCmdJob?.isActive == true) return
        swdCmdJob = scope.launch {
            TboxRepository.addLog("DEBUG", "SWD send", "Prevent restart")
            //sendUdpMessage(SWD_CODE, SELF_CODE, 0x07, byteArrayOf(0x00, 0x00, 0x00, 0x01)) //Netstates Prevent Restart
            //sendUdpMessage(SWD_CODE, SELF_CODE, 0x07, byteArrayOf(0x00, 0x00, 0x01, 0x01)) //Monitor Prevent Restart
            sendTboxMessage(SWD_CODE, SELF_CODE, 0x07, byteArrayOf(0x00, 0x00, 0x02, 0x01), false) //Netstates и Monitor Prevent Restart
        }
    }

    private fun locSubscribe(value: Boolean = false) {
        if (!TboxRepository.tboxConnected.value) {
            return
        }
        if (locCmdJob?.isActive == true) return
        locCmdJob = scope.launch {
            if (value) {
                TboxRepository.addLog("DEBUG", "LOC send", "Location subscribe")
                val timeout = (LOCATION_UPDATE_TIME).toByte()
                sendTboxMessage(LOC_CODE, SELF_CODE, 0x05, byteArrayOf(0x02, timeout, 0x00), false)
                //sendUdpMessage(LOC_CODE, SELF_CODE, 0x05, byteArrayOf(0x02, timeout, 0x01), false)
            }
            else {
                TboxRepository.addLog("DEBUG", "LOC send", "Location unsubscribe")
                sendTboxMessage(LOC_CODE, SELF_CODE, 0x05, byteArrayOf(0x00, 0x00, 0x00), false)
            }
            //TboxRepository.updateLocationSubscribed(value)
        }
    }

    private fun sendControlTboxApplication(app: String, cmd: String) {
        val appCode = when (app) {
            "APP" -> APP_CODE
            "MDC" -> MDC_CODE
            "SWD" -> SWD_CODE
            "LOC" -> LOC_CODE
            "CRT" -> CRT_CODE
            "NTM" -> NTM_CODE
            "GATE" -> GATE_CODE
            else -> return
        }
        val cmdCode = when (cmd) {
            "VERSION" -> 0x01.toByte()
            "SUSPEND" -> 0x02.toByte()
            "RESUME" -> 0x03.toByte()
            "STOP" -> 0x04.toByte()
            else -> return
        }
        scope.launch {
            TboxRepository.addLog("DEBUG", "$app send", cmd)
            sendTboxMessage(
                appCode,
                SELF_CODE,
                cmdCode,
                byteArrayOf(0x00, 0x00),
                false
            )
        }
    }

    private fun sendWidgetUpdate() {
        // Проверяем, есть ли активные виджеты
        if (!NetWidget.hasActiveWidgets(this) &&
            !ConWidget.hasActiveWidgets(this) &&
            !ConResWidget.hasActiveWidgets(this)
            ) {
            return
        }

        val intent = Intent(ACTION_UPDATE_WIDGET).apply {
            setPackage(this@BackgroundService.packageName)
            putExtra(EXTRA_SIGNAL_LEVEL, TboxRepository.netState.value.signalLevel)
            putExtra(EXTRA_NET_TYPE, TboxRepository.netState.value.netStatus)
            putExtra(EXTRA_TBOX_STATUS, TboxRepository.tboxConnected.value)
            putExtra(EXTRA_APN_STATUS, (TboxRepository.apnStatus.value))
            putExtra(EXTRA_THEME, TboxRepository.currentTheme.value)
            putExtra(EXTRA_WIDGET_SHOW_INDICATOR, widgetShowIndicator.value)
            putExtra(EXTRA_WIDGET_SHOW_LOC_INDICATOR, widgetShowLocIndicator.value && getLocData.value)
            putExtra(EXTRA_LOC_SET_POSITION, TboxRepository.locValues.value.locateStatus)

            /*val isTruePosition = if (getCanFrame.value) {
                TboxRepository.isLocValuesTrue.value
            } else {
                TboxRepository.locValues.value.locateStatus
            }*/
            putExtra(EXTRA_LOC_TRUE_POSITION, TboxRepository.isLocValuesTrue.value)
        }
        try {
            sendBroadcast(intent)
        } catch (e: Exception) {
            TboxRepository.addLog("ERROR", "Widget", "Failed to send broadcast")
            Log.e("Widget", "Failed to send broadcast", e)
        }
    }

    private fun cancelAllJobs() {
        listOf(
            infraBootstrapJob,
            serviceStartupJob,
            mainJob, periodicJob, apnJob, appCmdJob, crtCmdJob, ssmCmdJob,
            swdCmdJob, locCmdJob, apnCmdJob, sendATJob, humJob,
            modemModeJob, checkConnectionJob, tboxClientReconnectJob, versionsJob, generalStateBroadcastJob,
            settingsListenerJob, dataListenerJob, getSMSJob, openMainActivityJob
        ).forEach { job ->
            job?.cancel()
        }
    }

    private fun scheduleOpenMainActivity(delayMs: Long) {
        openMainActivityJob?.cancel()
        openMainActivityJob = scope.launch(exceptionHandler) {
            if (delayMs > 0) {
                delay(delayMs)
            }
            suspend fun tryBringMainToFront() {
                withContext(Dispatchers.Main) {
                    try {
                        val launchIntent =
                            MainActivityIntentHelper.createBringToFrontIntent(this@BackgroundService)
                        startActivity(launchIntent)
                    } catch (e: Exception) {
                        Log.e("BackgroundService", "Open MainActivity failed", e)
                        TboxRepository.addLog("ERROR", "UI", "Open MainActivity: ${e.message}")
                    }
                }
            }
            tryBringMainToFront()
            delay(OPEN_MAIN_ACTIVITY_VERIFY_DELAY_MS)
            if (!MainActivityForegroundTracker.isMainActivityInForeground.value) {
                tryBringMainToFront()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        broadcastSender.stopListeners()
        broadcastSender.clearSubscribers()

        cancelAllJobs()
        job.cancel()
        disconnectTboxClient()
        packetProcessingDispatcher.close()

        isRunning = false

        try {
            unregisterReceiver(broadcastReceiver)
            Log.d("Background Service", "TboxBroadcastReceiver unregistered")
        } catch (e: Exception) {
            TboxRepository.addLog("ERROR", "Background Service", "Failed to unregister TboxBroadcastReceiver")
            Log.e("Background Service", "Failed to unregister TboxBroadcastReceiver", e)
        }

        try {
            themeObserver?.stopObserving()
            Log.d("Theme Service", "Service destroyed")
        } catch (e: Exception) {
            TboxRepository.addLog("ERROR", "Theme Service", "Error during service destruction")
            Log.e("Theme Service", "Error during service destruction", e)
        }

        if (overlayControllerLazy.isInitialized()) {
            overlayController.onDestroy()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun sendTboxMessage(
                                       tid: Byte,
                                       sid: Byte,
                                       cmd: Byte,
                                       msg: ByteArray,
                                       needLog: Boolean = true): Boolean {
        try {
            val client = tBoxClient ?: run {
                TboxRepository.addLog("ERROR", "TBox Proxy", "Client is not initialized")
                return false
            }

            var data = fillHeader(msg.size, tid, sid, cmd) + msg
            val checkSum = xorSum(data)
            data += checkSum
            sendRawMessageMutex.withLock {
                withTimeout(1000) { // Таймаут на отправку
                    client.sendRawMessage(data)
                }
            }

            if (needLog) {
                TboxRepository.addLog("DEBUG", "Proxy message send", toHexString(data))
            }

            return true
        } catch (e: Exception) {
            TboxRepository.addLog("ERROR", "Proxy message send", "Error send message")
            Log.e("TBox Proxy", "Error sending message through proxy", e)
            return false
        }
    }

    private fun responseWork(receivePacket: DatagramPacket) {
        if (!tripsFromDiskReady.get()) {
            return
        }
        val fromAddress = receivePacket.address.hostAddress
        val fromPort = receivePacket.port
        if (!checkPacket(receivePacket.data)) {
            TboxRepository.addLog("ERROR", "TBox proxy response",
                "Unknown data received from $fromAddress:$fromPort: \" +\n" +
                        toHexString(receivePacket.data))
            return
        }
        val dataLength = extractDataLength(receivePacket.data)
        if (!checkLength(receivePacket.data, dataLength)) {
            TboxRepository.addLog("ERROR", "TBox proxy response",
                "Error data length ${receivePacket.data.size-14} < $dataLength " +
                        "received from $fromAddress:$fromPort: " +
                        toHexString(receivePacket.data))
            return
        }
        val receivedData = extractData(receivePacket.data, dataLength)
        if (receivedData.contentEquals(ByteArray(0))) {
            TboxRepository.addLog(
                "ERROR", "TBox proxy response",
                "Checksum error or no data received from $fromAddress:$fromPort: " +
                        toHexString(receivePacket.data))
            return
        }
        val tid = receivePacket.data[9]
        val tids = String.format("%02X", tid)
        val tidName:String
        val sid = receivePacket.data[8]
        val sids = String.format("%02X", sid)
        val cmd = receivePacket.data[12]
        val cmds = String.format("%02X", cmd)
        var needEndLog = true

        when (tid) {
            MDC_CODE -> {
                tidName = "MDC"
                try {
                    when (cmd) {
                        0x81.toByte() -> {
                            needEndLog = !ansVersion(tidName, receivedData)
                            TboxRepository.updateTboxMdcStoped(false)
                            TboxRepository.updateTboxMdcVersionAnswer(true)
                        }

                        0x82.toByte() -> {
                            needEndLog = !ansAppControl(tidName, cmd, receivedData)
                            TboxRepository.updateTboxMdcSuspended(true)
                        }

                        0x83.toByte() -> {
                            needEndLog = !ansAppControl(tidName, cmd, receivedData)
                        }

                        0x84.toByte() -> {
                            needEndLog = !ansAppControl(tidName, cmd, receivedData)
                            TboxRepository.updateTboxMdcStoped(true)
                        }

                        0x87.toByte() -> {
                            needEndLog = !ansMDCNetState(receivedData)
                        }

                        0x8E.toByte() -> {
                            needEndLog = !ansATcmd(receivedData)
                        }

                        0x90.toByte() -> {
                            needEndLog = !ansMDCAPNManage(receivedData)
                        }

                        0x91.toByte() -> {
                            needEndLog = !ansMDCAPNState(receivedData)
                        }

                        else -> {
                            TboxRepository.addLog(
                                "ERROR", "$tidName response", "Unknown message from $tidName"
                            )
                        }
                    }
                } catch (e: Exception) {
                    TboxRepository.addLog("ERROR", "$tidName response", "$e")
                }
            }
            APP_CODE -> {
                tidName = "APP"
                try {
                    when (cmd) {
                        0x81.toByte() -> {
                            needEndLog = !ansVersion(tidName, receivedData)
                            TboxRepository.updateTboxAppStoped(false)
                            TboxRepository.updateTboxAppVersionAnswer(true)
                        }
                        0x82.toByte() -> {
                            needEndLog = !ansAppControl(tidName, cmd, receivedData)
                            TboxRepository.updateTboxAppSuspended(true)
                        }
                        0x83.toByte() -> {
                            needEndLog = !ansAppControl(tidName, cmd, receivedData)
                        }
                        0x84.toByte() -> {
                            needEndLog = !ansAppControl(tidName, cmd, receivedData)
                            TboxRepository.updateTboxAppStoped(true)
                        }
                        else -> {
                            TboxRepository.addLog("ERROR", "$tidName response", "Unknown message from $tidName")
                        }
                    }
                } catch (e: Exception) {
                    TboxRepository.addLog("ERROR", "$tidName response", "$e")
                }
            }
            SWD_CODE -> {
                tidName = "SWD"
                try {
                    when (cmd) {
                        0x81.toByte() -> {
                            needEndLog = !ansVersion(tidName, receivedData)
                        }
                        0x82.toByte() -> {
                            needEndLog = !ansAppControl(tidName, cmd, receivedData)
                            TboxRepository.updateTboxSwdSuspended(true)
                        }
                        0x83.toByte() -> {
                            needEndLog = !ansAppControl(tidName, cmd, receivedData)
                        }
                        0x84.toByte() -> {
                            needEndLog = !ansAppControl(tidName, cmd, receivedData)
                        }
                        0x87.toByte() -> {
                            if (receivedData.contentEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00))) {
                                TboxRepository.addLog("INFO", "$tidName response", "Command PreventRestart complete")
                                TboxRepository.updatePreventRestartSend(true)
                                needEndLog = false
                            } else {
                                TboxRepository.addLog("ERROR", "$tidName response", "Command PreventRestart not complete")
                            }
                        }
                        else -> {
                            TboxRepository.addLog("ERROR", "$tidName response", "Unknown message from $tidName")
                        }
                    }
                } catch (e: Exception) {
                    TboxRepository.addLog("ERROR", "$tidName response", "$e")
                }
            }
            CRT_CODE -> {
                tidName = "CRT"
                try {
                    when (cmd) {
                        0x81.toByte() -> {
                            needEndLog = !ansVersion(tidName, receivedData)
                        }
                        0x82.toByte() -> {
                            needEndLog = !ansAppControl(tidName, cmd, receivedData)
                        }
                        0x83.toByte() -> {
                            needEndLog = !ansAppControl(tidName, cmd, receivedData)
                        }
                        0x84.toByte() -> {
                            needEndLog = !ansAppControl(tidName, cmd, receivedData)
                        }
                        0x90.toByte() -> {
                            needEndLog = !ansCRTPowVol(receivedData)
                        }
                        0x92.toByte() -> {
                            needEndLog = !ansCRTDidData(receivedData)
                        }
                        0x94.toByte() -> {
                            needEndLog = !ansCRTHdmData(receivedData)
                        }
                        0x95.toByte() -> {
                            needEndLog = !ansCRTCanFrame(receivedData)
                        }
                        0x96.toByte() -> {
                            needEndLog = !ansCRTCycleSignal(receivedData)
                        }

                        else -> {
                            TboxRepository.addLog("ERROR", "$tidName response", "Unknown message from $tidName")
                        }
                    }
                } catch (e: Exception) {
                    TboxRepository.addLog("ERROR", "$tidName response", "$e")
                }
            }
            LOC_CODE -> {
                tidName = "LOC"
                try {
                    when (cmd) {
                        0x81.toByte() -> {
                            needEndLog = !ansVersion(tidName, receivedData)
                        }
                        0x82.toByte() -> {
                            needEndLog = !ansAppControl(tidName, cmd, receivedData)
                        }
                        0x83.toByte() -> {
                            needEndLog = !ansAppControl(tidName, cmd, receivedData)
                        }
                        0x84.toByte() -> {
                            needEndLog = !ansAppControl(tidName, cmd, receivedData)
                        }
                        0x85.toByte() -> {
                            needEndLog = !ansLOCValues(receivedData)
                        }
                        else -> {
                            TboxRepository.addLog(
                                "ERROR", "$tidName response", "Unknown message from $tidName"
                            )
                        }
                    }
                } catch (e: Exception) {
                    TboxRepository.addLog("ERROR", "$tidName response", "$e")
                }
            }
            GATE_CODE -> {
                tidName = "GATE"
                try {
                    when (cmd) {
                        0x81.toByte() -> {
                            needEndLog = !ansVersion(tidName, receivedData, false)
                        }
                        0x82.toByte() -> {
                            needEndLog = !ansAppControl(tidName, cmd, receivedData)
                        }
                        0x83.toByte() -> {
                            needEndLog = !ansAppControl(tidName, cmd, receivedData)
                        }
                        0x84.toByte() -> {
                            needEndLog = !ansAppControl(tidName, cmd, receivedData)
                        }
                        else -> {
                            TboxRepository.addLog("ERROR", "$tidName response", "Unknown message from $tidName")
                        }
                    }
                } catch (e: Exception) {
                    TboxRepository.addLog("ERROR", "$tidName response", "$e")
                }
            }
            else -> {
                tidName = "unknown"
                TboxRepository.addLog("ERROR", "TBox proxy response",
                    "Unknown TID 0x$tids")
            }
        }
        if (needEndLog) {
            TboxRepository.addLog(
                "DEBUG", "TBox proxy response",
                "Received from $fromAddress:$fromPort: TID: 0x$tids ($tidName), " +
                        "SID: 0x$sids, CMD: 0x$cmds - " +
                        toHexString(receivedData)
            )
        }
    }

    private fun ansMDCNetState(data: ByteArray): Boolean {
        TboxRepository.addLog("DEBUG", "MDC response", "Get network state")
        if (data.copyOfRange(0, 4)
                .contentEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        ) {
            TboxRepository.addLog("ERROR", "MDC response", "Error check network state")
            return false
        }
        else if (data.copyOfRange(0, 4)
                .contentEquals(byteArrayOf(0xF4.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        ) {
            TboxRepository.addLog("ERROR", "MDC response", "Error check network state - state not correct")
            return false
        }
        else if (data.copyOfRange(0, 4)
                .contentEquals(byteArrayOf(0xF5.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        ) {
            TboxRepository.addLog("ERROR", "MDC response", "Error check network state - subscribe type not correct")
            return false
        }

        var csq = 99
        var signalLevel = 0
        var regStatus = "-"
        var simStatus = "-"
        var netStatus = "-"
        if (data.size >= 8) {
            regStatus = if (data[4].toInt() == 0) {
                "нет"
            } else if (data[4].toInt() == 1) {
                "домашняя сеть"
            } else if (data[4].toInt() == 2) {
                "поиск сети"
            } else if (data[4].toInt() == 3) {
                "регистрация отклонена"
            } else if (data[4].toInt() == 5) {
                "роуминг"
            } else {
                "${data[4]}"
            }

            csq = if (data[5] == 0x99.toByte()) {
                99
            } else {
                data[5].toInt()
            }
            signalLevel = getSignalLevel(csq)

            simStatus = if (data[6].toInt() == 0) {
                "нет SIM"
            } else if (data[6].toInt() == 1) {
                "SIM готова"
            } else if (data[6].toInt() == 2) {
                "требуется PIN"
            } else if (data[6].toInt() == 3) {
                "ошибка SIM"
            } else {
                "${data[6]}"
            }

            netStatus = if (data[7].toInt() == 0) {
                "-"
            } else if (data[7].toInt() == 2) {
                "2G"
            } else if (data[7].toInt() == 3) {
                "3G"
            } else if (data[7].toInt() == 4) {
                "4G"
            } else if (data[7].toInt() == 7) {
                "нет сети"
            } else {
                "${data[7]}"
            }
        }
        if (
            TboxRepository.netState.value.csq != csq
            || TboxRepository.netState.value.signalLevel != signalLevel
            || TboxRepository.netState.value.netStatus != netStatus
            || TboxRepository.netState.value.regStatus != regStatus
            || TboxRepository.netState.value.simStatus != simStatus
            ) {
            val connectionChangeTime = if (TboxRepository.netState.value.regStatus != regStatus) {
                Date()
            } else {
                TboxRepository.netState.value.connectionChangeTime
            }
            TboxRepository.updateNetState(NetState(
                csq = csq,
                signalLevel = signalLevel,
                netStatus = netStatus,
                regStatus = regStatus,
                simStatus = simStatus,
                connectionChangeTime = connectionChangeTime
                ))
        }

        netUpdateCount = 0
        if (TboxRepository.netState.value.regStatus !in listOf("домашняя сеть", "роуминг")) {
            TboxRepository.updateAPNState(APNState())
            TboxRepository.updateAPN2State(APNState())
            TboxRepository.updateAPNStatus(false)
            apnCheck = false
        }
        else {
            apnCheck = true
        }

        var imei = "-"
        var iccid = "-"
        var imsi = "-"
        var operator = "-"
        if (data.size >= 67) {
            imei = String(data, 52, 15, Charsets.UTF_8)
            iccid = String(data, 8, 20, Charsets.UTF_8)
            imsi = String(data, 29, 15, Charsets.UTF_8)
            operator = CsnOperatorResolver.getOperatorName(
                String(data, 45, 3, Charsets.UTF_8),
                String(data, 49, 2, Charsets.UTF_8)
            )
        }
        if (
            TboxRepository.netValues.value.imei != imei
            || TboxRepository.netValues.value.iccid != iccid
            || TboxRepository.netValues.value.imsi != imsi
            || TboxRepository.netValues.value.operator != operator
            ) {
            TboxRepository.updateNetValues(
                NetValues(
                    imei = imei,
                    iccid = iccid,
                    imsi = imsi,
                    operator = operator
                )
            )
        }
        return true
    }

    private fun getSignalLevel(csq: Int): Int {
        return when (csq) {
            in 1..10 -> 1
            in 11..16 -> 2
            in 17..24 -> 3
            in 25..32 -> 4
            else -> 0
        }
    }

    private fun ansMDCAPNManage(data: ByteArray): Boolean {
        TboxRepository.addLog("DEBUG", "MDC response", "Get APN manage response")
        if (data.copyOfRange(0, 4)
                .contentEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        ) {
            TboxRepository.addLog("ERROR", "MDC APN Manage response", "Error APN manage")
            return false
        }
        else if (data.copyOfRange(0, 4)
                .contentEquals(byteArrayOf(0xF4.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        ) {
            TboxRepository.addLog("ERROR", "MDC APN Manage response", "The operation is not permitted in the current APN state")
            return false
        }
        else if (data.copyOfRange(0, 4)
                .contentEquals(byteArrayOf(0xF5.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        ) {
            TboxRepository.addLog("ERROR", "MDC APN Manage response", "Invalid APN channel number")
            return false
        }
        else if (data.copyOfRange(0, 4)
                .contentEquals(byteArrayOf(0, 0, 0, 0))
        ) {
            TboxRepository.addLog("DEBUG", "MDC APN Manage response", "APN command completed")
        }
        return true
    }

    private fun ansMDCAPNState(data: ByteArray): Boolean {
        TboxRepository.addLog("DEBUG", "MDC response", "Get APN state")
        if (data.copyOfRange(0, 4)
                .contentEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        ) {
            TboxRepository.addLog("ERROR", "MDC APN State response", "APN state check error")
            return false
        }

        var apnStatus: Boolean? = null
        var apnType = "-"
        var apnIP = "-"
        var apnGate = "-"
        var apnDNS1 = "-"
        var apnDNS2 = "-"
        if (data.size >= 103) {
            apnStatus = data[6] == 0x01.toByte()
            apnType = String(data, 7, 32, Charsets.UTF_8)
            apnIP = String(data, 39, 15, Charsets.UTF_8)
            apnGate = String(data, 87, 15, Charsets.UTF_8)
            apnDNS1 = String(data, 55, 15, Charsets.UTF_8)
            apnDNS2 = String(data, 71, 15, Charsets.UTF_8)
        }
        if (data[4] == 0x00.toByte()) {
            if (
                TboxRepository.apnState.value.apnIP != apnIP
                || TboxRepository.apnState.value.apnStatus != apnStatus
                || TboxRepository.apnState.value.apnType != apnType
                || TboxRepository.apnState.value.apnGate != apnGate
                || TboxRepository.apnState.value.apnDNS1 != apnDNS1
                || TboxRepository.apnState.value.apnDNS2 != apnDNS2
            ) {
                TboxRepository.updateAPNState(
                    APNState(
                        apnIP = apnIP,
                        apnStatus = apnStatus,
                        apnType = apnType,
                        apnGate = apnGate,
                        apnDNS1 = apnDNS1,
                        apnDNS2 = apnDNS2,
                        changeTime = Date()
                    )
                )
            }
            apn1UpdateCount = 0
        }
        else if (data[4] == 0x01.toByte()) {
            if (
                TboxRepository.apn2State.value.apnIP != apnIP
                || TboxRepository.apn2State.value.apnStatus != apnStatus
                || TboxRepository.apn2State.value.apnType != apnType
                || TboxRepository.apn2State.value.apnGate != apnGate
                || TboxRepository.apn2State.value.apnDNS1 != apnDNS1
                || TboxRepository.apn2State.value.apnDNS2 != apnDNS2
            ) {
                TboxRepository.updateAPN2State(
                    APNState(
                        apnIP = apnIP,
                        apnStatus = apnStatus,
                        apnType = apnType,
                        apnGate = apnGate,
                        apnDNS1 = apnDNS1,
                        apnDNS2 = apnDNS2,
                        changeTime = Date()
                    )
                )
            }
            apn2UpdateCount = 0
        }
        return true
    }

    private fun ansATcmd(data: ByteArray): Boolean {
        if (data.copyOfRange(0, 4).contentEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        ) {
            TboxRepository.addLog("ERROR", "MDC AT response", "Error sending AT command")
            TboxRepository.addATLog("Receive: ERROR")
            return false
        }
        val ans = try {
            String(data.copyOfRange(10, data.size), charset = Charsets.UTF_8).trimEnd()
        } catch (_: Exception) {
            toHexString(data.copyOfRange(10, data.size))
        }

        TboxRepository.addATLog("Receive: $ans")

        if ("ERROR" in ans) {
            TboxRepository.addLog("ERROR", "MDC AT response", "AT command error: $ans")
        } else {
            TboxRepository.addLog("DEBUG", "MDC AT response", "AT command answer: $ans")
        }

        if ("+CFUN: 0" in ans || ("AT+CFUN=0" in ans && "OK" in ans)) {
            TboxRepository.updateModemStatus(0)
        } else if ("+CFUN: 1" in ans || ("AT+CFUN=1" in ans && "OK" in ans)) {
            TboxRepository.updateModemStatus(1)
        } else if ("+CFUN: 4" in ans || ("AT+CFUN=4" in ans && "OK" in ans)) {
            TboxRepository.updateModemStatus(4)
        } else if ("+CMGR: \"REC" in ans && "OK" in ans) {
            val ansList = ans.split("\n")
            if (ansList.size >= 4) {
                val messageNumber = try {
                    ansList[0].split("=")[1].trim().toInt()
                } catch (_: Exception) {
                    -1
                }
                val infoList = ansList[1].replace("\"", "").split(",")
                val from: String
                val dateTime: String
                if (infoList.size >= 4) {
                    from = try {
                        decodeFlexibleUcs2(infoList[1])
                    } catch (_: Exception) {
                        "n/a"
                    }
                    dateTime = "${infoList[3]} ${infoList[4]}"
                } else {
                    from = "?"
                    dateTime = "?"
                }

                val message = try {
                    decodeFlexibleUcs2(ansList[2])
                } catch (_: Exception){
                    "?"
                }

                TboxRepository.addATLog("SMS $messageNumber from ($from) $dateTime: $message")
            } else {
                TboxRepository.addATLog(toHexString(data.copyOfRange(10, data.size)))
            }
        } else if ("at+cmgr=" in ans.lowercase() && "ERROR" in ans){
            isLastSMS = true
        }

        return "ERROR" !in ans
    }

    fun decodeFlexibleUcs2(hexString: String, littleEndian: Boolean = false): String {
        return try {
            val cleanHex = hexString.replace(Regex("[^0-9a-fA-F]"), "")

            val bytes = cleanHex.chunked(2)
                .mapNotNull { hexPair ->
                    try {
                        if (hexPair.length == 2) hexPair.toInt(16).toByte() else null
                    } catch (_: NumberFormatException) {
                        null
                    }
                }
                .toByteArray()

            if (bytes.isEmpty()) return ""

            String(bytes, if (littleEndian) Charsets.UTF_16LE else Charsets.UTF_16BE)
        } catch (_: Exception) {
            // В случае любой ошибки возвращаем пустую строку
            ""
        }
    }

    private fun ansCRTPowVol(data: ByteArray): Boolean {
        if (!data.copyOfRange(0, 4).contentEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00))) {
            TboxRepository.addLog("ERROR", "CRT response", "Error power voltages info")
            return false
        }
        if (data.size >= 10) {
            val buffer = ByteBuffer.wrap(data.copyOfRange(4, 10)).order(ByteOrder.LITTLE_ENDIAN)

            val rawVoltage1 = buffer.short.toInt() and 0xFFFF
            val voltage1 = rawVoltage1.toFloat() / 1000f

            val rawVoltage2 = buffer.short.toInt() and 0xFFFF
            val voltage2 = rawVoltage2.toFloat() / 1000f

            val rawVoltage3 = buffer.short.toInt() and 0xFFFF
            val voltage3 = rawVoltage3.toFloat() / 1000f

            TboxRepository.updateVoltages(VoltagesState(
                voltage1,
                voltage2,
                voltage3,
                updateTime = Date()
            ))
            TboxRepository.addLog("DEBUG", "CRT response",
                "Get power voltages: $voltage1 V, $voltage2 V, $voltage3 V")
            return true
        }
        return false
    }

    private fun ansCRTCanFrame(data: ByteArray): Boolean {
        if (!data.copyOfRange(0, 4).contentEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00))) {
            TboxRepository.addLog("ERROR", "CRT response", "Error CAN Frame")
            return false
        }
        try {
            CanFramesProcess.process(data, canDataSaveCount.value)
        } catch (e: Exception) {
            TboxRepository.addLog("ERROR", "CRT response",
                "Error get CAN Frame: $e")
            return false
        }
        return true
    }

    private fun ansCRTCycleSignal(data: ByteArray): Boolean {
        if (!data.copyOfRange(0, 4).contentEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00))) {
            TboxRepository.addLog("ERROR", "CRT response", "Error Cycle signal data")
            return false
        }
        try {
            TboxRepository.updateCycleSignalTime()
            val rawValue = data.copyOfRange(4, data.size)
            //TboxRepository.addLog("DEBUG", "CRT response",
            //    "Get Cycle signal: ${toHexString(rawValue)}")
            TboxRepository.addLog("DEBUG", "CRT response",
                "Get Cycle signal: ${rawValue.size} bytes")
            if (rawValue.size >= 346) {
                val offset = if (rawValue.size == 346) {
                    -4
                } else {
                    0
                }
                with(CycleDataRepository) {
                    updateVoltage(rawValue.copyOfRange(1, 3).toFloat("UINT16_LE") / 1000f)
                    updateOdometer(rawValue.copyOfRange(9 + offset, 13 + offset).toFloat("UINT32_LE").toUInt())

                    // Давления
                    listOf(21, 22, 23, 24).forEachIndexed { index, byteIndex ->
                        val wheelPressure = if (rawValue[byteIndex + offset] != 0xFF.toByte()) {
                            rawValue[byteIndex + offset].toUInt().toFloat() / 36f
                        } else {
                            null
                        }
                        when (index) {
                            0 -> updatePressure1(wheelPressure)
                            1 -> updatePressure2(wheelPressure)
                            2 -> updatePressure3(wheelPressure)
                            3 -> updatePressure4(wheelPressure)
                        }
                    }

                    updateCarSpeed(rawValue.copyOfRange(28 + offset, 30 + offset).toFloat("UINT16_LE") / 16f)
                    updateLateralAcceleration(rawValue.copyOfRange(30 + offset, 32 + offset).toFloat("UINT16_LE") / 1000f - 2f)
                    updateLongitudinalAcceleration(rawValue.copyOfRange(32 + offset, 34 + offset).toFloat("UINT16_LE") / 1000f - 2f)
                    updateEngineRPM(rawValue.copyOfRange(36 + offset, 38 + offset).toFloat("UINT16_LE") / 4f)

                    // Скорости колес
                    listOf(103 to 105, 106 to 108, 109 to 111, 112 to 114).forEachIndexed { index, (start, end) ->
                        val wheelSpeed = rawValue.copyOfRange(start + offset, end + offset).toFloat("UINT16_LE")
                        when (index) {
                            0 -> updateSpeed1(wheelSpeed)
                            1 -> updateSpeed2(wheelSpeed)
                            2 -> updateSpeed3(wheelSpeed)
                            3 -> updateSpeed4(wheelSpeed)
                        }
                    }

                    // Температуры колес
                    listOf(240, 242, 249, 252).forEachIndexed { index, byteIndex ->
                        val wheelTemperature = rawValue[byteIndex + offset].toUInt().toFloat()
                        when (index) {
                            0 -> {
                                if (rawValue[239 + offset].toInt() == 1) {
                                    updateTemperature1(wheelTemperature)
                                } else {
                                    updateTemperature1(null)
                                }
                            }
                            1 -> {
                                if (rawValue[243 + offset].toInt() == 1) {
                                    updateTemperature2(wheelTemperature)
                                } else {
                                    updateTemperature2(null)
                                }
                            }
                            2 -> {
                                if (rawValue[250 + offset].toInt() == 1) {
                                    updateTemperature3(wheelTemperature)
                                } else {
                                    updateTemperature3(null)
                                }
                            }
                            3 -> {
                                if (rawValue[253 + offset].toInt() == 1) {
                                    updateTemperature4(wheelTemperature)
                                } else {
                                    updateTemperature4(null)
                                }
                            }
                        }
                    }

                    updateYawRate(rawValue.copyOfRange(135 + offset, 137 + offset).toFloat("UINT16_LE") / 100f - 180f)
                }
            }
        } catch (e: Exception) {
            TboxRepository.addLog("ERROR", "CRT response",
                "Error get Cycle signal: $e")
            return false
        }
        return true
    }

    private fun ansCRTDidData(data: ByteArray): Boolean {
        if (!data.copyOfRange(0, 4).contentEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00))) {
            TboxRepository.addLog("ERROR", "CRT response", "Error DID data")
            return false
        }
        if (data.size >= 7) {
            if (data[4] == 0x01.toByte()) {
                val value = try {
                    String(data.copyOfRange(6, data.size), charset = Charsets.UTF_8).trimEnd()
                } catch (_: Exception) {
                    ""
                }
                TboxRepository.addDidDataCSV(
                    toHexString(byteArrayOf(data[5])),
                    toHexString(data.copyOfRange(6, data.size)),
                    value)

                if (data[5] == 0x04.toByte()) {
                    val sw =
                        String(data.copyOfRange(6, data.size), charset = Charsets.UTF_8).trimEnd()
                    TboxRepository.addLog("DEBUG", "CRT response", "Version SW: $sw")
                    scope.launch {
                        settingsManager.saveCustomString("sw_version", sw)
                    }
                    return true
                } else if (data[5] == 0x05.toByte()) {
                    toHexString(byteArrayOf(data[5]))
                    val hw =
                        String(data.copyOfRange(6, data.size), charset = Charsets.UTF_8).trimEnd()
                    TboxRepository.addLog("DEBUG", "CRT response", "Version HW: $hw")
                    scope.launch {
                        settingsManager.saveCustomString("hw_version", hw)
                    }
                    return true
                } else if (data[5] == 0x0F.toByte()) {
                    toHexString(byteArrayOf(data[5]))
                    val vin =
                        String(data.copyOfRange(6, data.size), charset = Charsets.UTF_8).trimEnd()
                    TboxRepository.addLog("DEBUG", "CRT response", "VIN code: $vin")
                    scope.launch {
                        settingsManager.saveCustomString("vin_code", vin)
                    }
                    return true
                } else if (data[5] == 0x82.toByte()) {
                    return ansLOCValues(data)
                }
            }
        }
        TboxRepository.addLog("DEBUG", "CRT response",
            "Get DID data: ${toHexString(data)}")
        return true
    }

    private fun ansCRTHdmData(data: ByteArray): Boolean {
        if (!data.copyOfRange(0, 4).contentEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00))) {
            TboxRepository.addLog("ERROR", "CRT response", "Error HDM data")
            return false
        }
        if (data.size >= 7) {
            val buffer = ByteBuffer.wrap(data.copyOfRange(4, 7)).order(ByteOrder.LITTLE_ENDIAN)

            val isPower = buffer.get().toInt() and 0xFF != 0
            val isIgnition = buffer.get().toInt() and 0xFF != 0
            val isCan = buffer.get().toInt() and 0xFF != 0

            TboxRepository.updateHdm(HdmData(isPower, isIgnition, isCan))
            TboxRepository.addLog("DEBUG", "CRT response",
                "Get HDM data: $isPower, $isIgnition, $isCan")
        } else {
            TboxRepository.addLog("WARN", "CRT response",
                "No HDM data")
            return false
        }
        return true
    }

    private fun ansVersion(app: String, data: ByteArray, needSaveSettings: Boolean = true): Boolean {
        if (!data.copyOfRange(0, 4).contentEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00))) {
            TboxRepository.addLog("ERROR", "$app response", "Error version info")
            return false
        }
        val version =  String(data.copyOfRange(4, data.size), charset = Charsets.UTF_8).trimEnd()
        if (app == "GATE") {
            TboxRepository.updateGateVersion(version)
        }
        if (needSaveSettings) {
            scope.launch {
                settingsManager.saveCustomString("${app.lowercase()}_version", version)
            }
        }
        TboxRepository.addLog("DEBUG", "$app response", "Version info: $version")
        return true
    }

    private fun ansAppControl(app: String, cmd: Byte, data: ByteArray): Boolean {
        when (cmd) {
            0x82.toByte() -> {
                if (data.contentEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00)) || data.contentEquals(byteArrayOf(0x01))) {
                    TboxRepository.addLog("INFO", "$app response", "Command SUSPEND complete")
                    return true
                } else {
                    TboxRepository.addLog(
                        "ERROR",
                        "$app response",
                        "Command SUSPEND not complete"
                    )
                    return false
                }
            }
            0x83.toByte() -> {
                if (data.contentEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00)) || data.contentEquals(byteArrayOf(0x01))) {
                    TboxRepository.addLog("INFO", "$app response", "Command RESUME complete")
                    return true
                } else {
                    TboxRepository.addLog(
                        "ERROR",
                        "$app response",
                        "Command RESUME not complete"
                    )
                    return false
                }
            }
            0x84.toByte() -> {
                if (data.contentEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00))) {
                    TboxRepository.addLog("INFO", "$app response", "Command STOP complete")
                    return true
                } else {
                    TboxRepository.addLog(
                        "ERROR",
                        "$app response",
                        "Command STOP not complete"
                    )
                    return false
                }
            }
            else -> {
                return false
            }
        }
    }

    private fun ansLOCValues(data: ByteArray): Boolean {
        if (data.copyOfRange(0, 4)
                .contentEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        ) {
            TboxRepository.addLog("ERROR", "LOC response", "Error location")
            return false
        }

        if (data.size == 6) {
            if (data.copyOfRange(0, 4).contentEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00))) {
                TboxRepository.addLog("DEBUG", "LOC response", "Location subscribe command complete")
            }
        } else if (data.size >= 45) {
            try {
                //TboxRepository.updateLocationSubscribed(true)
                val rawValue = toHexString(data.copyOfRange(6, 45))

                val gpsData = data.copyOfRange(6, 45)
                val buffer = ByteBuffer.wrap(gpsData).order(ByteOrder.LITTLE_ENDIAN)

                // 1. Статус позиционирования (1 байт)
                val locateStatus = buffer.get().toInt() and 0xFF != 0

                // 2. Время UTC (8 байт)
                val utcTime = UtcTime(
                    year = buffer.get().toInt() and 0xFF,
                    month = buffer.get().toInt() and 0xFF,
                    day = buffer.get().toInt() and 0xFF,
                    hour = buffer.get().toInt() and 0xFF,
                    minute = buffer.get().toInt() and 0xFF,
                    second = buffer.get().toInt() and 0xFF
                )

                // Пропускаем 1 байт (выравнивание или reserved)
                val longitudeDirection = buffer.get().toInt() and 0xFF

                // 3. Долгота (4 байта, int32)
                val rawLongitude = buffer.int
                val longitude = rawLongitude.toDouble() / 1000000.0 * if (longitudeDirection == 1) -1 else 1

                // Пропускаем 1 байт (выравнивание или reserved)
                val latitudeDirection = buffer.get().toInt() and 0xFF

                // 4. Широта (4 байта, int32)
                val rawLatitude = buffer.int
                val latitude = rawLatitude.toDouble() / 1000000.0 * if (latitudeDirection == 1) -1 else 1

                // 5. Высота (4 байта, int32)
                val rawAltitude = buffer.int
                val altitude = rawAltitude.toDouble() / 1000000.0

                // 6. Видимые спутники (1 байт)
                val visibleSatellites = buffer.get().toInt() and 0xFF

                // 7. Используемые спутники (1 байт)
                val usingSatellites = buffer.get().toInt() and 0xFF

                // 8. Скорость (2 байта, uint16)
                val rawSpeed = buffer.short.toInt() and 0xFFFF
                val speed = rawSpeed.toFloat() / 10f

                // 9. Истинное направление (2 байта, uint16)
                val rawTrueDirection = buffer.short.toInt() and 0xFFFF
                val trueDirection = rawTrueDirection.toFloat() / 10f

                // 10. Магнитное направление (2 байта, uint16)
                val rawMagneticDirection = buffer.short.toInt() and 0xFFFF
                val magneticDirection = rawMagneticDirection.toFloat() / 10f

                val locValues = LocValues(
                    rawValue = rawValue,
                    locateStatus = locateStatus,
                    utcTime = utcTime,
                    longitude = longitude,
                    latitude = latitude,
                    altitude = altitude,
                    visibleSatellites = visibleSatellites,
                    usingSatellites = usingSatellites,
                    speed = speed,
                    trueDirection = trueDirection,
                    magneticDirection = magneticDirection,
                    updateTime = Date()
                )

                TboxRepository.updateLocationUpdateTime()

                if (rawValue != TboxRepository.locValues.value.rawValue) {
                    TboxRepository.updateLocValues(
                        locValues
                    )
                }

                if ((longitude == 0.0 && latitude == 0.0 && altitude == 0.0) || !locateStatus) {
                    TboxRepository.updateIsLocValuesTrue(false)
                }

                if (mockLocation.value) {
                    locationMockManager.setMockLocation(locValues)
                }

                TboxRepository.addLog(
                    "DEBUG", "LOC response",
                    "Get location values: $longitude, $latitude"
                )
            } catch (e: Exception) {
                TboxRepository.addLog("ERROR", "LOC response", "Error parsing location data: ${e.message}")
                return false
            }
        } else {
            TboxRepository.addLog("DEBUG", "LOC response",
                "Get unknown location values")
            return false
        }
        return true
    }

    private fun onTboxConnected(value: Boolean = false) {
        if (value) {
            packetSilenceChecks = 0
            TboxRepository.addLog("INFO", "TBox connection", "TBox connected")
            TboxRepository.updateTboxConnected(true)

            modemMode(-1)

            if (autoSuspendTboxSwd.value) {
                sendControlTboxApplication("SWD", "SUSPEND")
                suspendTboxSwdLastTime = System.currentTimeMillis()
            }
            if (autoSuspendTboxMdc.value) {
                sendControlTboxApplication("MDC", "SUSPEND")
                suspendTboxMdcLastTime = System.currentTimeMillis()
            }
            if (autoSuspendTboxApp.value) {
                sendControlTboxApplication("APP", "SUSPEND")
                suspendTboxAppLastTime = System.currentTimeMillis()
            }

            if (autoStopTboxApp.value) {
                if (autoSuspendTboxSwd.value) {
                    scope.launch {
                        delay(5000)
                        sendControlTboxApplication("APP", "STOP")
                        stopTboxAppLastTime = System.currentTimeMillis()
                    }
                } else {
                    sendControlTboxApplication("APP", "STOP")
                    stopTboxAppLastTime = System.currentTimeMillis()
                }
            }
            if (autoStopTboxMdc.value) {
                if (autoSuspendTboxSwd.value) {
                    scope.launch {
                        delay(5000)
                        sendControlTboxApplication("MDC", "STOP")
                        stopTboxMdcLastTime = System.currentTimeMillis()
                    }
                } else {
                    sendControlTboxApplication("MDC", "STOP")
                    stopTboxMdcLastTime = System.currentTimeMillis()
                }
            }

            if (autoPreventTboxRestart.value) {
                swdPreventRestart()
                preventRestartLastTime = System.currentTimeMillis()
            }
            /*if (updateVoltages.value) {
                crtGetPowVolInfo()
            }*/
            if (getCanFrame.value) {
                crtGetCanFrame()
            }
            /*if (getCycleSignal.value) {
                crtGetCycleSignal()
            }*/
            if (getLocData.value) {
                locSubscribe(true)
            }
            //crtGetHdmData()
            val notification = createNotification("TBox connected")
            startForeground(NOTIFICATION_ID, notification)
        }
        else {
            packetSilenceChecks = 0
            TboxRepository.addLog("WARN", "TBox connection", "TBox disconnected")
            TboxRepository.resetConnectionData()
            val notification = createNotification("TBox disconnected")
            startForeground(NOTIFICATION_ID, notification)
        }
        TboxRepository.updateTboxConnectionTime()
        sendWidgetUpdate()
    }

    private suspend fun Job?.awaitCompletionWithTimeout(timeoutMillis: Long = 5000): Boolean {
        if (this == null || !isActive) return true

        return try {
            withTimeout(timeoutMillis) {
                join()
            }
            true
        } catch (_: TimeoutCancellationException) {
            false
        }
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("BackgroundService", "Coroutine error", throwable)
        TboxRepository.addLog("ERROR", "Coroutine", "Error: ${throwable.message}")
    }

    /**
     * After boot-time service start: once the data listener is running, optionally bring
     * [MainActivity] to the foreground on the home main screen (tab 100) after a short delay.
     */
    private suspend fun maybeOpenMainScreenAfterBootSuspend() {
        try {
            val enabled = settingsManager.mainScreenOpenOnBootFlow.first()
            if (!enabled) return
            settingsManager.saveSelectedTab(SettingsManager.MAIN_SCREEN_SELECTED_TAB_INDEX)
            scheduleOpenMainActivity(2000L)
        } catch (e: Exception) {
            Log.e("BackgroundService", "Open main screen after boot failed", e)
            TboxRepository.addLog("ERROR", "Boot UI", "Open main screen: ${e.message}")
        }
    }
}
