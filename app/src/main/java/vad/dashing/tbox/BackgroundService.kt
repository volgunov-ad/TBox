package vad.dashing.tbox

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import vad.dashing.tbox.location.LocationMockManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vad.dashing.tbox.ui.FloatingDashboardUI
import vad.dashing.tbox.ui.MyLifecycleOwner
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Date
import kotlin.let

class BackgroundService : Service() {
    private lateinit var settingsManager: SettingsManager
    private lateinit var ipManager: IPManager
    private lateinit var locationMockManager: LocationMockManager
    private lateinit var scope: CoroutineScope
    private val job = SupervisorJob()
    private lateinit var autoModemRestart: StateFlow<Boolean>
    private lateinit var autoTboxReboot: StateFlow<Boolean>
    private lateinit var autoSuspendTboxApp: StateFlow<Boolean>
    private lateinit var autoStopTboxApp: StateFlow<Boolean>
    private lateinit var autoPreventTboxRestart: StateFlow<Boolean>
    private lateinit var getVoltages: StateFlow<Boolean>
    private lateinit var getCanFrame: StateFlow<Boolean>
    private lateinit var getCycleSignal: StateFlow<Boolean>
    private lateinit var getLocData: StateFlow<Boolean>
    private lateinit var serverIp: StateFlow<String>
    private lateinit var tboxIPRotation: StateFlow<Boolean>
    private lateinit var widgetShowIndicator: StateFlow<Boolean>
    private lateinit var widgetShowLocIndicator: StateFlow<Boolean>
    private lateinit var mockLocation: StateFlow<Boolean>
    private lateinit var isFloatingDashboard: StateFlow<Boolean>
    private lateinit var floatingDashboardHeight: StateFlow<Int>
    private lateinit var floatingDashboardWidth: StateFlow<Int>
    private lateinit var floatingDashboardStartX: StateFlow<Int>
    private lateinit var floatingDashboardStartY: StateFlow<Int>

    private val serverPort = 50047
    private var currentIP: String? = null
    private var address: InetAddress? = null
    private lateinit var themeObserver: ThemeObserver
    private val socket = DatagramSocket().apply {soTimeout = 1000}
    private val mutex = Mutex()

    private var mainJob: Job? = null
    private var periodicJob: Job? = null
    private var apnJob: Job? = null
    private var appCmdJob: Job? = null
    private var crtCmdJob: Job? = null
    private var ssmCmdJob: Job? = null
    private var swdCmdJob: Job? = null
    private var locCmdJob: Job? = null
    private var listenJob: Job? = null
    private var apnCmdJob: Job? = null
    private var humJob: Job? = null
    private var sendATJob: Job? = null
    private var modemModeJob: Job? = null
    private var checkConnectionJob: Job? = null
    private var versionsJob: Job? = null
    private var generalStateBroadcastJob: Job? = null
    private var settingsListenerJob: Job? = null

    private var netUpdateTime: Long = 5000
    private var apnUpdateTime: Long = 10000
    private var netUpdateCount: Int = 0
    private var apn1UpdateCount: Int = 0
    private var apn2UpdateCount: Int = 0
    private var apnCheck: Boolean = false
    private var stopTboxAppLastTime = System.currentTimeMillis()
    private var suspendTboxAppLastTime = System.currentTimeMillis()
    private var preventRestartLastTime = System.currentTimeMillis()

    private val broadcastReceiver = TboxBroadcastReceiver()
    lateinit var broadcastSender: TboxBroadcastSender

    private lateinit var windowManager: WindowManager
    private var params: WindowManager.LayoutParams? = null
    private var composeView: ComposeView? = null
    private val lifecycleOwner by lazy { MyLifecycleOwner() }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayRetryCount = 0

    companion object {
        private const val MAX_OVERLAY_RETRIES = 3

        private const val APP_CODE = 0x2F.toByte()
        private const val MDC_CODE = 0x25.toByte()
        private const val LOC_CODE = 0x29.toByte()
        private const val CRT_CODE = 0x23.toByte()
        private const val SWD_CODE = 0x2D.toByte()
        private const val NTM_CODE = 0x24.toByte()
        private const val HUM_CODE = 0x30.toByte()
        private const val UDA_CODE = 0x38.toByte()
        private const val SELF_CODE = 0x50.toByte()
        private const val DEFAULT_TBOX_IP = "192.168.225.1"
        private var isRunning = false
        const val LOCATION_UPDATE_TIME = 1
        const val NOTIFICATION_ID = 50047
        const val CHANNEL_ID = "tbox_background_channel"

        private val GEAR_BOX_7_DRIVE_MODES = setOf(0x1B, 0x2B, 0x3B, 0x4B, 0x5B, 0x6B, 0x7B)
            .map { it.toByte() }
            .toSet()

        private val GEAR_BOX_7_PREPARED_DRIVE_MODES = setOf(0x10, 0x20, 0x30, 0x40, 0x50, 0x60, 0x70)
            .map { it.toByte() }
            .toSet()

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

        const val ACTION_START = "vad.dashing.tbox.START"
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
        const val ACTION_LOC_SUSPEND = "vad.dashing.tbox.LOC_SUSPEND"
        const val ACTION_LOC_RESUME = "vad.dashing.tbox.LOC_RESUME"
        const val ACTION_LOC_STOP = "vad.dashing.tbox.LOC_STOP"
        const val ACTION_LOC_SUBSCRIBE = "vad.dashing.tbox.LOC_SUBSCRIBE"
        const val ACTION_LOC_UNSUBSCRIBE = "vad.dashing.tbox.LOC_UNSUBSCRIBE"
        const val ACTION_GET_CAN_FRAME = "vad.dashing.tbox.GET_CAN_FRAME"
        const val ACTION_GET_INFO = "vad.dashing.tbox.GET_INFO"
        const val ACTION_LIGHT_SHOW_START = "vad.dashing.tbox.LIGHT_SHOW_START"
        const val ACTION_LIGHT_SHOW_STOP = "vad.dashing.tbox.LIGHT_SHOW_STOP"

        const val ACTION_SHOW_FLOATING = "vad.dashing.tbox.ACTION_SHOW_FLOATING"
        const val ACTION_HIDE_FLOATING = "vad.dashing.tbox.ACTION_HIDE_FLOATING"
    }

