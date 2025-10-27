package com.dashing.tbox

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Date

class BackgroundService : Service() {
    private lateinit var settingsManager: SettingsManager
    private lateinit var ipManager: IPManager
    private lateinit var scope: CoroutineScope
    private val job = SupervisorJob()
    private lateinit var autoModemRestart: StateFlow<Boolean>
    private lateinit var autoTboxReboot: StateFlow<Boolean>
    private lateinit var autoPreventTboxRestart: StateFlow<Boolean>
    private lateinit var updateVoltages: StateFlow<Boolean>
    private lateinit var getCanFrame: StateFlow<Boolean>
    private lateinit var serverIp: StateFlow<String>
    private lateinit var widgetShowIndicator: StateFlow<Boolean>
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
    private var crtWaitCmdJob: Job? = null
    private var ssmCmdJob: Job? = null
    private var swdCmdJob: Job? = null
    private var locCmdJob: Job? = null
    private var listenJob: Job? = null
    private var apnCmdJob: Job? = null
    private var sendATJob: Job? = null
    private var modemModeJob: Job? = null
    private var checkConnectionJob: Job? = null
    private var versionsJob: Job? = null

    private var netUpdateTime: Long = 5000
    private var apnUpdateTime: Long = 10000
    private var netUpdateCount: Int = 0
    private var apn1UpdateCount: Int = 0
    private var apn2UpdateCount: Int = 0
    private var apnCheck: Boolean = false
    private var preventRestartLastTime = Date()

    companion object {
        private const val APP_CODE = 0x2F.toByte()
        private const val MDC_CODE = 0x25.toByte()
        private const val LOC_CODE = 0x29.toByte()
        private const val CRT_CODE = 0x23.toByte()
        private const val SWD_CODE = 0x2D.toByte()
        private const val NTM_CODE = 0x24.toByte()
        private const val GATE_CODE = 0x37.toByte()
        private const val DEFAULT_TBOX_IP = "192.168.225.1"
        private var isRunning = false
        const val LOCATION_UPDATE_TIME = 1
        const val NOTIFICATION_ID = 50047
        const val CHANNEL_ID = "tbox_background_channel"

        const val ACTION_UPDATE_WIDGET = "com.dashing.tbox.UPDATE_WIDGET"
        const val EXTRA_CSQ = "com.dashing.tbox.CSQ"
        const val EXTRA_TBOX_STATUS = "com.dashing.tbox.TBOX_STATUS"
        const val EXTRA_NET_TYPE = "com.dashing.tbox.NET_TYPE"
        const val EXTRA_APN_STATUS = "com.dashing.tbox.EXTRA_APN_STATUS"
        const val EXTRA_PIN = "com.dashing.tbox.EXTRA_PIN"
        const val EXTRA_PUK = "com.dashing.tbox.EXTRA_PUK"
        const val EXTRA_THEME = "com.dashing.tbox.EXTRA_THEME"
        const val EXTRA_AT_CMD = "com.dashing.tbox.EXTRA_AT_CMD"
        const val EXTRA_WIDGET_SHOW_INDICATOR = "com.dashing.tbox.EXTRA_WIDGET_SHOW_INDICATOR"

        const val ACTION_START = "com.dashing.tbox.START"
        const val ACTION_STOP = "com.dashing.tbox.STOP"
        const val ACTION_SEND_AT = "com.dashing.tbox.SEND_AT"
        const val ACTION_MODEM_CHECK = "com.dashing.tbox.MODEM_CHECK"
        const val ACTION_MODEM_OFF = "com.dashing.tbox.MODEM_OFF"
        const val ACTION_MODEM_ON = "com.dashing.tbox.MODEM_ON"
        const val ACTION_MODEM_FLY = "com.dashing.tbox.MODEM_FLY"
        const val ACTION_TBOX_REBOOT = "com.dashing.tbox.TBOX_REBOOT"
        const val ACTION_APN1_RESTART = "com.dashing.tbox.APN1_RESTART"
        const val ACTION_APN1_FLY = "com.dashing.tbox.APN1_FLY"
        const val ACTION_APN1_RECONNECT = "com.dashing.tbox.APN1_RECONNECT"
        const val ACTION_APN2_RESTART = "com.dashing.tbox.APN2_RESTART"
        const val ACTION_APN2_FLY = "com.dashing.tbox.APN2_FLY"
        const val ACTION_APN2_RECONNECT = "com.dashing.tbox.APN2_RECONNECT"
        const val ACTION_TEST1 = "com.dashing.tbox.TEST1"
        const val ACTION_PIN = "com.dashing.tbox.PIN"
        const val ACTION_PUK = "com.dashing.tbox.PUK"
        const val ACTION_LOC_SUBSCRIBE = "com.dashing.tbox.LOC_SUBSCRIBE"
        const val ACTION_LOC_UNSUBSCRIBE = "com.dashing.tbox.LOC_UNSUBSCRIBE"
        const val ACTION_GET_CAN_FRAME = "com.dashing.tbox.GET_CAN_FRAME"
        const val ACTION_GET_VERSIONS = "com.dashing.tbox.GET_VERSIONS"
    }

