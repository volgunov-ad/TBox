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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class BackgroundService : Service() {
    private val serverIp = "192.168.225.1"
    private val serverPort = 50047
    private val serverPort2 = 50047

    private val socket = DatagramSocket().apply {soTimeout = 2000}
    private val socket2 = DatagramSocket().apply {soTimeout = 2000}

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Default + job)
    private val mutex = Mutex()

    private var mainJob: Job? = null
    private var activityJob: Job? = null
    private var sendATJob: Job? = null
    private var singleCmdJob: Job? = null
    private var testJob: Job? = null

    companion object {
        const val NOTIFICATION_ID = 1234
        const val CHANNEL_ID = "tbox_background_channel"

        const val ACTION_UPDATE_WIDGET = "com.dashing.tbox.ACTION_UPDATE_WIDGET"
        const val EXTRA_CSQ = "com.dashing.tbox.CSQ"
        const val EXTRA_TBOX_STATUS = "com.dashing.tbox.TBOX_STATUS"
        const val EXTRA_NET_TYPE = "com.dashing.tbox.NET_TYPE"
        const val EXTRA_APN_STATUS = "com.dashing.tbox.EXTRA_APN_STATUS"
        const val EXTRA_PIN = "com.dashing.tbox.EXTRA_PIN"
        const val EXTRA_PUK = "com.dashing.tbox.EXTRA_PUK"

        const val ACTION_NET_UPD_START = "com.dashing.tbox.NET_UPD_START"
        const val ACTION_NET_UPD_STOP = "com.dashing.tbox.NET_UPD_STOP"
        const val ACTION_APN_UPD_START = "com.dashing.tbox.APN_UPD_START"
        const val ACTION_APN_UPD_STOP = "com.dashing.tbox.APN_UPD_STOP"
        const val ACTION_SEND_AT = "com.dashing.tbox.ACTION_SEND_AT"
        const val ACTION_MODEM_CHECK = "com.dashing.tbox.ACTION_MODEM_CHECK"
        const val ACTION_MODEM_OFF = "com.dashing.tbox.ACTION_MODEM_OFF"
        const val ACTION_MODEM_ON = "com.dashing.tbox.ACTION_MODEM_ON"
        const val ACTION_TBOX_REBOOT = "com.dashing.tbox.ACTION_TBOX_REBOOT"
        const val ACTION_APN1_RESTART = "com.dashing.tbox.ACTION_APN1_RESTART"
        const val ACTION_APN1_FLY = "com.dashing.tbox.ACTION_APN1_FLY"
        const val ACTION_APN1_RECONNECT = "com.dashing.tbox.ACTION_APN1_RECONNECT"
        const val ACTION_APN2_RESTART = "com.dashing.tbox.ACTION_APN2_RESTART"
        const val ACTION_APN2_FLY = "com.dashing.tbox.ACTION_APN2_FLY"
        const val ACTION_APN2_RECONNECT = "com.dashing.tbox.ACTION_APN2_RECONNECT"
        const val ACTION_TEST1 = "com.dashing.tbox.ACTION_TEST1"
        const val ACTION_TEST2 = "com.dashing.tbox.ACTION_TEST2"
        const val ACTION_TEST3 = "com.dashing.tbox.ACTION_TEST3"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        when (intent?.action) {
            ACTION_NET_UPD_START -> startNetUpdater()
            ACTION_NET_UPD_STOP -> stopNetUpdater()
            ACTION_APN_UPD_START -> startAPNUpdater()
            ACTION_APN_UPD_STOP -> stopAPNUpdater()
            ACTION_SEND_AT -> sendAT("ATI\r\n".toByteArray())
            ACTION_MODEM_CHECK -> sendAT("AT+CFUN?\r\n".toByteArray())
            ACTION_MODEM_OFF -> sendAT("AT+CFUN=0\r\n".toByteArray())
            ACTION_MODEM_ON -> sendAT("AT+CFUN=1\r\n".toByteArray())
            ACTION_TBOX_REBOOT -> rebootTbox(byteArrayOf(0x02))
            ACTION_APN1_RESTART -> sendAPNManage(byteArrayOf(0x00, 0x00, 0x01, 0x00))
            ACTION_APN1_FLY -> sendAPNManage(byteArrayOf(0x00, 0x00, 0x02, 0x00))
            ACTION_APN1_RECONNECT -> sendAPNManage(byteArrayOf(0x00, 0x00, 0x03, 0x00))
            ACTION_APN2_RESTART -> sendAPNManage(byteArrayOf(0x00, 0x01, 0x01, 0x00))
            ACTION_APN2_FLY -> sendAPNManage(byteArrayOf(0x00, 0x01, 0x02, 0x00))
            ACTION_APN2_RECONNECT -> sendAPNManage(byteArrayOf(0x00, 0x01, 0x03, 0x00))
            ACTION_TEST1 -> sendAT("AT+CPIN?\r\n".toByteArray())
            ACTION_TEST2 -> {
                val pin = intent.getStringExtra(EXTRA_PIN) ?: ""
                sendAT("AT+CPIN=\"$pin\"\r\n".toByteArray())
            }
            ACTION_TEST3 -> {
                val pin = intent.getStringExtra(EXTRA_PIN) ?: ""
                val puk = intent.getStringExtra(EXTRA_PUK) ?: ""
                sendAT("AT+CPIN=\"$puk\",\"$pin\"\r\n".toByteArray())
            }
        }
        return START_STICKY
        //ACTION_GPS_CHECK -> sendAT("AT\$MYGPSSTATE\r\n".toByteArray())
        //ACTION_GPS_OFF -> sendAT("AT\$MYGPSPWR=0\r\n".toByteArray())
        //ACTION_GPS_ON -> sendAT("AT\$MYGPSPWR=1\r\n".toByteArray())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TBox Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background network monitoring"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TBox Monitor")
            .setContentText("Active connection monitoring")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun startNetUpdater() {
        if (mainJob?.isActive == true) return
        var netErr = 0
        var apnErr = 0
        var apn2Err = 0
        mainJob = scope.launch {
            while (isActive) {
                Log.d("NetUpdater", "Update network state")
                mutex.withLock {
                    if (sendUdpMessage(socket, serverPort, 0x25, 0x37, 0x07, byteArrayOf(0x01, 0x00))) {
                        netErr = 0
                        TboxRepository.updateTboxConnected(true)
                    } else {
                        netErr += 1
                        if (netErr > 1) {
                            clearNetStates()
                            clearAPNStates()
                            clearAPN2States()
                            TboxRepository.updateTboxConnected(false)
                        }
                    }
                    if (TboxRepository.tboxConnected.value) {
                        Log.d("APNUpdater", "Update APN state")
                        if (sendUdpMessage(socket, serverPort, 0x25, 0x37, 0x11, byteArrayOf(0x00, 0x00, 0x00, 0x00))) {
                            apnErr = 0
                        } else {
                            apnErr += 1
                            if (apnErr > 1) {
                                clearAPNStates()
                            }
                        }
                        if (sendUdpMessage(socket, serverPort, 0x25, 0x37, 0x11, byteArrayOf(0x00, 0x00, 0x01, 0x00))) {
                            apn2Err = 0
                        } else {
                            apn2Err += 1
                            if (apn2Err > 1) {
                                clearAPN2States()
                            }
                        }
                    }
                }
                sendWidgetUpdate()
                delay(10000)
            }
        }
    }

    private fun stopNetUpdater() {
        mainJob?.cancel()
        mainJob = null
    }

    private fun startAPNUpdater() {
        if (activityJob?.isActive == true) return
        var apnErr = 0
        activityJob = scope.launch {
            while (isActive) {
                if (TboxRepository.tboxConnected.value) {
                    Log.d("APNUpdater", "Update APN state")
                    mutex.withLock {
                        if (sendUdpMessage(socket, serverPort, 0x25, 0x37, 0x11, byteArrayOf(0x00, 0x00, 0x00, 0x00))) {
                            apnErr = 0
                        } else {
                            apnErr += 1
                            if (apnErr > 1) {
                                clearAPNStates()
                            }
                        }
                    }
                }
                delay(15000)
            }
        }
    }

    private fun stopAPNUpdater() {
        activityJob?.cancel()
        activityJob = null
    }

    private fun sendAT(cmd: ByteArray) {
        if (!TboxRepository.tboxConnected.value) {
            return
        }
        if (sendATJob?.isActive == true) return
        sendATJob = scope.launch {
            mutex.withLock {
                sendUdpMessage(socket, serverPort, 0x25, 0x37, 0x0E,
                    byteArrayOf(
                        (cmd.size + 10 shr 8).toByte(), (cmd.size + 10 and 0xFF).toByte(),
                        0xFF.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00) + cmd)
            }
        }
    }

    private fun rebootTbox(cmd: ByteArray) {
        if (!TboxRepository.tboxConnected.value) {
            return
        }
        if (singleCmdJob?.isActive == true) return
        singleCmdJob = scope.launch {
            mutex.withLock {
                sendUdpMessage(socket, serverPort, 0x23, 0x37, 0x2B, cmd)
            }
        }
    }

    private fun sendAPNManage(cmd: ByteArray) {
        if (!TboxRepository.tboxConnected.value) {
            return
        }
        if (singleCmdJob?.isActive == true) return
        singleCmdJob = scope.launch {
            mutex.withLock {
                sendUdpMessage(socket, serverPort, 0x25, 0x37, 0x10, cmd)
            }
        }
    }

    private fun test(cmd: ByteArray) {
        if (!TboxRepository.tboxConnected.value) {
            return
        }
        if (testJob?.isActive == true) return
        testJob = scope.launch {
            mutex.withLock {
                sendUdpMessage(socket2, serverPort2, 0x23, 0x37, 0x10, cmd)
            }
        }
    }

    private fun sendWidgetUpdate() {
        val intent = Intent(ACTION_UPDATE_WIDGET).apply {
            setPackage(this@BackgroundService.packageName)
            putExtra(EXTRA_CSQ, TboxRepository.netState.value.csq)
            putExtra(EXTRA_NET_TYPE, TboxRepository.netState.value.netStatus)
            putExtra(EXTRA_TBOX_STATUS, TboxRepository.tboxConnected.value)
            putExtra(EXTRA_APN_STATUS, TboxRepository.apnState.value.apnStatus)
        }
        try {
            sendBroadcast(intent)
            Log.d("Widget", "Update sent: CSQ=${TboxRepository.netState.value.csq}, " +
                    "Connected=$TboxRepository.tboxConnected.value")
        } catch (e: Exception) {
            Log.e("Widget", "Failed to send broadcast", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        socket.close()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun sendUdpMessage(socket: DatagramSocket, port: Int, tid: Byte, sid: Byte, cmd: Byte, msg: ByteArray): Boolean {
        var message = ""
        val receiveData = ByteArray(1024)
        val receivePacket = DatagramPacket(receiveData, receiveData.size)

        var data = fillHeader(msg.size, tid, sid, cmd) + msg
        val checkSum = xorSum(data)
        data += checkSum
        try {
            val address = InetAddress.getByName(serverIp)
            val packet = DatagramPacket(data, data.size, address, port)
            socket.send(packet)
            message = "Отправлено: ${toHexString(data)}\n"
            try {
                socket.receive(receivePacket)
                message += responseWork(receivePacket)
            } catch (e: Exception) {
                message += "Ошибка приема сообщения\n"
                e.printStackTrace()
                TboxRepository.updateMessage(message)
                return false
            }
        } catch (e: Exception) {
            message += "Ошибка отправки сообщения\n"
            e.printStackTrace()
            TboxRepository.updateMessage(message)
            return false
        }
        TboxRepository.updateMessage(message)
        return true
    }

    private fun responseWork(receivePacket: DatagramPacket): String {
        var message = ""
        val fromAddress = receivePacket.address.hostAddress
        val fromPort = receivePacket.port
        if (checkPacket(receivePacket.data)) {
            val dataLength = extractDataLength(receivePacket.data)
            val receivedData = extractData(receivePacket.data, dataLength)
            if (!receivedData.contentEquals(ByteArray(0))) {
                val tid = receivePacket.data[9]
                val tids = String.format("%02X", tid)
                val sid = receivePacket.data[8]
                val sids = String.format("%02X", sid)
                val cmd = receivePacket.data[12]
                val cmds = String.format("%02X", cmd)
                if (receivedData.size >= 4) {
                    if (tid == 0x25.toByte()) {
                        if (sid == 0x37.toByte()) {
                            if (cmd == 0x87.toByte()) {
                                message = ansMDCNetState(receivedData)
                            } else if ((cmd == 0x90.toByte())) {
                                message = ansMDCAPNManage(receivedData)
                            } else if ((cmd == 0x91.toByte())) {
                                message = ansMDCAPNState(receivedData)
                            } else if ((cmd == 0x8E.toByte())) {
                                message = ansATcmd(receivedData)
                            } else {
                                message = "Неизвестный ответ от MDC\n"
                            }
                        } else {
                            message = "Неизвестный SID 0x$sids\n"
                        }
                    } else {
                        message = "Неизвестный TID 0x$tids\n"
                    }
                } else {
                    message = "Мало данных\n"
                }
                message += "Получено от $fromAddress:$fromPort: TID: 0x$tids, SID: 0x$sids, CMD: 0x$cmds - " +
                        "${toHexString(receivedData)}\n"
            } else {
                message += "Неверные данные 1\nПолучено от $fromAddress:$fromPort: " +
                        "${toHexString(receivePacket.data)}\n"
            }
        }
        else {
            message += "Неверные данные 2\nПолучено от $fromAddress:$fromPort: " +
                    "${toHexString(receivePacket.data)}\n"
        }
        return message
    }

    private fun ansMDCNetState(data: ByteArray): String {
        var message = ""
        if (data.copyOfRange(0, 4)
                .contentEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        ) {
            message = "Ошибка проверки состояния сети\n"
        }
        else if (data.copyOfRange(0, 4)
                .contentEquals(byteArrayOf(0xF4.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        ) {
            message = "Ошибка проверки состояния сети - неправильное состояние системы\n"
        }
        else if (data.copyOfRange(0, 4)
                .contentEquals(byteArrayOf(0xF5.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        ) {
            message = "Ошибка проверки состояния сети - неверный тип подписки\n"
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
                "SIM заблокирована"
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
        return message
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

    private fun ansMDCAPNManage(data: ByteArray): String {
        var message = ""
        if (data.copyOfRange(0, 4)
                .contentEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        ) {
            message = "Результат: Ошибка управления APN\n"
        }
        else if (data.copyOfRange(0, 4)
                .contentEquals(byteArrayOf(0xF4.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        ) {
            message = "Результат: Операция не разрешена в текущем состоянии APN\n"
        }
        else if (data.copyOfRange(0, 4)
                .contentEquals(byteArrayOf(0xF5.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        ) {
            message = "Результат: Неверный номер канала APN\n"
        }
        else if (data.copyOfRange(0, 4)
                .contentEquals(byteArrayOf(0, 0, 0, 0))
        ) {
            message = "Результат: Команда APN выполнена\n"
        }
        return message
    }

    private fun ansMDCAPNState(data: ByteArray): String {
        var message = ""
        if (data.copyOfRange(0, 4)
                .contentEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        ) {
            message = "Результат: Ошибка проверки состояния APN\n"
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
        }

        return message
    }

    private fun clearAPNStates() {
        TboxRepository.updateAPNState(
            APNState(
                apnIP = "-",
                apnStatus = "-",
                apnType = "-",
                apnGate = "-",
                apnDNS1 = "-",
                apnDNS2 = "-"
            )
        )
    }

    private fun clearAPN2States() {
        TboxRepository.updateAPN2State(
            APNState(
                apnIP = "-",
                apnStatus = "-",
                apnType = "-",
                apnGate = "-",
                apnDNS1 = "-",
                apnDNS2 = "-"
            )
        )
    }

    private fun ansATcmd(data: ByteArray): String {
        var message = ""
        if (data.copyOfRange(0, 4)
                .contentEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        ) {
            message = "Результат: Ошибка отправки AT-команды\n"
        }
        else {
            message = "Результат: AT команда выполнена\n" +
                    String(data.copyOfRange(10, data.size), charset = Charsets.UTF_8) + "\n"
        }
        return message
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