    override fun onCreate() {
        super.onCreate()

        settingsManager = SettingsManager(this)
        locationMockManager = LocationMockManager(this)
        scope = CoroutineScope(Dispatchers.Default + job + exceptionHandler)

        windowManager = getSystemService(WindowManager::class.java)

        autoModemRestart = settingsManager.autoModemRestartFlow
            .stateIn(scope, SharingStarted.Eagerly, false)
        autoTboxReboot = settingsManager.autoTboxRebootFlow
            .stateIn(scope, SharingStarted.Eagerly, false)
        autoSuspendTboxApp = settingsManager.autoSuspendTboxAppFlow
            .stateIn(scope, SharingStarted.Eagerly, false)
        autoStopTboxApp = settingsManager.autoStopTboxAppFlow
            .stateIn(scope, SharingStarted.Eagerly, false)
        autoPreventTboxRestart = settingsManager.autoPreventTboxRestartFlow
            .stateIn(scope, SharingStarted.Eagerly, false)
        getVoltages = settingsManager.getVoltagesFlow
            .stateIn(scope, SharingStarted.Eagerly, false)
        getCanFrame = settingsManager.getCanFrameFlow
            .stateIn(scope, SharingStarted.Eagerly, false)
        getCycleSignal = settingsManager.getCycleSignalFlow
            .stateIn(scope, SharingStarted.Eagerly, false)
        getLocData = settingsManager.getLocDataFlow
            .stateIn(scope, SharingStarted.Eagerly, false)
        serverIp = settingsManager.tboxIPFlow
            .stateIn(scope, SharingStarted.Eagerly, DEFAULT_TBOX_IP)
        tboxIPRotation = settingsManager.tboxIPRotationFlow
            .stateIn(scope, SharingStarted.Eagerly, false)
        widgetShowIndicator = settingsManager.widgetShowIndicatorFlow
            .stateIn(scope, SharingStarted.Eagerly, false)
        widgetShowLocIndicator = settingsManager.widgetShowLocIndicatorFlow
            .stateIn(scope, SharingStarted.Eagerly, false)
        mockLocation = settingsManager.mockLocationFlow
            .stateIn(scope, SharingStarted.Eagerly, false)
        isFloatingDashboard = settingsManager.floatingDashboardFlow
            .stateIn(scope, SharingStarted.Eagerly, false)
        floatingDashboardHeight = settingsManager.floatingDashboardHeightFlow
            .stateIn(scope, SharingStarted.Eagerly, 100)
        floatingDashboardWidth = settingsManager.floatingDashboardWidthFlow
            .stateIn(scope, SharingStarted.Eagerly, 100)
        floatingDashboardStartX = settingsManager.floatingDashboardStartXFlow
            .stateIn(scope, SharingStarted.Eagerly, 50)
        floatingDashboardStartY = settingsManager.floatingDashboardStartYFlow
            .stateIn(scope, SharingStarted.Eagerly, 50)

        ipManager = IPManager(this)
        ipManager.updateIPs(serverIp.value)
        TboxRepository.updateIPList(ipManager.getIPList())
        currentIP = if (tboxIPRotation.value) {
            ipManager.getNextIP()
        } else {
            DEFAULT_TBOX_IP
        }
        TboxRepository.addLog("DEBUG", "IP manager", "Set TBox current IP: $currentIP")

        broadcastSender = TboxBroadcastSender(this, scope)

        val filter = IntentFilter().apply {
            addAction(TboxBroadcastReceiver.MAIN_ACTION)
            addAction(TboxBroadcastReceiver.GET_STATE)
            addAction(TboxBroadcastReceiver.SUBSCRIBE)
            addAction(TboxBroadcastReceiver.UNSUBSCRIBE)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // API 33+
                registerReceiver(broadcastReceiver, filter, RECEIVER_EXPORTED)
            } else {
                // API < 33
                registerReceiver(broadcastReceiver, filter)
            }

            Log.d("BackgroundService", "TboxBroadcastReceiver registered")
        } catch (e: Exception) {
            Log.e("BackgroundService", "Failed to register TboxBroadcastReceiver", e)
        }

