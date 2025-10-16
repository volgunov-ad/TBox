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
    private lateinit var scope: CoroutineScope
    private val job = SupervisorJob()
    private lateinit var autoModemRestart: StateFlow<Boolean>
    private lateinit var autoTboxReboot: StateFlow<Boolean>
    private lateinit var autoPreventTboxRestart: StateFlow<Boolean>
    private lateinit var updateVoltages: StateFlow<Boolean>
    private lateinit var serverIp: StateFlow<String>
    private val serverPort = 50047
    private var address: InetAddress? = null
    private lateinit var themeObserver: ThemeObserver
    private val socket = DatagramSocket().apply {soTimeout = 1000}
    private val mutex = Mutex()

    private var mainJob: Job? = null
    private var periodicJob: Job? = null
    private var apnJob: Job? = null
    private var appCmd: Job? = null
    private var crtCmd: Job? = null
    private var ssmCmd: Job? = null
    private var swdCmd: Job? = null
    private var locCmd: Job? = null
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

    companion object {
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
        serverIp = settingsManager.tboxIPFlow
            .stateIn(scope, SharingStarted.Eagerly, DEFAULT_TBOX_IP)

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
            InetAddress.getByName(serverIp.value)
        } catch (e: Exception) {
            TboxRepository.addLog("ERROR", "Tbox IP address",
                "Failed to get address for IP: ${serverIp.value}")
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
                        0x25,
                        0x37,
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
                val receiveData = ByteArray(1024)
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
                            0x25,
                            0x37,
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
                            0x25,
                            0x37,
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
                //delay(5000)
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

                            if (!checkConnection()) {
                                if (autoTboxReboot.value) {
                                    TboxRepository.addLog("WARN", "Net connection checker",
                                        "No network connection. Restart TBox")
                                    crtRebootTbox()
                                    delay(rebootTimeout)
                                    rebootTimeout = 1800000
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
                var preventRestartLastTime = Date()
                var preventRestartTimeDiff: Long
                delay(5000)
                sendWidgetUpdate()
                var widgetUpdateTime = Date()
                delay(15000)
                while (isActive) {
                    if(Date().time - widgetUpdateTime.time > 5000) {
                        sendWidgetUpdate()
                        widgetUpdateTime = Date()
                    }
                    if (TboxRepository.locationSubscribed.value){
                        val delta =  (Date().time - TboxRepository.locValues.value.updateTime.time) / 1000
                        if (delta > LOCATION_UPDATE_TIME * 3) {
                            TboxRepository.updateLocationSubscribed(false)
                        }
                    }
                    if (updateVoltages.value) {
                        val delta = Date().time - TboxRepository.voltages.value.updateTime.time
                        if (delta > 30000) {
                            TboxRepository.updateVoltages(VoltagesState(0.0, 0.0, 0.0))
                            if (TboxRepository.tboxConnected.value) {
                                crtGetPowVolInfo()
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
                sendUdpMessage(socket, serverPort, 0x25, 0x37, 0x0E,
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
        if (crtCmd?.isActive == true) return
        crtCmd = scope.launch {
            try {
                TboxRepository.addLog("DEBUG", "CRT send", descr)
                sendUdpMessage(socket, serverPort, 0x23, 0x37, cmd, data)
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
                sendUdpMessage(socket, serverPort, 0x23, 0x37, 0x01,
                    byteArrayOf(0x00, 0x00))
                sendUdpMessage(socket, serverPort, 0x25, 0x37, 0x01,
                    byteArrayOf(0x00, 0x00))
                sendUdpMessage(socket, serverPort, 0x29, 0x37, 0x01,
                    byteArrayOf(0x00, 0x00))
                sendUdpMessage(socket, serverPort, 0x2D, 0x37, 0x01,
                    byteArrayOf(0x00, 0x00))
                sendUdpMessage(socket, serverPort, 0x2F, 0x37, 0x01,
                    byteArrayOf(0x00, 0x00))
            } catch (e: CancellationException) {
                // Нормальная отмена - не логируем
                throw e
            } catch (e: Exception) {
                Log.e("Versions", "Fatal error in Versions job", e)
            }
        }
    }

    private fun crtGetCanFrame() {
        crtCmd(0x12, byteArrayOf(0x00, 0x14.toByte()), "Send GetDid command")
    }

    private fun crtGetPowVolInfo() {
        crtCmd(0x10, byteArrayOf(0x00, 0x00, 0x00, 0x00), "Send GetPowVolInfo command")
    }

    private fun crtGetHdmData() {
        crtCmd(0x14, byteArrayOf(0x00, 0x00), "Send GetHdmData command")
    }

    private fun ssmGetDynamicCode() {
        if (ssmCmd?.isActive == true) return
        ssmCmd = scope.launch {
            TboxRepository.addLog("DEBUG", "SSM send", "Send GetDynamicCode command")
            sendUdpMessage(socket, serverPort, 0x35, 0x37, 0x06,
                byteArrayOf(0x00, 0x00))
        }
    }

    private fun mdcSendAPNManage(cmd: ByteArray) {
        if (!TboxRepository.tboxConnected.value) {
            return
        }
        if (apnCmdJob?.isActive == true) return
        apnCmdJob = scope.launch {
            sendUdpMessage(socket, serverPort, 0x25, 0x37, 0x10, cmd)
        }
    }

    private fun appSuspendTboxApp() {
        if (!TboxRepository.tboxConnected.value) {
            return
        }
        if (appCmd?.isActive == true) return
        appCmd = scope.launch {
            TboxRepository.addLog("DEBUG", "APP send", "Suspend APP")
            sendUdpMessage(socket, serverPort, 0x2F, 0x37, 0x02, byteArrayOf(0x00))
        }
    }

    private fun swdPreventRestart() {
        if (!TboxRepository.tboxConnected.value) {
            return
        }
        if (swdCmd?.isActive == true) return
        swdCmd = scope.launch {
            TboxRepository.addLog("DEBUG", "SWD send", "Prevent restart")
            sendUdpMessage(socket, serverPort, 0x2D, 0x37, 0x07, byteArrayOf(0x00, 0x00, 0x00, 0x00))
        }
    }

    private fun locSubscribe(value: Boolean = false) {
        if (!TboxRepository.tboxConnected.value) {
            return
        }
        if (locCmd?.isActive == true) return
        locCmd = scope.launch {
            if (value) {
                TboxRepository.addLog("DEBUG", "LOC send", "Location subscribe")
                val timeout = (LOCATION_UPDATE_TIME).toByte()
                sendUdpMessage(socket, serverPort, 0x29, 0x37, 0x05, byteArrayOf(0x02, timeout, 0x00))
            }
            else {
                TboxRepository.addLog("DEBUG", "LOC send", "Location unsubscribe")
                sendUdpMessage(socket, serverPort, 0x29, 0x37, 0x05, byteArrayOf(0x00, 0x00, 0x00))
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
        }
        try {
            sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e("Widget", "Failed to send broadcast", e)
        }
    }

    private fun cancelAllJobs() {
        listOf(
            mainJob, periodicJob, apnJob, appCmd, crtCmd, ssmCmd,
            swdCmd, locCmd, listenJob, apnCmdJob, sendATJob,
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
            address = getCurrentAddress()
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
            return true
        } catch (e: Exception) {
            TboxRepository.addLog("ERROR", "UDP message send", "Error send message")
            e.printStackTrace()
            return false
        }
    }

    private fun responseWork(receivePacket: DatagramPacket) {
        val fromAddress = receivePacket.address.hostAddress
        val fromPort = receivePacket.port
        if (checkPacket(receivePacket.data)) {
            val dataLength = extractDataLength(receivePacket.data)
            val receivedData = extractData(receivePacket.data, dataLength)
            //TboxRepository.addLog("DEBUG", "UDP response",
            //    "Received from $fromAddress:$fromPort: " + toHexString(receivePacket.data))
            if (!receivedData.contentEquals(ByteArray(0))) {
                val tid = receivePacket.data[9]
                val tids = String.format("%02X", tid)
                val sid = receivePacket.data[8]
                val sids = String.format("%02X", sid)
                val cmd = receivePacket.data[12]
                val cmds = String.format("%02X", cmd)

                if (tid == 0x25.toByte()) {
                    if (receivedData.size >= 4) {
                        if (cmd == 0x87.toByte()) {
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
                        }
                    } else {
                        TboxRepository.addLog("ERROR", "MDC response", "Too small data length")
                    }
                } else if (tid == 0x2F.toByte()) {
                    if (cmd == 0x82.toByte()) {
                        if (receivedData.contentEquals(byteArrayOf(0x01))) {
                            TboxRepository.addLog("DEBUG", "APP response", "Command SUSPEND complete")
                            TboxRepository.updateSuspendTboxAppSend(true)
                        }
                        else {
                            TboxRepository.addLog("ERROR", "APP response", "Command SUSPEND not complete")
                        }
                    } else if (cmd == 0x84.toByte()) {
                        if (receivedData.contentEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00))) {
                            TboxRepository.addLog("DEBUG", "APP response", "Command STOP complete")
                        } else {
                            TboxRepository.addLog("ERROR", "APP response", "Command STOP not complete")
                        }
                    } else if (cmd == 0x81.toByte()) {
                        ansVersion("APP", receivedData)
                    } else {
                        TboxRepository.addLog("ERROR", "APP response", "Unknown message from APP")
                    }
                } else if (tid == 0x2D.toByte()) {
                    if (cmd == 0x87.toByte()) {
                        if (receivedData.contentEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00))) {
                            TboxRepository.addLog("DEBUG", "SWD response", "Command PreventRestart complete")
                            TboxRepository.updatePreventRestartSend(true)
                        }
                        else {
                            TboxRepository.addLog("ERROR", "SWD response", "Command PreventRestart not complete")
                        }
                    } else if (cmd == 0x81.toByte()) {
                        ansVersion("SWD", receivedData)
                    } else {
                        TboxRepository.addLog("ERROR", "SWD response", "Unknown message from SWD")
                    }
                } else if (tid == 0x23.toByte()) {
                    when (cmd) {
                        0x90.toByte() -> {
                            ansCRTPowVol(receivedData)
                        }
                        0x94.toByte() -> {
                            ansHdmData(receivedData)
                        }
                        0x81.toByte() -> {
                            ansVersion("CRT", receivedData)
                        }
                        else -> {
                            TboxRepository.addLog("ERROR", "CRT response", "Unknown message from CRT")
                        }
                    }
                } else if (tid == 0x29.toByte()) {
                    when (cmd) {
                        0x85.toByte() -> {
                            ansLOCValues(receivedData)
                        }
                        0x81.toByte() -> {
                            ansVersion("LOC", receivedData)
                        }
                        else -> {
                            TboxRepository.addLog("ERROR", "LOC response", "Unknown message from LOC")
                        }
                    }
                } else {
                    TboxRepository.addLog("ERROR", "UDP response", "Unknown TID 0x$tids")
                }
                TboxRepository.addLog("DEBUG", "UDP response",
                    "Received from $fromAddress:$fromPort: TID: 0x$tids, SID: 0x$sids, CMD: 0x$cmds - " +
                        toHexString(receivedData))
            } else {
                TboxRepository.addLog("ERROR", "UDP response",
                    "Unknown data received from $fromAddress:$fromPort: " +
                            toHexString(receivePacket.data))
            }
        }
        else {
            TboxRepository.addLog("ERROR", "UDP response",
                "Unknown data received from $fromAddress:$fromPort: \" +\n" +
                    toHexString(receivePacket.data))
        }
    }

    private fun ansMDCNetState(data: ByteArray) {
        TboxRepository.addLog("DEBUG", "MDC response ", "Get network state")
        if (data.copyOfRange(0, 4)
                .contentEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        ) {
            TboxRepository.addLog("ERROR", "MDC response ", "Error check network state")
        }
        else if (data.copyOfRange(0, 4)
                .contentEquals(byteArrayOf(0xF4.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        ) {
            TboxRepository.addLog("ERROR", "MDC response ", "Error check network state - state not correct")
        }
        else if (data.copyOfRange(0, 4)
                .contentEquals(byteArrayOf(0xF5.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        ) {
            TboxRepository.addLog("ERROR", "MDC response ", "Error check network state - subscribe type not correct")
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

    private fun ansMDCAPNManage(data: ByteArray) {
        TboxRepository.addLog("DEBUG", "MDC response ", "Get APN manage response")
        if (data.copyOfRange(0, 4)
                .contentEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        ) {
            TboxRepository.addLog("ERROR", "MDC APN Manage response ", "Error APN manage")
        }
        else if (data.copyOfRange(0, 4)
                .contentEquals(byteArrayOf(0xF4.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        ) {
            TboxRepository.addLog("ERROR", "MDC APN Manage response ", "The operation is not permitted in the current APN state")
        }
        else if (data.copyOfRange(0, 4)
                .contentEquals(byteArrayOf(0xF5.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        ) {
            TboxRepository.addLog("ERROR", "MDC APN Manage response ", "Invalid APN channel number")
        }
        else if (data.copyOfRange(0, 4)
                .contentEquals(byteArrayOf(0, 0, 0, 0))
        ) {
            TboxRepository.addLog("ERROR", "MDC APN Manage response ", "APN command completed")
        }
    }

    private fun ansMDCAPNState(data: ByteArray) {
        TboxRepository.addLog("DEBUG", "MDC response ", "Get APN state")
        if (data.copyOfRange(0, 4)
                .contentEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        ) {
            TboxRepository.addLog("ERROR", "MDC APN State response ", "APN state check error")
        }

        var apnStatus = "-"
        var apnType = "-"
        var apnIP = "-"
        var apnGate = "-"
        var apnDNS1 = "-"
        var apnDNS2 = "-"
        if (data.size >= 87) {
            apnStatus = if (data[6] == 0x01.toByte()) {
                "подключен"
            } else {
                "отключен"
            }
            apnType = String(data, 7, 32, Charsets.UTF_8)
            apnIP = String(data, 39, 15, Charsets.UTF_8)
            apnGate = String(data, 55, 15, Charsets.UTF_8)
            apnDNS1 = String(data, 71, 15, Charsets.UTF_8)
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

    private fun ansATcmd(data: ByteArray) {
        if (data.copyOfRange(0, 4).contentEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        ) {
            TboxRepository.addLog("ERROR", "MDC AT response ", "Error sending AT command")
        } else {
            val ans = String(data.copyOfRange(10, data.size), charset = Charsets.UTF_8).trimEnd()
            if ("ERROR" in ans) {
                TboxRepository.addLog("ERROR", "MDC AT response ", "AT command error: $ans")
                return
            }
            TboxRepository.addLog("DEBUG", "MDC AT response ", "AT command answer: $ans")
            if ("+CFUN: 0" in ans || ("AT+CFUN=0" in ans && "OK" in ans)) {
                TboxRepository.updateModemStatus(0)
            } else if ("+CFUN: 1" in ans || ("AT+CFUN=1" in ans && "OK" in ans)) {
                TboxRepository.updateModemStatus(1)
            } else if ("+CFUN: 4" in ans || ("AT+CFUN=4" in ans && "OK" in ans)) {
                TboxRepository.updateModemStatus(4)
            }
        }
    }

    private fun ansCRTPowVol(data: ByteArray) {
        if (data.copyOfRange(0, 4).contentEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00))) {
            TboxRepository.addLog("DEBUG", "CRT response", "Get power voltages info")
        } else {
            TboxRepository.addLog("ERROR", "CRT response ", "Error power voltages info")
            return
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
            //TboxRepository.addLog("INFO", "CRT response", "$voltage1 V, $voltage2 V, $voltage3 V")
        }
    }

    private fun ansHdmData(data: ByteArray) {
        if (data.copyOfRange(0, 4).contentEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00))) {
            TboxRepository.addLog("DEBUG", "CRT response", "Get HDM data")
        } else {
            TboxRepository.addLog("ERROR", "CRT response ", "Error HDM data")
            return
        }
        if (data.size >= 7) {
            val buffer = ByteBuffer.wrap(data.copyOfRange(4, 6)).order(ByteOrder.LITTLE_ENDIAN)

            val isPower = buffer.get().toInt() and 0xFF != 0
            val isIgnition = buffer.get().toInt() and 0xFF != 0
            val isCan = buffer.get().toInt() and 0xFF != 0

            TboxRepository.updateHdm(HdmData(isPower, isIgnition, isCan))
        }
    }

    private fun ansVersion(app: String, data: ByteArray) {
        if (!data.copyOfRange(0, 4).contentEquals(byteArrayOf(0x00, 0x00, 0x00, 0x00))) {
            TboxRepository.addLog("ERROR", "$app response ", "Error version info")
            return
        }
        val version =  String(data.copyOfRange(4, data.size), charset = Charsets.UTF_8).trimEnd()
        scope.launch {
            settingsManager.saveCustomString("${app.lowercase()}_version", version)
        }
        TboxRepository.addLog("DEBUG", "$app response", "Version info: $version")
    }

    private fun ansLOCValues(data: ByteArray) {
        TboxRepository.addLog("DEBUG", "LOC response", "Get location values")
        if (data.copyOfRange(0, 4)
                .contentEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        ) {
            TboxRepository.addLog("ERROR", "LOC response ", "Error location")
            return
        }

        if (data.size == 6) {
            if (data.copyOfRange(0, 4).contentEquals(byteArrayOf(0x0, 0x00, 0x00, 0x00))) {
                TboxRepository.addLog("DEBUG", "LOC response ", "Location subscribe command complete")
                return
            }
        }

        if (data.size >= 45) {
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
        }
    }

    private fun onTboxConnected(value: Boolean = false) {
        if (value) {
            TboxRepository.addLog("INFO", "UDP Listener", "TBox connected")
            TboxRepository.updateTboxConnected(true)
            modemMode(-1)

            //if (settingsManager.getAutoStopTboxAppSetting()) {
            //    suspendTboxApp()
            //}
            //if (autoPreventTboxRestart.value) {
            if (autoPreventTboxRestart.value) {
                appSuspendTboxApp()
                swdPreventRestart()
            }
            //ssmGetDynamicCode()
            if (updateVoltages.value) {
                crtGetPowVolInfo()
            }
            crtGetHdmData()
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

fun extractData(data: ByteArray, length: Int): ByteArray {
    if (data.size < 14 + length) {
        return ByteArray(0)
    }
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

fun toHexString(data: ByteArray): String {
    return data.joinToString(" ") { "%02X".format(it) }
}