    override fun onCreate() {
        super.onCreate()

        settingsManager = SettingsManager(this)
        scope = CoroutineScope(Dispatchers.Default + job)

        autoModemRestart = settingsManager.autoModemRestartFlow
            .stateIn(scope, SharingStarted.Eagerly, false)
        autoTboxReboot = settingsManager.autoTboxRebootFlow
            .stateIn(scope, SharingStarted.Eagerly, false)
        autoPreventTboxRestart = settingsManager.autoPreventTboxRestartFlow
            .stateIn(scope, SharingStarted.Eagerly, false)
        updateVoltages = settingsManager.updateVoltagesFlow
            .stateIn(scope, SharingStarted.Eagerly, false)
        getCanFrame = settingsManager.getCanFrameFlow
            .stateIn(scope, SharingStarted.Eagerly, false)
        serverIp = settingsManager.tboxIPFlow
            .stateIn(scope, SharingStarted.Eagerly, DEFAULT_TBOX_IP)
        widgetShowIndicator = settingsManager.widgetShowIndicatorFlow
            .stateIn(scope, SharingStarted.Eagerly, false)

        ipManager = IPManager(this)
        ipManager.updateIPs(serverIp.value)
        TboxRepository.updateIPList(ipManager.getIPList())
        currentIP = ipManager.getNextIP()
        TboxRepository.addLog("DEBUG", "IP manager", "Set TBox current IP: $currentIP")

        //address = InetAddress.getByName(serverIp.value)
        //address = InetAddress.getByName(DEFAULT_TBOX_IP)

        try {
            setupThemeObserver()
            Log.d("ThemeService", "Service created successfully")
        } catch (e: Exception) {
            Log.e("ThemeService", "Failed to create service", e)
            stopSelf() // Останавливаем сервис при критической ошибке
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
        val notification = createNotification(intent?.action)
        startForeground(NOTIFICATION_ID, notification)
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
                    TboxRepository.updateServiceStartTime()
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
                }
            }
            ACTION_SEND_AT -> mdcSendAT("ATI\r\n".toByteArray())
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
            ACTION_TEST1 -> {
                val atCmd = intent.getStringExtra(EXTRA_AT_CMD) ?: "ATI"
                mdcSendAT(atCmd.toByteArray())
                //test(byteArrayOf(0x00, 0x00, 0x31, 0x39, 0x32, 0x2E, 0x31, 0x36, 0x38, 0x2E, 0x32,
                //    0x32, 0x35, 0x2E, 0x32, 0x37, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                //    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x35, 0x30, 0x37, 0x30, 0x00, 0x00))
            }
            ACTION_PIN -> {
                val pin = intent.getStringExtra(EXTRA_PIN) ?: ""
                mdcSendAT("AT+CPIN=\"$pin\"\r\n".toByteArray())
            }
            ACTION_PUK -> {
                val pin = intent.getStringExtra(EXTRA_PIN) ?: ""
                val puk = intent.getStringExtra(EXTRA_PUK) ?: ""
                mdcSendAT("AT+CPIN=\"$puk\",\"$pin\"\r\n".toByteArray())
            }
            ACTION_LOC_SUBSCRIBE -> locSubscribe(true)
            ACTION_LOC_UNSUBSCRIBE -> locSubscribe(false)
            ACTION_GET_CAN_FRAME -> crtGetCanFrame()
            ACTION_GET_VERSIONS -> getVersions()
        }
        return START_STICKY
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
                        GATE_CODE,
                        0x07,
                        byteArrayOf(0x01, 0x00)
                    )
                    netUpdateCount += 1
                    if (netUpdateCount > 2) {
                        clearNetStates()
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
                            GATE_CODE,
                            0x11,
                            byteArrayOf(0x00, 0x00, 0x00, 0x00))
                        apn1UpdateCount += 1
                        if (apn1UpdateCount > 2) {
                            clearAPNStates(1)
                        }
                        delay(500)
                        TboxRepository.addLog("DEBUG", "MDC send", "Update APN2")
                        sendUdpMessage(
                            socket,
                            serverPort,
                            MDC_CODE,
                            GATE_CODE,
                            0x11,
                            byteArrayOf(0x00, 0x00, 0x01, 0x00)
                        )
                        apn2UpdateCount += 1
                        if (apn2UpdateCount > 2) {
                            clearAPNStates(2)
                        }

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
                            modemMode(1, 1000)
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
            TboxRepository.apnState.value.apnStatus == "подключен"
    }

    private fun stopCheckConnection() {
        checkConnectionJob?.cancel()
        checkConnectionJob = null
    }

    private fun startPeriodicJob() {
        if (periodicJob?.isActive == true) return
        periodicJob = scope.launch {
            try {
                Log.d("1s Job", "Start periodic job")
                var preventRestartTimeDiff: Long
                delay(5000)
                sendWidgetUpdate()
                var widgetUpdateTime = Date()
                var crtGetPowVolInfoTime = Date()
                var crtGetCanFrameTime = Date()
                var updateIPTime = Date()
                delay(15000)
                while (isActive) {
                    if(Date().time - widgetUpdateTime.time > 5000) {
                        sendWidgetUpdate()
                        widgetUpdateTime = Date()
                    }
                    if (TboxRepository.locationSubscribed.value){
                        val delta =  (Date().time - TboxRepository.locValues.value.updateTime.time) / 1000
                        if (delta > LOCATION_UPDATE_TIME * 6) {
                            TboxRepository.updateLocationSubscribed(false)
                        }
                    }
                    if (updateVoltages.value) {
                        val delta = Date().time - TboxRepository.voltages.value.updateTime.time
                        if (delta > 30000) {
                            TboxRepository.updateVoltages(VoltagesState(0.0, 0.0, 0.0))
                            if (TboxRepository.tboxConnected.value && Date().time - crtGetPowVolInfoTime.time > 10000) {
                                crtGetPowVolInfo()
                                crtGetPowVolInfoTime = Date()
                            }
                        }
                    }
                    if (getCanFrame.value) {
                        val delta = Date().time - TboxRepository.canFrameTime.value.time
                        if (delta > 60000) {
                            if (TboxRepository.tboxConnected.value && Date().time - crtGetCanFrameTime.time > 10000) {
                                crtGetCanFrame()
                                crtGetCanFrameTime = Date()
                            }
                        }
                    }
                    if (TboxRepository.tboxConnected.value) {
                        if (autoPreventTboxRestart.value) {
                            // Отправка команд предотвращения перезагрузки, если они не были подтверждены,
                            // но не чаще 1 раза в 15 секунд
                            preventRestartTimeDiff = Date().time - preventRestartLastTime.time
                            if (!TboxRepository.preventRestartSend.value && preventRestartTimeDiff > 15000) {
                                swdPreventRestart()
                                preventRestartLastTime = Date()
                            }
                            if (!TboxRepository.suspendTboxAppSend.value && preventRestartTimeDiff > 15000) {
                                appSuspendTboxApp()
                                preventRestartLastTime = Date()
                            }
                            // Отправка команд предотвращения перезагрузки, через каждые 15 минут
                            if (preventRestartTimeDiff > 900000) {
                                swdPreventRestart()
                                appSuspendTboxApp()
                                preventRestartLastTime = Date()
                            }
                        }
                    }
                    else {
                        // Выбор следующего IP адреса, если подключения нет больше 60 с
                        if (Date().time - TboxRepository.tboxConnectionTime.value.time > 60000 && Date().time - updateIPTime.time > 60000) {
                            if (ipManager.isCurrentIPLast()) {
                                ipManager.updateIPs(serverIp.value)
                                TboxRepository.updateIPList(ipManager.getIPList())
                                TboxRepository.addLog("DEBUG", "IP manager",
                                    "Update IP list: ${TboxRepository.ipList.value.joinToString("; ")}")
                            }
                            currentIP = ipManager.getNextIP()
                            TboxRepository.addLog("DEBUG", "IP manager", "Set TBox current IP: $currentIP")
                            address = null
                            updateIPTime = Date()
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

    private fun modemMode(mode: Int, timeout: Long = 5000, needCheck: Boolean = true) {
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
                    mdcSendAT("AT+CFUN=$mode\r\n".toByteArray())
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
                sendUdpMessage(socket, serverPort, MDC_CODE, GATE_CODE, 0x0E,
                    byteArrayOf(
                        (cmd.size + 10 shr 8).toByte(), (cmd.size + 10 and 0xFF).toByte(),
                        0xFF.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00) + cmd)
            } catch (e: CancellationException) {
                // Нормальная отмена - не логируем
                throw e
            } catch (e: Exception) {
                Log.e("AT command", "Fatal error in AT command job", e)
            }
        }
    }

    private fun crtCmd (cmd: Byte, data: ByteArray, descr: String) {
        if (!TboxRepository.tboxConnected.value) {
            return
        }
        //if (crtCmdJob?.isActive == true) return
        scope.launch {
            try {
                TboxRepository.addLog("DEBUG", "CRT send", descr)
                sendUdpMessage(socket, serverPort, CRT_CODE, GATE_CODE, cmd, data)
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
        crtCmd(0x2B, byteArrayOf(0x02), "Restart TBox")
    }

    private fun getVersions() {
        if (versionsJob?.isActive == true) return
        versionsJob = scope.launch {
            try {
                TboxRepository.addLog("DEBUG", "Versions", "Get apps versions")
                clearVersions()
                sendUdpMessage(socket, serverPort, CRT_CODE, GATE_CODE, 0x01,
                    byteArrayOf(0x00, 0x00))
                sendUdpMessage(socket, serverPort, MDC_CODE, GATE_CODE, 0x01,
                    byteArrayOf(0x00, 0x00))
                sendUdpMessage(socket, serverPort, LOC_CODE, GATE_CODE, 0x01,
                    byteArrayOf(0x00, 0x00))
                sendUdpMessage(socket, serverPort, SWD_CODE, GATE_CODE, 0x01,
                    byteArrayOf(0x00, 0x00))
                sendUdpMessage(socket, serverPort, APP_CODE, GATE_CODE, 0x01,
                    byteArrayOf(0x00, 0x00))
                sendUdpMessage(socket, serverPort, CRT_CODE, GATE_CODE, 0x12,
                    byteArrayOf(0x00, 0x00, 0x01, 0x04.toByte())) // CRT - SW
                sendUdpMessage(socket, serverPort, CRT_CODE, GATE_CODE, 0x12,
                    byteArrayOf(0x00, 0x00, 0x01, 0x05.toByte())) // CRT - HW
            } catch (e: CancellationException) {
                // Нормальная отмена - не логируем
                throw e
            } catch (e: Exception) {
                Log.e("Versions", "Fatal error in Versions job", e)
            }
        }
    }

    private suspend fun clearVersions() {
        settingsManager.saveCustomString("app_version", "")
        settingsManager.saveCustomString("crt_version", "")
        settingsManager.saveCustomString("loc_version", "")
        settingsManager.saveCustomString("mdc_version", "")
        settingsManager.saveCustomString("swd_version", "")
        settingsManager.saveCustomString("sw_version", "")
        settingsManager.saveCustomString("hw_version", "")
    }

    private fun crtGetCanFrame() {
        crtCmd(0x15, byteArrayOf(0x00, 0x00), "Send GetCanFrame command")
        //crtCmd(0x12, byteArrayOf(0x01, 0x15.toByte()), "Send GetDid command CanFrame")
    }

    private fun crtGetPowVolInfo() {
        crtCmd(0x10, byteArrayOf(0x01, 0x01), "Send GetPowVolInfo command")
        //crtCmd(0x12, byteArrayOf(0x01, 0x10.toByte()), "Send GetDid command PowVolInfo")
    }

    private fun crtGetHdmData() {
        crtCmd(0x14, byteArrayOf(0x03, 0x00), "Send GetHdmData command")
        val didList = listOf(0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(),
            0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0x0A.toByte(), 0x0B.toByte(),
            0x09.toByte(), 0x0C.toByte(), 0x0D.toByte(), 0x0E.toByte(), 0x0F.toByte(),
            0x10.toByte(), 0x11.toByte(), 0x12.toByte(), 0x13.toByte(), 0x14.toByte(),
            0x15.toByte(), 0x16.toByte(), 0x17.toByte(), 0x18.toByte(), 0x19.toByte(),
            0x1A.toByte(), 0x1C.toByte(), 0x1B.toByte(), 0x1D.toByte(), 0x1E.toByte(),
            0x1F.toByte(), 0x20.toByte(), 0x21.toByte(), 0x22.toByte(), 0x23.toByte(),
            0x24.toByte(), 0x80.toByte(), 0x81.toByte(), 0x82.toByte(), 0x83.toByte())
        for (i in didList) {
            crtCmd(0x12, byteArrayOf(0x00, 0x00, 0x01, i.toByte()), "Send GetDid command HdmData - byte: 0x${i.toString(16).uppercase()}")
        }
    }

    private fun ssmGetDynamicCode() {
        if (ssmCmdJob?.isActive == true) return
        ssmCmdJob = scope.launch {
            TboxRepository.addLog("DEBUG", "SSM send", "Send GetDynamicCode command")
            sendUdpMessage(socket, serverPort, 0x35, GATE_CODE, 0x06,
                byteArrayOf(0x00, 0x00))
        }
    }

    private fun mdcSendAPNManage(cmd: ByteArray) {
        if (!TboxRepository.tboxConnected.value) {
            return
        }
        if (apnCmdJob?.isActive == true) return
        apnCmdJob = scope.launch {
            sendUdpMessage(socket, serverPort, MDC_CODE, GATE_CODE, 0x10, cmd)
        }
    }

    private fun appSuspendTboxApp() {
        if (!TboxRepository.tboxConnected.value) {
            return
        }
        if (appCmdJob?.isActive == true) return
        appCmdJob = scope.launch {
            TboxRepository.addLog("DEBUG", "APP send", "Suspend APP")
            sendUdpMessage(socket, serverPort, APP_CODE, GATE_CODE, 0x02, byteArrayOf(0x00))
        }
    }

    private fun swdPreventRestart() {
        if (!TboxRepository.tboxConnected.value) {
            return
        }
        if (swdCmdJob?.isActive == true) return
        swdCmdJob = scope.launch {
            TboxRepository.addLog("DEBUG", "SWD send", "Prevent restart")
            sendUdpMessage(socket, serverPort, SWD_CODE, GATE_CODE, 0x07, byteArrayOf(0x00, 0x00, 0x00, 0x00))
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
                sendUdpMessage(socket, serverPort, LOC_CODE, GATE_CODE, 0x05, byteArrayOf(0x02, timeout, 0x00))
            }
            else {
                TboxRepository.addLog("DEBUG", "LOC send", "Location unsubscribe")
                sendUdpMessage(socket, serverPort, LOC_CODE, GATE_CODE, 0x05, byteArrayOf(0x00, 0x00, 0x00))
            }
            TboxRepository.updateLocationSubscribed(value)
        }
    }

    private fun sendWidgetUpdate() {
        val intent = Intent(ACTION_UPDATE_WIDGET).apply {
            setPackage(this@BackgroundService.packageName)
            putExtra(EXTRA_CSQ, TboxRepository.netState.value.csq)
            putExtra(EXTRA_NET_TYPE, TboxRepository.netState.value.netStatus)
            putExtra(EXTRA_TBOX_STATUS, TboxRepository.tboxConnected.value)
            putExtra(EXTRA_APN_STATUS, TboxRepository.apnState.value.apnStatus)
            putExtra(EXTRA_THEME, TboxRepository.currentTheme.value)
            putExtra(EXTRA_WIDGET_SHOW_INDICATOR, widgetShowIndicator.value)
        }
        //TboxRepository.addLog("DEBUG", "Widget", "${widgetShowIndicator.value}")
        try {
            sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e("Widget", "Failed to send broadcast", e)
        }
    }

    private fun cancelAllJobs() {
        listOf(
            mainJob, periodicJob, apnJob, appCmdJob, crtCmdJob, ssmCmdJob,
            swdCmdJob, locCmdJob, listenJob, apnCmdJob, sendATJob,
            modemModeJob, checkConnectionJob, versionsJob
        ).forEach { job ->
            job?.cancel()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelAllJobs()
        job.cancel()
        socket.close()
        isRunning = false
        try {
            themeObserver.stopObserving()
            Log.d("ThemeService", "Service destroyed")
        } catch (e: Exception) {
            Log.e("ThemeService", "Error during service destruction", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun sendUdpMessage(socket: DatagramSocket, port: Int, tid: Byte, sid: Byte, cmd: Byte, msg: ByteArray): Boolean {
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
                socket.send(packet)
            }
            TboxRepository.addLog("DEBUG", "UDP message send", toHexString(data))

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
                        TboxRepository.addLog("DEBUG", "APP response", "Command SUSPEND complete")
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
                        TboxRepository.addLog("DEBUG", "APP response", "Command STOP complete")
                        needEndLog = false
                    } else {
                        TboxRepository.addLog("ERROR", "APP response", "Command STOP not complete")
                    }
                } else if (cmd == 0x81.toByte()) {
                    needEndLog = !ansVersion("APP", receivedData)
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
                        TboxRepository.addLog("DEBUG", "SWD response", "Command PreventRestart complete")
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
        TboxRepository.updateNetState(NetState(
            csq = csq,
            netStatus = netStatus,
            regStatus = regStatus,
            simStatus = simStatus))
        netUpdateCount = 0
        if (TboxRepository.netState.value.regStatus !in listOf("домашняя сеть", "роуминг")) {
            clearAPNStates(1)
            clearAPNStates(2)
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
        TboxRepository.updateNetValues(
            NetValues(
            imei = imei,
            iccid = iccid,
            imsi = imsi,
            operator = operator)
        )
        return true
    }

    private fun clearNetStates() {
        TboxRepository.updateNetState(NetState(
            csq = 99,
            netStatus = "-",
            regStatus = "-",
            simStatus = "-"))
        TboxRepository.updateNetValues(NetValues(
            imei = "-",
            iccid = "-",
            imsi = "-",
            operator = "-"))
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

        var apnStatus = "-"
        var apnType = "-"
        var apnIP = "-"
        var apnGate = "-"
        var apnDNS1 = "-"
        var apnDNS2 = "-"
        if (data.size >= 103) {
            apnStatus = if (data[6] == 0x01.toByte()) {
                "подключен"
            } else {
                "отключен"
            }
            apnType = String(data, 7, 32, Charsets.UTF_8)
            apnIP = String(data, 39, 15, Charsets.UTF_8)
            apnGate = String(data, 87, 15, Charsets.UTF_8)
            apnDNS1 = String(data, 55, 15, Charsets.UTF_8)
            apnDNS2 = String(data, 71, 15, Charsets.UTF_8)
        }
        if (data[4] == 0x00.toByte()) {
            TboxRepository.updateAPNState(APNState(
                apnIP = apnIP,
                apnStatus = apnStatus,
                apnType = apnType,
                apnGate = apnGate,
                apnDNS1 = apnDNS1,
                apnDNS2 = apnDNS2
            ))
            apn1UpdateCount = 0
        }
        else if (data[4] == 0x01.toByte()) {
            TboxRepository.updateAPN2State(APNState(
                apnIP = apnIP,
                apnStatus = apnStatus,
                apnType = apnType,
                apnGate = apnGate,
                apnDNS1 = apnDNS1,
                apnDNS2 = apnDNS2
            ))
            apn2UpdateCount = 0
        }
        return true
    }

    private fun clearAPNStates(number: Int = 1) {
        val apnState = APNState(
            apnIP = "-",
            apnStatus = "-",
            apnType = "-",
            apnGate = "-",
            apnDNS1 = "-",
            apnDNS2 = "-"
        )
        if (number == 1) {
            TboxRepository.updateAPNState(
                apnState
            )
        }
        else {
            TboxRepository.updateAPN2State(
                apnState
            )
        }
    }

    private fun ansATcmd(data: ByteArray): Boolean {
        if (data.copyOfRange(0, 4).contentEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        ) {
            TboxRepository.addLog("ERROR", "MDC AT response", "Error sending AT command")
            return false
        }
        val ans = String(data.copyOfRange(10, data.size), charset = Charsets.UTF_8).trimEnd()
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
            val voltage1 = rawVoltage1.toDouble() / 1000.0

            val rawVoltage2 = buffer.short.toInt() and 0xFFFF
            val voltage2 = rawVoltage2.toDouble() / 1000.0

            val rawVoltage3 = buffer.short.toInt() and 0xFFFF
            val voltage3 = rawVoltage3.toDouble() / 1000.0

            TboxRepository.updateVoltages(VoltagesState(voltage1, voltage2, voltage3))
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
            val rawValue = data.copyOfRange(4, data.size)
            TboxRepository.addCanFrame(toHexString(rawValue))
            for (i in 0 until rawValue.size step 17) {
                try {
                    val rawFrame = rawValue.copyOfRange(i, i + 17)
                    val timeStamp = rawFrame.copyOfRange(0, 4)
                    val canID = rawFrame.copyOfRange(4, 8)
                    val dlc = rawFrame[8]
                    val data = rawFrame.copyOfRange(9, 17)

                    if (canID.contentEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00))) {
                        continue
                    }

                    TboxRepository.addCanFrameStructured(
                        toHexString(canID),
                        data
                    )

                    if (canID.contentEquals(byteArrayOf(0x00, 0x00, 0x00, 0xC4.toByte()))) {
                        val angle = data.copyOfRange(0, 2).toDouble("UINT16_BE") / 100.0
                        val speed = data[2].toInt()
                        TboxRepository.updateSteer(SteerData(angle, speed))
                    } else if (canID.contentEquals(byteArrayOf(0x00, 0x00, 0x00, 0xFA.toByte()))) {
                        val rpm = data.copyOfRange(0, 2).toDouble("UINT16_BE") / 4.0
                        val speed = data[3].toDouble()
                        TboxRepository.updateEngineSpeed(EngineSpeedData(rpm, speed))
                    } else if (canID.contentEquals(byteArrayOf(0x00, 0x00, 0x02, 0x00))) {
                        val speed = data.copyOfRange(4, 6).toDouble("UINT16_BE") / 100.0
                        TboxRepository.updateCarSpeed(CarSpeedData(speed))
                    } else if (canID.contentEquals(byteArrayOf(0x00, 0x00, 0x03, 0x05))) {
                        val speed = data[0].toInt()
                        TboxRepository.updateCruise(Cruise(speed))
                    } else if (canID.contentEquals(byteArrayOf(0x00, 0x00, 0x03, 0x10))) {
                        val speed1 = data.copyOfRange(0, 2).toDouble("UINT16_BE") * 0.065
                        val speed2 = data.copyOfRange(2, 4).toDouble("UINT16_BE") * 0.065
                        val speed3 = data.copyOfRange(4, 6).toDouble("UINT16_BE") * 0.065
                        val speed4 = data.copyOfRange(6, 8).toDouble("UINT16_BE") * 0.065
                        TboxRepository.updateWheels(Wheels(speed1, speed2, speed3, speed4))
                    } else if (canID.contentEquals(byteArrayOf(0x00, 0x00, 0x04, 0x30))) {
                        val odometer = data.copyOfRange(5, 8).toUInt20FromNibbleBigEndian()
                        TboxRepository.updateOdo(OdoData(odometer))
                    }
                } catch (e: Exception) {
                    TboxRepository.addLog("ERROR", "CRT response",
                        "Error get CAN Frame $i: $e")
                }
            }
            TboxRepository.addLog("DEBUG", "CRT response",
                "Get CAN Frame")
        } catch (e: Exception) {
            TboxRepository.addLog("ERROR", "CRT response",
                "Error get CAN Frame: $e")
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
            TboxRepository.updateLocationSubscribed(true)

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
            buffer.get()

            // 3. Долгота (4 байта, int32)
            val rawLongitude = buffer.int
            val longitude = rawLongitude.toDouble() / 1000000.0

            // Пропускаем 1 байт (выравнивание или reserved)
            buffer.get()

            // 4. Широта (4 байта, int32)
            val rawLatitude = buffer.int
            val latitude = rawLatitude.toDouble() / 1000000.0

            // 5. Высота (4 байта, int32)
            val rawAltitude = buffer.int
            val altitude = rawAltitude.toDouble() / 1000000.0

            // 6. Видимые спутники (1 байт)
            val visibleSatellites = buffer.get().toInt() and 0xFF

            // 7. Используемые спутники (1 байт)
            val usingSatellites = buffer.get().toInt() and 0xFF

            // 8. Скорость (2 байта, uint16)
            val rawSpeed = buffer.short.toInt() and 0xFFFF
            val speed = rawSpeed.toDouble() / 10.0

            // 9. Истинное направление (2 байта, uint16)
            val rawTrueDirection = buffer.short.toInt() and 0xFFFF
            val trueDirection = rawTrueDirection.toDouble() / 10.0

            // 10. Магнитное направление (2 байта, uint16)
            val rawMagneticDirection = buffer.short.toInt() and 0xFFFF
            val magneticDirection = rawMagneticDirection.toDouble() / 10.0

            TboxRepository.updateLocValues(LocValues(
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
                magneticDirection = magneticDirection
            ))
            TboxRepository.addLog("DEBUG", "LOC response",
                "Get location values: $longitude, $latitude")
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

            if (autoPreventTboxRestart.value) {
                appSuspendTboxApp()
                swdPreventRestart()
                preventRestartLastTime = Date()
            }
            if (updateVoltages.value) {
                crtGetPowVolInfo()
            }
            if (getCanFrame.value) {
                crtGetCanFrame()
            }
            //crtGetHdmData()
        }
        else {
            TboxRepository.addLog("WARN", "UDP Listener", "TBox disconnected")
            TboxRepository.updateTboxConnected(false)
            clearNetStates()
            clearAPNStates(1)
            clearAPNStates(2)
            //sendWidgetUpdate()
            TboxRepository.updatePreventRestartSend(false)
            TboxRepository.updateSuspendTboxAppSend(false)
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

    fun ByteArray.toUInt20FromNibbleBigEndian(): Int {
        require(this.size >= 3) { "ByteArray must have at least 3 bytes" }
        return ((this[0].toInt() and 0x0F) shl 16) or
                ((this[1].toInt() and 0xFF) shl 8) or
                (this[2].toInt() and 0xFF)
    }

    fun ByteArray.toDouble(format: String = "UINT16_BE"): Double {
        return when (format) {
            "UINT16_BE" -> {
                require(this.size >= 2) { "ByteArray must have at least 2 bytes for UINT16_BE" }
                val intValue = ((this[0].toInt() and 0xFF) shl 8) or
                        (this[1].toInt() and 0xFF)
                intValue.toDouble()
            }
            "UINT16_LE" -> {
                require(this.size >= 2) { "ByteArray must have at least 2 bytes for UINT16_LE" }
                val intValue = ((this[1].toInt() and 0xFF) shl 8) or
                        (this[0].toInt() and 0xFF)
                intValue.toDouble()
            }
            "UINT24_BE" -> {
                require(this.size >= 3) { "ByteArray must have at least 3 bytes for UINT24_BE" }
                val intValue = ((this[0].toInt() and 0xFF) shl 16) or
                        ((this[1].toInt() and 0xFF) shl 8) or
                        (this[2].toInt() and 0xFF)
                intValue.toDouble()
            }
            "UINT24_LE" -> {
                require(this.size >= 3) { "ByteArray must have at least 3 bytes for UINT24_LE" }
                val intValue = ((this[2].toInt() and 0xFF) shl 16) or
                        ((this[1].toInt() and 0xFF) shl 8) or
                        (this[0].toInt() and 0xFF)
                intValue.toDouble()
            }
            else -> throw IllegalArgumentException("Unknown format: $format. Supported: UINT16_BE, UINT16_LE, UINT24_BE, UINT24_LE")
        }
    }
}

fun fillHeader(dataLength: Int,
               tid: Byte,
               sid: Byte,
               param: Byte): ByteArray {
    val header = ByteArray(13)
    header[0] = 0x8E.toByte()      // Стартовый байт
    header[1] = 0x5D.toByte()      // Идентификатор протокола
    header[2] = (dataLength + 10 shr 8).toByte()  // Длина данных (старший байт)
    header[3] = (dataLength + 10 and 0xFF).toByte() // Длина данных (младший байт)
    header[4] = 0x00               // Sequence number
    header[5] = 0x00               // Reserved
    header[6] = 0x01               // Версия протокола
    header[7] = 0x00               // Reserved
    header[8] = tid                // ID целевого модуля
    header[9] = sid                // ID исходного модуля
    header[10] = (dataLength shr 8).toByte()  // Длина данных (старший байт)
    header[11] = (dataLength and 0xFF).toByte() // Длина данных (младший байт)
    header[12] = param             // Команда
    return header
}

fun checkPacket(data: ByteArray): Boolean {
    if (data.isEmpty() || data.size < 14) {
        return false
    }
    if (data[0] != 0x8E.toByte() || data[1] != 0x5D.toByte()) {
        return false
    }
    return true
}

fun extractDataLength(data: ByteArray): Int {
    return ((data[10].toInt() and 0xFF) shl 8) or (data[11].toInt() and 0xFF)
}

fun checkLength(data: ByteArray, length: Int): Boolean {
    return data.size - 14 >= length
}

fun extractData(data: ByteArray, length: Int): ByteArray {
    if (xorSum(data.copyOfRange(0, 13+length)) != data[13+length]) {
        return ByteArray(0)
    }
    return data.copyOfRange(13, 13+length)
}

fun xorSum(data: ByteArray): Byte {
    if (data.isEmpty() || data.size < 9) {
        return 0
    }

    var checksum: Byte = 0
    // Начинаем с 10-го байта (индекс 9, так как индексация с 0)
    for (i in 9 until data.size) {
        checksum = (checksum.toInt() xor data[i].toInt()).toByte()
    }
    return checksum
}

fun toHexString(data: ByteArray, separator: String = " "): String {
    return data.joinToString(separator) { "%02X".format(it) }
}
