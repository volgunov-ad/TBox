package vad.dashing.tbox.adb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AdbRepository {

    const val ACTION_USB_PERMISSION = "vad.dashing.tbox.ADB_USB_PERMISSION"

    enum class Phase {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR,
    }

    enum class TransportType {
        TCP,
        USB,
    }

    data class State(
        val phase: Phase = Phase.DISCONNECTED,
        val transport: TransportType? = null,
        val endpoint: String = "",
        val banner: String = "",
        val error: String? = null,
    )

    data class UsbCandidate(
        val deviceId: Int,
        val name: String,
        val vendorId: Int,
        val productId: Int,
        val hasPermission: Boolean,
    )

    private const val MAX_LOG_LINES = 500
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val _state = MutableStateFlow(State())
    private val _usbCandidates = MutableStateFlow<List<UsbCandidate>>(emptyList())
    private val _consoleLog = MutableStateFlow<List<String>>(emptyList())

    val state: StateFlow<State> = _state.asStateFlow()
    val usbCandidates: StateFlow<List<UsbCandidate>> = _usbCandidates.asStateFlow()
    val consoleLog: StateFlow<List<String>> = _consoleLog.asStateFlow()

    @Volatile private var initialized = false
    private lateinit var appContext: Context
    private lateinit var usbManager: UsbManager
    private var connection: AdbConnection? = null
    private var connectedUsbDeviceId: Int? = null
    private var pendingUsbDeviceId: Int? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> refreshUsbDevices()
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = extraUsbDevice(intent)
                    refreshUsbDevices()
                    if (device != null && device.deviceId == connectedUsbDeviceId) {
                        scope.launch {
                            mutex.withLock {
                                closeConnection()
                                setError(TransportType.USB, device.deviceName, "USB device disconnected")
                            }
                        }
                    }
                }
                ACTION_USB_PERMISSION -> {
                    val device = extraUsbDevice(intent)
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    refreshUsbDevices()
                    if (device != null && device.deviceId == pendingUsbDeviceId) {
                        pendingUsbDeviceId = null
                        if (granted) {
                            connectUsb(device.deviceId)
                        } else {
                            setError(TransportType.USB, device.deviceName, "USB permission denied")
                        }
                    }
                }
            }
        }
    }

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
        val filter = IntentFilter(ACTION_USB_PERMISSION).apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appContext.registerReceiver(usbReceiver, filter)
        }
        initialized = true
        refreshUsbDevices()
    }

    fun refreshUsbDevices() {
        if (!initialized) return
        _usbCandidates.value = usbManager.deviceList.values
            .filter(AdbUsbTransport::isAdbDevice)
            .sortedBy { it.deviceName }
            .map {
                UsbCandidate(
                    deviceId = it.deviceId,
                    name = usbDisplayName(it),
                    vendorId = it.vendorId,
                    productId = it.productId,
                    hasPermission = usbManager.hasPermission(it),
                )
            }
    }

    fun requestUsbPermission(deviceId: Int) {
        if (!initialized) return
        val device = findUsbDevice(deviceId) ?: run {
            setError(TransportType.USB, "", "ADB USB device not found")
            return
        }
        if (usbManager.hasPermission(device)) {
            connectUsb(deviceId)
            return
        }
        pendingUsbDeviceId = deviceId
        _state.value = State(
            phase = Phase.CONNECTING,
            transport = TransportType.USB,
            endpoint = usbDisplayName(device),
        )
        appendLog("USB permission requested for ${usbDisplayName(device)}")
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val intent = PendingIntent.getBroadcast(
            appContext,
            deviceId,
            Intent(ACTION_USB_PERMISSION).setPackage(appContext.packageName),
            flags,
        )
        usbManager.requestPermission(device, intent)
    }

    fun connectTcp(host: String, port: Int) {
        if (!initialized) return
        scope.launch {
            mutex.withLock {
                closeConnection()
                val endpoint = "$host:$port"
                _state.value = State(Phase.CONNECTING, TransportType.TCP, endpoint)
                appendLog("Connecting to $endpoint")
                runCatching {
                    val transport = AdbTcpTransport.connect(host, port)
                    connectTransport(transport, TransportType.TCP, endpoint, null)
                }.onFailure {
                    closeConnection()
                    setError(TransportType.TCP, endpoint, it.message ?: it.javaClass.simpleName)
                }
            }
        }
    }

    fun connectUsb(deviceId: Int) {
        if (!initialized) return
        val device = findUsbDevice(deviceId) ?: run {
            setError(TransportType.USB, "", "ADB USB device not found")
            return
        }
        if (!usbManager.hasPermission(device)) {
            requestUsbPermission(deviceId)
            return
        }
        scope.launch {
            mutex.withLock {
                closeConnection()
                val endpoint = usbDisplayName(device)
                _state.value = State(Phase.CONNECTING, TransportType.USB, endpoint)
                appendLog("Connecting to USB $endpoint")
                runCatching {
                    val transport = AdbUsbTransport.open(usbManager, device)
                    connectTransport(transport, TransportType.USB, endpoint, deviceId)
                }.onFailure {
                    closeConnection()
                    setError(TransportType.USB, endpoint, it.message ?: it.javaClass.simpleName)
                }
            }
        }
    }

    fun disconnect() {
        scope.launch {
            mutex.withLock {
                val endpoint = _state.value.endpoint
                closeConnection()
                _state.value = State()
                if (endpoint.isNotEmpty()) appendLog("Disconnected from $endpoint")
            }
        }
    }

    fun execute(command: String) {
        val normalized = command.trim()
        if (normalized.isEmpty()) return
        scope.launch {
            mutex.withLock {
                val active = connection
                if (active == null || _state.value.phase != Phase.CONNECTED) {
                    appendLog("Not connected")
                    return@withLock
                }
                appendLog("$ $normalized")
                runCatching { active.execute(normalized) }
                    .onSuccess { result ->
                        appendOutput(result.stdout)
                        appendOutput(result.stderr)
                        result.exitCode?.let { appendLog("Exit code: $it") }
                    }
                    .onFailure {
                        val previous = _state.value
                        closeConnection()
                        setError(
                            previous.transport,
                            previous.endpoint,
                            it.message ?: it.javaClass.simpleName,
                        )
                    }
            }
        }
    }

    fun clearLog() {
        _consoleLog.value = emptyList()
    }

    private fun connectTransport(
        transport: AdbTransport,
        type: TransportType,
        endpoint: String,
        usbDeviceId: Int?,
    ) {
        val newConnection = AdbConnection(
            transport,
            AdbAuthKeys.loadOrCreate(appContext.filesDir.resolve("adb")),
            "tbox@${Build.MODEL}",
        )
        try {
            val info = newConnection.connect()
            connection = newConnection
            connectedUsbDeviceId = usbDeviceId
            _state.value = State(
                phase = Phase.CONNECTED,
                transport = type,
                endpoint = endpoint,
                banner = info.banner,
            )
            appendLog("Connected to $endpoint")
        } catch (error: Exception) {
            runCatching { newConnection.close() }
            throw error
        }
    }

    private fun closeConnection() {
        val previous = connection
        connection = null
        connectedUsbDeviceId = null
        runCatching { previous?.close() }
    }

    private fun setError(type: TransportType?, endpoint: String, message: String) {
        _state.value = State(
            phase = Phase.ERROR,
            transport = type,
            endpoint = endpoint,
            error = message,
        )
        appendLog("Error: $message")
    }

    private fun appendOutput(output: String) {
        if (output.isEmpty()) return
        output.replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .dropLastWhile { it.isEmpty() }
            .forEach(::appendLog)
    }

    private fun appendLog(message: String) {
        val time = synchronized(timeFormat) { timeFormat.format(Date()) }
        _consoleLog.value = (_consoleLog.value + "[$time] $message").takeLast(MAX_LOG_LINES)
    }

    private fun findUsbDevice(deviceId: Int): UsbDevice? =
        usbManager.deviceList.values.firstOrNull {
            it.deviceId == deviceId && AdbUsbTransport.isAdbDevice(it)
        }

    private fun usbDisplayName(device: UsbDevice): String {
        val product = runCatching { device.productName }.getOrNull()?.takeIf { it.isNotBlank() }
        return product ?: device.deviceName
    }

    private fun extraUsbDevice(intent: Intent): UsbDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
}