        try {
            setupThemeObserver()
            Log.d("ThemeService", "Service created successfully")
        } catch (e: Exception) {
            Log.e("ThemeService", "Failed to create service", e)
        }
        createNotificationChannel()
    }

    private fun setupThemeObserver() {
        try {
            themeObserver = ThemeObserver(this) { themeMode ->
                handleThemeChange(themeMode)
            }
            themeObserver.startObserving()
        } catch (e: Exception) {
            Log.e("ThemeService", "Failed to setup theme observer", e)
            // Используем тему по умолчанию
            handleThemeChange(1)
        }
    }

    private fun handleThemeChange(themeMode: Int) {
        try {
            TboxRepository.updateCurrentTheme(themeMode)
        } catch (e: Exception) {
            Log.e("ThemeService", "Error handling theme change", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        when (intent?.action) {
            ACTION_START -> {
                if (!isRunning) {
                    isRunning = true
                    TboxRepository.addLog("INFO", "Service", "Start service")
                    startNetUpdater()
                    startAPNUpdater()
                    startListener()
                    startCheckConnection()
                    startPeriodicJob()
                    startSettingsListener()
                    TboxRepository.updateServiceStartTime()
                    val notification = createNotification("Start service")
                    startForeground(NOTIFICATION_ID, notification)
                    mainHandler.postDelayed({
                        if (isFloatingDashboard.value) {
                            TboxRepository.addLog("DEBUG", "Floating Dashboard",
                                "Delayed overlay start (5000ms)")
                            openOverlay()
                        }
                    }, 5000)
                }
            }
            ACTION_STOP -> {
                if (isRunning) {
                    isRunning = false
                    TboxRepository.addLog("INFO", "Service", "Stop service")
                    stopNetUpdater()
                    stopAPNUpdater()
                    stopListener()
                    stopCheckConnection()
                    stopPeriodicJob()
                    stopSettingsListener()
                    stopStateBroadcastListener()
                    val notification = createNotification("Stop service")
                    startForeground(NOTIFICATION_ID, notification)
                    closeOverlay()
                }
            }
            ACTION_SEND_AT -> {
                val atCmd = intent.getStringExtra(EXTRA_AT_CMD) ?: "ATI"
                mdcSendAT((atCmd+ "\r\n").toByteArray())
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
                mdcSendAT("AT+CPIN=\"$pin\"\r\n".toByteArray())
            }
            ACTION_PUK -> {
                val pin = intent.getStringExtra(EXTRA_PIN) ?: ""
                val puk = intent.getStringExtra(EXTRA_PUK) ?: ""
                mdcSendAT("AT+CPIN=\"$puk\",\"$pin\"\r\n".toByteArray())
            }
            ACTION_LOC_SUSPEND -> sendSuspendTboxApplication("LOC")
            ACTION_LOC_RESUME -> sendResumeTboxApplication("LOC")
            ACTION_LOC_STOP -> sendStopTboxApplication("LOC")
            ACTION_LOC_SUBSCRIBE -> locSubscribe(true)
            ACTION_LOC_UNSUBSCRIBE -> locSubscribe(false)
            ACTION_GET_CAN_FRAME -> crtGetCanFrame()
            ACTION_GET_INFO -> getInfo()
            ACTION_CLOSE -> crtCmd(0x26,
                ByteArray(45).apply {
                    this[0] = 0x02 },
                "Close", "INFO")
            ACTION_OPEN -> crtCmd(0x26,
                ByteArray(45).apply {
                    this[0] = 0x02
                    this[9] = 0x01 },
                "Open", "INFO")
            ACTION_LIGHT_SHOW_START -> humLightShowReq(byteArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0))
            ACTION_LIGHT_SHOW_STOP -> humLightShowReq(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0))
            ACTION_SHOW_FLOATING -> openOverlay()
            ACTION_HIDE_FLOATING -> closeOverlay()
        }
        return START_STICKY
    }

    private fun openOverlay() {
        // Проверяем, не открыто ли уже окно
        if (composeView != null) {
            TboxRepository.addLog("DEBUG", "Floating Dashboard", "Already shown")
            overlayRetryCount = 0
            return
        }

        // Проверяем максимальное количество попыток
        if (overlayRetryCount >= MAX_OVERLAY_RETRIES) {
            TboxRepository.addLog("ERROR", "Floating Dashboard",
                "Max retries reached ($MAX_OVERLAY_RETRIES). Stopping attempts.")
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            TboxRepository.addLog("ERROR", "Floating Dashboard", "Cannot draw overlays")
            return
        }

        params = WindowManager.LayoutParams(
            floatingDashboardWidth.value,
            floatingDashboardHeight.value,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = floatingDashboardStartX.value
            y = floatingDashboardStartY.value
        }

        val newComposeView = ComposeView(this)

        try {
            // Создаем новый ComposeView каждый раз
            newComposeView.apply {
                // Устанавливаем наш MyLifecycleOwner
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)

                setContent {
                    FloatingDashboardUI(
                        settingsManager = settingsManager,
                        service = this@BackgroundService,
                        params = params!!
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("FloatingDashboard", "Error creating view", e)
            TboxRepository.addLog("ERROR", "Floating Dashboard", "Failed to create: ${e.message}")
            composeView = null
            overlayRetryCount++
            scheduleOverlayCheck()
            return
        }

        try {
            windowManager.addView(newComposeView, params)
            composeView = newComposeView

            if (!lifecycleOwner.isInitialized || lifecycleOwner.lifecycle.currentState.isAtLeast(
                    Lifecycle.State.DESTROYED
                )
            ) {
                lifecycleOwner.setCurrentState(Lifecycle.State.STARTED)
            }

            TboxRepository.addLog("INFO", "Floating Dashboard", "Shown")
        } catch (e: Exception) {
            Log.e("FloatingDashboard", "Error adding view", e)
            TboxRepository.addLog("ERROR", "Floating Dashboard", "Failed to show: ${e.message}")
            composeView = null
        }

        if (composeView == null) {
            overlayRetryCount++
            scheduleOverlayCheck()
        } else {
            overlayRetryCount = 0 // Сброс при успехе
        }
    }

    private fun closeOverlay() {
        composeView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e("FloatingDashboard", "Error removing view", e)
            }
            composeView = null
        }

        TboxRepository.addLog("INFO", "Floating Dashboard", "Closed")
    }

    private fun scheduleOverlayCheck() {
        if (!isFloatingDashboard.value || composeView != null || overlayRetryCount >= MAX_OVERLAY_RETRIES) {
            return
        }

        mainHandler.postDelayed({
            Log.d("OverlayCheck", "Retry attempt ${overlayRetryCount + 1}/$MAX_OVERLAY_RETRIES")
            openOverlay()
        }, 5000)
    }

    private fun stopOverlayCheck() {
        mainHandler.removeCallbacksAndMessages(null)
        overlayRetryCount = 0
    }

    fun updateWindowPosition(x: Int, y: Int) {
        params?.let { currentParams ->
            currentParams.x = x
            currentParams.y = y
            composeView?.let { view ->
                windowManager.updateViewLayout(view, currentParams)
            }
        }
    }

    fun updateWindowSize(width: Int, height: Int) {
        params?.let { currentParams ->
            currentParams.width = width.coerceAtLeast(50)
            currentParams.height = height.coerceAtLeast(50)
            composeView?.let { view ->
                windowManager.updateViewLayout(view, currentParams)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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

    private fun getCurrentAddress(): InetAddress? {
        return try {
            InetAddress.getByName(currentIP)
        } catch (e: Exception) {
            TboxRepository.addLog("ERROR", "Tbox IP address",
                "Failed to get address for IP: $currentIP")
            null
        }
    }

    private fun startNetUpdater() {
        if (mainJob?.isActive == true) return
        mainJob = scope.launch {
            try {
                Log.d("Net Updater", "Start updating network state")
                while (isActive) {
                    TboxRepository.addLog("DEBUG", "MDC send", "Update network")
                    sendUdpMessage(
                        socket,
                        serverPort,
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
                Log.e("Net Updater", "Fatal error in Net updater", e)
            }
        }
    }

    private fun stopNetUpdater() {
        mainJob?.cancel()
        mainJob = null
    }

    private fun startListener() {
        if (listenJob?.isActive == true) return
        listenJob = scope.launch {
            try {
                var errCount = 0
                val receiveData = ByteArray(4096)
                val receivePacket = DatagramPacket(receiveData, receiveData.size)
                Log.d("UDP Listener", "Start UDP listener")
                TboxRepository.updateTboxConnectionTime()
                while (isActive) {
                    try {
                        socket.receive(receivePacket)
                        responseWork(receivePacket)
                        errCount = 0
                        if (!TboxRepository.tboxConnected.value) {
                            onTboxConnected(true)
                        }
                    } catch (e: Exception) {
                        if (TboxRepository.tboxConnected.value) {
                            errCount += 1
                            if (errCount > netUpdateTime * 2 / 1000) {
                                onTboxConnected(false)
                            }
                        }
                    }
                    delay(100)
                }
            } catch (e: CancellationException) {
                // Нормальная отмена - не логируем
                throw e
            } catch (e: Exception) {
                Log.e("UDP Listener", "Fatal error in listener", e)
            }
        }
    }

    private fun stopListener() {
        listenJob?.cancel()
        listenJob = null
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
                        sendUdpMessage(
                            socket,
                            serverPort,
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
                        sendUdpMessage(
                            socket,
                            serverPort,
                            MDC_CODE,
                            SELF_CODE,
                            0x11,
                            byteArrayOf(0x00, 0x00, 0x01, 0x00), false
                        )
                        apn2UpdateCount += 1
                        if (apn2UpdateCount > 2) {
                            TboxRepository.updateAPN2State(APNState())
                        }

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
            } catch (e: CancellationException) {
                // Нормальная отмена - не логируем
                throw e
            } catch (e: Exception) {
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
                var modemCheckTimeout = 10000L
                Log.d("Connection checker", "Start check connection")
                delay(10000)
                while (isActive) {
                    delay(modemCheckTimeout)
                    if (!TboxRepository.tboxConnected.value) {
                        modemCheckTimeout = 10000
                        continue
                    }
                    if (checkConnection()) {
                        modemCheckTimeout = 10000
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
                                modemCheckTimeout = 10000
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

    /*@OptIn(FlowPreview::class)
    private fun startStateBroadcastListener() {
        generalStateBroadcastJob = scope.launch {
            combine(
                TboxRepository.netState,
                TboxRepository.apnState,
                TboxRepository.apn2State,
                TboxRepository.tboxConnected
            ) {}
                .debounce(100) // Задержка 100ms чтобы избежать спама
                .collect {
                    sendBroadcastData(BroadcastActions.ACTION_GENERAL_STATE)
                }
        }
    }*/

    private fun stopStateBroadcastListener() {
        generalStateBroadcastJob?.cancel()
        generalStateBroadcastJob = null
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
                mockLocation.collect { isMockLocation ->
                    if (!isMockLocation) {
                        locationMockManager.stopMockLocation()
                    }
                }
            }

            launch {
                isFloatingDashboard.collect { showFloating ->
                    if (!showFloating) {
                        stopOverlayCheck()
                    }
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
                sendWidgetUpdate()
                var widgetUpdateTime = System.currentTimeMillis()
                //var crtGetPowVolInfoTime = System.currentTimeMillis()
                var crtGetCanFrameTime = System.currentTimeMillis()
                var crtGetCycleSignalTime = System.currentTimeMillis()
                var crtGetLocDataTime = System.currentTimeMillis()
                var updateIPTime = System.currentTimeMillis()
                var locErrorCount = 0
                var tboxAppCheckTime = System.currentTimeMillis()
                delay(15000)
                while (isActive) {
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

                    if(System.currentTimeMillis() - widgetUpdateTime > 5000) {
                        sendWidgetUpdate()
                        widgetUpdateTime = System.currentTimeMillis()
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
                            if (TboxRepository.tboxConnected.value && System.currentTimeMillis() - crtGetCanFrameTime > 10000) {
                                crtGetCanFrame()
                                crtGetCanFrameTime = System.currentTimeMillis()
                            }
                        }
                    }
                    if (getCycleSignal.value) {
                        val delta = currentTime - (TboxRepository.cycleSignalTime.value?.time ?: 0)
                        if (delta > 60000) {
                            if (TboxRepository.tboxConnected.value && System.currentTimeMillis() - crtGetCycleSignalTime > 10000) {
                                //crtGetCycleSignal()
                                crtGetCycleSignalTime = System.currentTimeMillis()
                            }
                        }
                    }
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
                                TboxRepository.carSpeed.value?.let { speed ->
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
                    if (TboxRepository.tboxConnected.value) {
                        if (autoSuspendTboxApp.value) {
                            // Отправка команды suspend app, если она не была подтверждена,
                            // но не чаще 1 раза в 15 секунд
                            val suspendTboxAppTimeDiff = System.currentTimeMillis() - suspendTboxAppLastTime
                            if (!TboxRepository.suspendTboxAppSend.value && suspendTboxAppTimeDiff > 15000) {
                                sendSuspendTboxApplication("APP")
                                suspendTboxAppLastTime = System.currentTimeMillis()
                            }
                            // Отправка команды, через каждые 15 минут
                            if (suspendTboxAppTimeDiff > 900000) {
                                sendSuspendTboxApplication("APP")
                                suspendTboxAppLastTime = System.currentTimeMillis()
                            }
                        }
                        if (autoStopTboxApp.value) {
                            // Проверка работы app
                            val tboxAppCheckTimeDiff = System.currentTimeMillis() - tboxAppCheckTime
                            if (tboxAppCheckTimeDiff > 60000) {
                                getInfo(
                                    checkAppVersion = true,
                                    checkMdcVersion = false,
                                    checkLocVersion = false,
                                    checkCrtVersion = false,
                                    checkSwdVersion = false,
                                    checkHW = false,
                                    checkSW = false,
                                    checkVIN = false,
                                    needClearInfo = false
                                )
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
                                sendStopTboxApplication("APP")
                                stopTboxAppLastTime = System.currentTimeMillis()
                            }
                            // Отправка команды, через каждые 15 минут
                            if (stopTboxAppTimeDiff > 900000) {
                                sendStopTboxApplication("APP")
                                stopTboxAppLastTime = System.currentTimeMillis()
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
                    else if (tboxIPRotation.value) {
                        // Выбор следующего IP адреса, если подключения нет больше 60 с
                        if (currentTime - TboxRepository.tboxConnectionTime.value.time > 60000 &&
                            System.currentTimeMillis() - updateIPTime > 60000) {
                            if (ipManager.isCurrentIPLast()) {
                                ipManager.updateIPs(serverIp.value)
                                TboxRepository.updateIPList(ipManager.getIPList())
                                TboxRepository.addLog("DEBUG", "IP manager",
                                    "Update IP list: ${TboxRepository.ipList.value.joinToString("; ")}")
                            }
                            currentIP = ipManager.getNextIP()
                            TboxRepository.addLog("DEBUG", "IP manager", "Set TBox current IP: $currentIP")
                            address = null
                            updateIPTime = System.currentTimeMillis()
                        }
                    }

                    /*for (i in 0 until 25) {
                        TboxRepository.addCanFrameStructured(
                            "00 00 $i 00",
                            byteArrayOf(0x16, 0x56, 0x41, 0x18, 0x08, 0x26, 0x0, 0x0C)
                        )
                        TboxRepository.addCanFrameStructured(
                            "00 00 $i 00",
                            byteArrayOf(0x16, 0x56, 0x41, 0x18, 0x08, 0x26, 0x0, 0x0C)
                        )
                    }*/

                    delay(1000)
                }
            } catch (e: CancellationException) {
                // Нормальная отмена - не логируем
                throw e
            } catch (e: Exception) {
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
                        mdcSendAT("AT+CFUN=$mode,1\r\n".toByteArray())
                    } else {
                        mdcSendAT("AT+CFUN=$mode\r\n".toByteArray())
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
                    mdcSendAT("AT+CFUN?\r\n".toByteArray())
                }
            } catch (e: CancellationException) {
                // Нормальная отмена - не логируем
                throw e
            } catch (e: Exception) {
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
                TboxRepository.addLog("DEBUG", "MDC send", "AT command send: $cmds")
                TboxRepository.addATLog("Send: $cmds")
                sendUdpMessage(socket, serverPort, MDC_CODE, SELF_CODE, 0x0E,
                    byteArrayOf(
                        (cmd.size + 10 shr 8).toByte(), (cmd.size + 10 and 0xFF).toByte(),
                        0xFF.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00) + cmd, false)
            } catch (e: CancellationException) {
                // Нормальная отмена - не логируем
                throw e
            } catch (e: Exception) {
                Log.e("AT command", "Fatal error in AT command job", e)
            }
        }
    }

    private fun crtCmd (cmd: Byte, data: ByteArray, description: String, logLevel: String = "DEBUG") {
        if (!TboxRepository.tboxConnected.value) {
            return
        }
        //if (crtCmdJob?.isActive == true) return
        scope.launch {
            try {
                TboxRepository.addLog(logLevel, "CRT send", description)
                sendUdpMessage(socket, serverPort, CRT_CODE, SELF_CODE, cmd, data, false)
                delay(100)
            } catch (e: CancellationException) {
                // Нормальная отмена - не логируем
                throw e
            } catch (e: Exception) {
                Log.e("CRT command", "Fatal error in CRT command job", e)
            }
        }
    }

    private fun crtRebootTbox() {
        crtCmd(0x2B, byteArrayOf(0x02), "Restart TBox", "INFO")
    }

    private fun getInfo(
        checkAppVersion: Boolean = true,
        checkCrtVersion: Boolean = true,
        checkMdcVersion: Boolean = true,
        checkLocVersion: Boolean = true,
        checkSwdVersion: Boolean = true,
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
                    sendUdpMessage(
                        socket, serverPort, CRT_CODE, SELF_CODE, 0x01,
                        byteArrayOf(0x00, 0x00), false
                    )
                }

                if (checkMdcVersion) {
                    TboxRepository.addLog("DEBUG", "MDC", "Get MDC Version")
                    sendUdpMessage(
                        socket, serverPort, MDC_CODE, SELF_CODE, 0x01,
                        byteArrayOf(0x00, 0x00), false
                    )
                }

                if (checkLocVersion) {
                    TboxRepository.addLog("DEBUG", "LOC", "Get LOC Version")
                    sendUdpMessage(
                        socket, serverPort, LOC_CODE, SELF_CODE, 0x01,
                        byteArrayOf(0x00, 0x00), false
                    )
                }

                if (checkSwdVersion) {
                    TboxRepository.addLog("DEBUG", "SWD", "Get SWD Version")
                    sendUdpMessage(
                        socket, serverPort, SWD_CODE, SELF_CODE, 0x01,
                        byteArrayOf(0x00, 0x00), false
                    )
                }

                if (checkAppVersion) {
                    TboxRepository.addLog("DEBUG", "APP", "Get APP Version")
                    sendUdpMessage(
                        socket, serverPort, APP_CODE, SELF_CODE, 0x01,
                        byteArrayOf(0x00, 0x00), false
                    )
                }

                if (checkSW) {
                    TboxRepository.addLog("DEBUG", "TBox", "Get SW Version")
                    sendUdpMessage(
                        socket, serverPort, CRT_CODE, SELF_CODE, 0x12,
                        byteArrayOf(0x00, 0x00, 0x01, 0x04.toByte()), false
                    ) // CRT - SW
                }

                if (checkHW) {
                    TboxRepository.addLog("DEBUG", "TBox", "Get HW Version")
                    sendUdpMessage(
                        socket, serverPort, CRT_CODE, SELF_CODE, 0x12,
                        byteArrayOf(0x00, 0x00, 0x01, 0x05.toByte()), false
                    ) // CRT - HW
                }

                if (checkVIN) {
                    TboxRepository.addLog("DEBUG", "TBox", "Get VIN code")
                    sendUdpMessage(
                        socket, serverPort, CRT_CODE, SELF_CODE, 0x12,
                        byteArrayOf(0x00, 0x00, 0x01, 0x0F.toByte()), false
                    ) // CRT VIN code
                }
            } catch (e: CancellationException) {
                // Нормальная отмена - не логируем
                throw e
            } catch (e: Exception) {
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

    private fun ssmGetDynamicCode() {
        if (ssmCmdJob?.isActive == true) return
        ssmCmdJob = scope.launch {
            TboxRepository.addLog("DEBUG", "SSM send", "Send GetDynamicCode command")
            sendUdpMessage(socket, serverPort, 0x35, SELF_CODE, 0x06,
                byteArrayOf(0x00, 0x00), false)
        }
    }

    private fun mdcSendAPNManage(cmd: ByteArray) {
        if (!TboxRepository.tboxConnected.value) {
            return
        }
        if (apnCmdJob?.isActive == true) return
        apnCmdJob = scope.launch {
            sendUdpMessage(socket, serverPort, MDC_CODE, SELF_CODE, 0x10, cmd)
        }
    }

    /*private fun appSuspendTboxApp() {
        if (!TboxRepository.tboxConnected.value) {
            return
        }
        if (appCmdJob?.isActive == true) return
        appCmdJob = scope.launch {
            TboxRepository.addLog("DEBUG", "APP send", "Suspend APP")
            sendUdpMessage(socket, serverPort, APP_CODE, SELF_CODE, 0x02, byteArrayOf(0x00), false)
        }
    }

    private fun appStopTboxApp() {
        if (!TboxRepository.tboxConnected.value) {
            return
        }
        if (appCmdJob?.isActive == true) return
        appCmdJob = scope.launch {
            TboxRepository.addLog("DEBUG", "APP send", "Stop APP")
            sendUdpMessage(socket, serverPort, APP_CODE, SELF_CODE, 0x04, byteArrayOf(0x00), false)
        }
    }*/

    private fun swdPreventRestart() {
        if (!TboxRepository.tboxConnected.value) {
            return
        }
        if (swdCmdJob?.isActive == true) return
        swdCmdJob = scope.launch {
            TboxRepository.addLog("DEBUG", "SWD send", "Prevent restart")
            //sendUdpMessage(socket, serverPort, SWD_CODE, SELF_CODE, 0x07, byteArrayOf(0x00, 0x00, 0x00, 0x01)) //Netstates Prevent Restart
            //sendUdpMessage(socket, serverPort, SWD_CODE, SELF_CODE, 0x07, byteArrayOf(0x00, 0x00, 0x01, 0x01)) //Monitor Prevent Restart
            sendUdpMessage(socket, serverPort, SWD_CODE, SELF_CODE, 0x07, byteArrayOf(0x00, 0x00, 0x02, 0x01), false) //Netstates и Monitor Prevent Restart
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
                sendUdpMessage(socket, serverPort, LOC_CODE, SELF_CODE, 0x05, byteArrayOf(0x02, timeout, 0x00), false)
                //sendUdpMessage(socket, serverPort, LOC_CODE, SELF_CODE, 0x05, byteArrayOf(0x02, timeout, 0x01), false)
            }
            else {
                TboxRepository.addLog("DEBUG", "LOC send", "Location unsubscribe")
                sendUdpMessage(socket, serverPort, LOC_CODE, SELF_CODE, 0x05, byteArrayOf(0x00, 0x00, 0x00), false)
            }
            //TboxRepository.updateLocationSubscribed(value)
        }
    }

    private fun humLightShowReq(value: ByteArray) {
        if (!TboxRepository.tboxConnected.value) {
            return
        }
        if (humJob?.isActive == true) return
        humJob = scope.launch {
            TboxRepository.addLog("DEBUG", "HUM send", "LightShowReq")
            sendUdpMessage(socket, serverPort, HUM_CODE, SELF_CODE, 0x0F, value, false)
        }
    }

    private fun sendSuspendTboxApplication(app: String) {
        val appCode = when (app) {
            "APP" -> APP_CODE
            "MDC" -> MDC_CODE
            "SWD" -> SWD_CODE
            "LOC" -> LOC_CODE
            "CRT" -> CRT_CODE
            "NTM" -> NTM_CODE
            else -> 0.toByte()
        }
        if (appCode == 0.toByte()) return
        scope.launch {
            TboxRepository.addLog("DEBUG", "$app send", "Suspend")
            sendUdpMessage(socket, serverPort, appCode, SELF_CODE, 0x02, byteArrayOf(0x00), false)
        }
    }

    private fun sendResumeTboxApplication(app: String) {
        val appCode = when (app) {
            "APP" -> APP_CODE
            "MDC" -> MDC_CODE
            "SWD" -> SWD_CODE
            "LOC" -> LOC_CODE
            "CRT" -> CRT_CODE
            "NTM" -> NTM_CODE
            else -> 0.toByte()
        }
        if (appCode == 0.toByte()) return
        scope.launch {
            TboxRepository.addLog("DEBUG", "$app send", "Resume")
            sendUdpMessage(socket, serverPort, appCode, SELF_CODE, 0x03, byteArrayOf(0x00), false)
        }
    }

    private fun sendStopTboxApplication(app: String) {
        val appCode = when (app) {
            "APP" -> APP_CODE
            "MDC" -> MDC_CODE
            "SWD" -> SWD_CODE
            "LOC" -> LOC_CODE
            "CRT" -> CRT_CODE
            "NTM" -> NTM_CODE
            else -> 0.toByte()
        }
        if (appCode == 0.toByte()) return
        scope.launch {
            TboxRepository.addLog("DEBUG", "$app send", "Stop")
            sendUdpMessage(socket, serverPort, appCode, SELF_CODE, 0x04, byteArrayOf(0x00), false)
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
            Log.e("Widget", "Failed to send broadcast", e)
        }
    }

    private fun cancelAllJobs() {
        listOf(
            mainJob, periodicJob, apnJob, appCmdJob, crtCmdJob, ssmCmdJob,
            swdCmdJob, locCmdJob, listenJob, apnCmdJob, sendATJob, humJob,
            modemModeJob, checkConnectionJob, versionsJob, generalStateBroadcastJob,
            settingsListenerJob
        ).forEach { job ->
            job?.cancel()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        broadcastSender.stopListeners()
        broadcastSender.clearSubscribers()

        cancelAllJobs()
        job.cancel()

        try {
            socket.close()
        } catch (e: Exception) {
            Log.e("BackgroundService", "Error closing socket", e)
        }

        isRunning = false

        try {
            unregisterReceiver(broadcastReceiver)
            Log.d("BackgroundService", "TboxBroadcastReceiver unregistered")
        } catch (e: Exception) {
            Log.e("BackgroundService", "Failed to unregister TboxBroadcastReceiver", e)
        }

        try {
            themeObserver.stopObserving()
            Log.d("ThemeService", "Service destroyed")
        } catch (e: Exception) {
            Log.e("ThemeService", "Error during service destruction", e)
        }

        // Очищаем ссылки
        address = null
        currentIP = null

        closeOverlay()
        // Устанавливаем состояние DESTROYED для lifecycle
        lifecycleOwner.setCurrentState(Lifecycle.State.DESTROYED)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun sendUdpMessage(socket: DatagramSocket,
                                       port: Int,
                                       tid: Byte,
                                       sid: Byte,
                                       cmd: Byte,
                                       msg: ByteArray,
                                       needLog: Boolean = true): Boolean {
        try {
            if (!TboxRepository.tboxConnected.value || address == null) {
                address = getCurrentAddress()
            }
            if (address == null) {
                return false
            }

            var data = fillHeader(msg.size, tid, sid, cmd) + msg
            val checkSum = xorSum(data)
            data += checkSum
            val packet = DatagramPacket(data, data.size, address, port)
            mutex.withLock {
                withTimeout(1000) { // Таймаут на отправку
                    socket.send(packet)
                }
            }

            if (needLog) {
                TboxRepository.addLog("DEBUG", "UDP message send", toHexString(data))
            }

            if (serverIp.value != currentIP) {
                settingsManager.saveTboxIP(currentIP!!)
            }

            return true
        } catch (e: Exception) {
            TboxRepository.addLog("ERROR", "UDP message send", "Error send message")
            return false
        }
    }

    private fun responseWork(receivePacket: DatagramPacket) {
        val fromAddress = receivePacket.address.hostAddress
        val fromPort = receivePacket.port
        if (!checkPacket(receivePacket.data)) {
            TboxRepository.addLog("ERROR", "UDP response",
                "Unknown data received from $fromAddress:$fromPort: \" +\n" +
                        toHexString(receivePacket.data))
            return
        }
        val dataLength = extractDataLength(receivePacket.data)
        if (!checkLength(receivePacket.data, dataLength)) {
            TboxRepository.addLog("ERROR", "UDP response",
                "Error data length ${receivePacket.data.size-14} < $dataLength " +
                        "received from $fromAddress:$fromPort: " +
                        toHexString(receivePacket.data))
            return
        }
        val receivedData = extractData(receivePacket.data, dataLength)
        if (receivedData.contentEquals(ByteArray(0))) {
            TboxRepository.addLog(
                "ERROR", "UDP response",
                "Checksum error or no data received from $fromAddress:$fromPort: " +
                        toHexString(receivePacket.data))
            return
        }
        val tid = receivePacket.data[9]
        val tids = String.format("%02X", tid)
        var tidName = ""
        val sid = receivePacket.data[8]
        val sids = String.format("%02X", sid)
        val cmd = receivePacket.data[12]
        val cmds = String.format("%02X", cmd)
        var needEndLog = true

        if (tid == MDC_CODE) {
            tidName = "MDC"
            try {
                needEndLog = !if (cmd == 0x87.toByte()) {
                    ansMDCNetState(receivedData)
                    //sendWidgetUpdate()
                } else if ((cmd == 0x90.toByte())) {
                    ansMDCAPNManage(receivedData)
                } else if ((cmd == 0x91.toByte())) {
                    ansMDCAPNState(receivedData)
                    //sendWidgetUpdate()
                } else if ((cmd == 0x8E.toByte())) {
                    ansATcmd(receivedData)
                } else if (cmd == 0x81.toByte()) {
                    ansVersion("MDC", receivedData)
                } else {
                    TboxRepository.addLog("ERROR", "MDC response", "Unknown message from MDC")
                    false
                }
            } catch (e: Exception) {
                TboxRepository.addLog("ERROR", "MDC response", "$e")
            }
        } else if (tid == APP_CODE) {
            tidName = "APP"
            try {
                if (cmd == 0x82.toByte()) {
                    if (receivedData.contentEquals(byteArrayOf(0x01))) {
                        TboxRepository.addLog("INFO", "APP response", "Command SUSPEND complete")
                        TboxRepository.updateSuspendTboxAppSend(true)
                        needEndLog = false
                    } else {
                        TboxRepository.addLog(
                            "ERROR",
                            "APP response",
                            "Command SUSPEND not complete"
                        )
                    }
                } else if (cmd == 0x84.toByte()) {
                    if (receivedData.contentEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00))) {
                        TboxRepository.addLog("INFO", "APP response", "Command STOP complete")
                        TboxRepository.updateTboxAppStoped(true)
                        needEndLog = false
                    } else {
                        TboxRepository.addLog("ERROR", "APP response", "Command STOP not complete")
                    }
                } else if (cmd == 0x81.toByte()) {
                    needEndLog = !ansVersion("APP", receivedData)
                    TboxRepository.updateTboxAppStoped(false)
                    TboxRepository.updateTboxAppVersionAnswer(true)
                } else {
                    TboxRepository.addLog("ERROR", "APP response", "Unknown message from APP")
                }
            } catch (e: Exception) {
                TboxRepository.addLog("ERROR", "APP response", "$e")
            }
        } else if (tid == SWD_CODE) {
            tidName = "SWD"
            try {
                if (cmd == 0x87.toByte()) {
                    if (receivedData.contentEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00))) {
                        TboxRepository.addLog("INFO", "SWD response", "Command PreventRestart complete")
                        TboxRepository.updatePreventRestartSend(true)
                        needEndLog = false
                    }
                    else {
                        TboxRepository.addLog("ERROR", "SWD response", "Command PreventRestart not complete")
                    }
                } else if (cmd == 0x81.toByte()) {
                    needEndLog = !ansVersion("SWD", receivedData)
                } else {
                    TboxRepository.addLog("ERROR", "SWD response", "Unknown message from SWD")
                }
            } catch (e: Exception) {
                TboxRepository.addLog("ERROR", "SWD response", "$e")
            }
        } else if (tid == CRT_CODE) {
            tidName = "CRT"
            try {
                when (cmd) {
                    0x90.toByte() -> {
                        needEndLog = !ansCRTPowVol(receivedData)
                    }

                    0x92.toByte() -> {
                        needEndLog = !ansCRTDidData(receivedData)
                    }

                    0x94.toByte() -> {
                        needEndLog = !ansCRTHdmData(receivedData)
                    }

                    0x81.toByte() -> {
                        needEndLog = !ansVersion("CRT", receivedData)
                    }

                    0x95.toByte() -> {
                        needEndLog = !ansCRTCanFrame(receivedData)
                    }

                    0x96.toByte() -> {
                        needEndLog = !ansCRTCycleSignal(receivedData)
                    }

                    else -> {
                        TboxRepository.addLog("ERROR", "CRT response", "Unknown message from CRT")
                    }
                }
            } catch (e: Exception) {
                TboxRepository.addLog("ERROR", "CRT response", "$e")
            }
        } else if (tid == LOC_CODE) {
            tidName = "LOC"
            try {
                when (cmd) {
                    0x85.toByte() -> {
                        needEndLog = !ansLOCValues(receivedData)
                    }

                    0x81.toByte() -> {
                        needEndLog = !ansVersion("LOC", receivedData)
                    }

                    0x82.toByte() -> {
                        if (receivedData.contentEquals(byteArrayOf(0x01))) {
                            TboxRepository.addLog("INFO", "LOC response", "Command SUSPEND complete")
                            needEndLog = false
                        } else {
                            TboxRepository.addLog(
                                "ERROR",
                                "LOC response",
                                "Command SUSPEND not complete"
                            )
                        }
                    }

                    0x83.toByte() -> {
                        if (receivedData.contentEquals(byteArrayOf(0x01))) {
                            TboxRepository.addLog("INFO", "LOC response", "Command RESUME complete")
                            needEndLog = false
                        } else {
                            TboxRepository.addLog(
                                "ERROR",
                                "LOC response",
                                "Command RESUME not complete"
                            )
                        }
                    }

                    0x84.toByte() -> {
                        if (receivedData.contentEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00))) {
                            TboxRepository.addLog("INFO", "LOC response", "Command STOP complete")
                            needEndLog = false
                        } else {
                            TboxRepository.addLog(
                                "ERROR",
                                "LOC response",
                                "Command STOP not complete"
                            )
                        }
                    }

                    else -> {
                        TboxRepository.addLog(
                            "ERROR", "LOC response", "Unknown message from LOC"
                        )
                    }
                }
            } catch (e: Exception) {
                TboxRepository.addLog("ERROR", "LOC response", "$e")
            }
        } else {
            TboxRepository.addLog("ERROR", "UDP response",
                "Unknown TID 0x$tids")
        }
        if (needEndLog) {
            TboxRepository.addLog(
                "DEBUG", "UDP response",
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
            apnStatus = if (data[6] == 0x01.toByte()) {
                true
            } else {
                false
            }
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
        val ans = String(data.copyOfRange(10, data.size), charset = Charsets.UTF_8).trimEnd()

        TboxRepository.addATLog("Receive: $ans")

        if ("ERROR" in ans) {
            TboxRepository.addLog("ERROR", "MDC AT response", "AT command error: $ans")
            return false
        }
        TboxRepository.addLog("DEBUG", "MDC AT response", "AT command answer: $ans")
        if ("+CFUN: 0" in ans || ("AT+CFUN=0" in ans && "OK" in ans)) {
            TboxRepository.updateModemStatus(0)
        } else if ("+CFUN: 1" in ans || ("AT+CFUN=1" in ans && "OK" in ans)) {
            TboxRepository.updateModemStatus(1)
        } else if ("+CFUN: 4" in ans || ("AT+CFUN=4" in ans && "OK" in ans)) {
            TboxRepository.updateModemStatus(4)
        }
        return true
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
            CanFramesProcess.process(data)
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
            TboxRepository.addLog("DEBUG", "CRT response",
                "Get Cycle signal: ${toHexString(rawValue)}")
            if (rawValue.size >= 3) {
                val voltage = rawValue.copyOfRange(1, 3).toFloat("UINT16_LE") / 1000f
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
                } catch (e: Exception) {
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
            val buffer = ByteBuffer.wrap(data.copyOfRange(4, 6)).order(ByteOrder.LITTLE_ENDIAN)

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

    private fun ansVersion(app: String, data: ByteArray): Boolean {
        if (!data.copyOfRange(0, 4).contentEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00))) {
            TboxRepository.addLog("ERROR", "$app response", "Error version info")
            return false
        }
        val version =  String(data.copyOfRange(4, data.size), charset = Charsets.UTF_8).trimEnd()
        scope.launch {
            settingsManager.saveCustomString("${app.lowercase()}_version", version)
        }
        TboxRepository.addLog("DEBUG", "$app response", "Version info: $version")
        return true
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
                "Get location values")
            return false
        }
        return true
    }

    private fun onTboxConnected(value: Boolean = false) {
        if (value) {
            TboxRepository.addLog("INFO", "UDP Listener", "TBox connected")
            TboxRepository.updateTboxConnected(true)

            modemMode(-1)

            if (autoSuspendTboxApp.value) {
                sendSuspendTboxApplication("APP")
                suspendTboxAppLastTime = System.currentTimeMillis()
            }
            if (autoStopTboxApp.value) {
                sendStopTboxApplication("APP")
                stopTboxAppLastTime = System.currentTimeMillis()
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
            //if (getCycleSignal.value) {
            //    crtGetCycleSignal()
            //}
            if (getLocData.value) {
                locSubscribe(true)
            }
            //crtGetHdmData()
            val notification = createNotification("TBox connected")
            startForeground(NOTIFICATION_ID, notification)
        }
        else {
            TboxRepository.addLog("WARN", "UDP Listener", "TBox disconnected")
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
        } catch (e: TimeoutCancellationException) {
            false
        }
    }

    fun ByteArray.toFloat(format: String = "UINT16_BE"): Float {
        return when (format) {
            "UINT16_BE" -> {
                require(this.size >= 2) { "ByteArray must have at least 2 bytes for UINT16_BE" }
                val intValue = ((this[0].toInt() and 0xFF) shl 8) or
                        (this[1].toInt() and 0xFF)
                intValue.toFloat()
            }
            "UINT16_LE" -> {
                require(this.size >= 2) { "ByteArray must have at least 2 bytes for UINT16_LE" }
                val intValue = ((this[1].toInt() and 0xFF) shl 8) or
                        (this[0].toInt() and 0xFF)
                intValue.toFloat()
            }
            "UINT24_BE" -> {
                require(this.size >= 3) { "ByteArray must have at least 3 bytes for UINT24_BE" }
                val intValue = ((this[0].toInt() and 0xFF) shl 16) or
                        ((this[1].toInt() and 0xFF) shl 8) or
                        (this[2].toInt() and 0xFF)
                intValue.toFloat()
            }
            "UINT24_LE" -> {
                require(this.size >= 3) { "ByteArray must have at least 3 bytes for UINT24_LE" }
                val intValue = ((this[2].toInt() and 0xFF) shl 16) or
                        ((this[1].toInt() and 0xFF) shl 8) or
                        (this[0].toInt() and 0xFF)
                intValue.toFloat()
            }
            else -> throw IllegalArgumentException("Unknown format: $format. Supported: UINT16_BE, UINT16_LE, UINT24_BE, UINT24_LE")
        }
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("BackgroundService", "Coroutine error", throwable)
        TboxRepository.addLog("ERROR", "Coroutine", "Error: ${throwable.message}")
    }
}
