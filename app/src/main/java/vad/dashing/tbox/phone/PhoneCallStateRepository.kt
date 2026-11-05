package vad.dashing.tbox.phone

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import vad.dashing.tbox.BLUETOOTH_PROFILE_HEADSET_CLIENT
import vad.dashing.tbox.hasBluetoothConnectPermission
import vad.dashing.tbox.isHfpClientConnected
import vad.dashing.tbox.normalizeDialPhoneNumber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Observes HFP Client call state from the app process via reflection on
 * `BluetoothHeadsetClient` / `BluetoothHeadsetClientCall` (system APIs) and
 * best-effort Telecom answer/hangup.
 *
 * Capability flags ([PhoneCallSnapshot.answerSupported] / [PhoneCallSnapshot.hangupSupported])
 * are true only when the reflected methods resolve; runtime success is still not guaranteed
 * without default-dialer / privileged rights — callers must treat failures as real.
 */
object PhoneCallStateRepository {
    private const val TAG = "PhoneCallState"

    private val started = AtomicBoolean(false)
    private val _snapshot = MutableStateFlow(PhoneCallSnapshot())
    val snapshot: StateFlow<PhoneCallSnapshot> = _snapshot.asStateFlow()

    val hfpConnected = snapshot.map { it.hfpConnected }.distinctUntilChanged()
    val callState = snapshot.map { it.callState }.distinctUntilChanged()
    val callDirection = snapshot.map { it.direction }.distinctUntilChanged()
    val callNumber = snapshot.map { it.number }.distinctUntilChanged()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var headsetClientProxy: Any? = null

    @Volatile
    private var answerViaHeadsetClient: Boolean = false

    @Volatile
    private var hangupViaHeadsetClient: Boolean = false

    @Volatile
    private var answerViaTelecom: Boolean = false

    @Volatile
    private var hangupViaTelecom: Boolean = false

    private var callChangedReceiver: BroadcastReceiver? = null
    private var connectionReceiver: BroadcastReceiver? = null

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile != BLUETOOTH_PROFILE_HEADSET_CLIENT || proxy == null) return
            headsetClientProxy = proxy
            probeCapabilities()
            refreshFromProxy()
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BLUETOOTH_PROFILE_HEADSET_CLIENT) return
            headsetClientProxy = null
            publish {
                it.copy(
                    hfpConnected = false,
                    callState = if (it.observerActive) PhoneCallState.IDLE else PhoneCallState.UNKNOWN,
                    direction = PhoneCallDirection.NONE,
                    answerSupported = answerCapability(),
                    hangupSupported = hangupCapability(),
                )
            }
        }
    }

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        appContext = context.applicationContext
        probeCapabilities()
        publish {
            it.copy(
                hfpConnected = isHfpClientConnected(),
                callState = PhoneCallState.UNKNOWN,
                answerSupported = answerCapability(),
                hangupSupported = hangupCapability(),
                observerActive = false,
            )
        }
        bindHeadsetClient()
        registerReceivers()
        refreshFromProxy()
    }

    fun stop() {
        if (!started.get()) return
        val ctx = appContext
        callChangedReceiver?.let { receiver ->
            runCatching { ctx?.unregisterReceiver(receiver) }
        }
        connectionReceiver?.let { receiver ->
            runCatching { ctx?.unregisterReceiver(receiver) }
        }
        callChangedReceiver = null
        connectionReceiver = null
        val adapter = runCatching { BluetoothAdapter.getDefaultAdapter() }.getOrNull()
        val proxy = headsetClientProxy as? BluetoothProfile
        if (adapter != null && proxy != null) {
            runCatching { adapter.closeProfileProxy(BLUETOOTH_PROFILE_HEADSET_CLIENT, proxy) }
        }
        headsetClientProxy = null
        started.set(false)
        publish {
            PhoneCallSnapshot(
                answerSupported = answerCapability(),
                hangupSupported = hangupCapability(),
            )
        }
    }

    fun answerIncomingCall(): PhoneCallControlResult {
        if (!answerCapability()) {
            return PhoneCallControlResult.Failure(
                "Ответ на звонок недоступен на этой прошивке / без привилегий",
            )
        }
        val snap = _snapshot.value
        if (snap.callState != PhoneCallState.INCOMING) {
            return PhoneCallControlResult.Failure("Нет входящего звонка")
        }
        if (answerViaHeadsetClient) {
            val ok = invokeHeadsetClientAnswer()
            if (ok) return PhoneCallControlResult.Success
        }
        if (answerViaTelecom) {
            val ok = invokeTelecomAccept()
            if (ok) return PhoneCallControlResult.Success
        }
        return PhoneCallControlResult.Failure("Не удалось ответить на звонок")
    }

    fun hangupCall(): PhoneCallControlResult {
        if (!hangupCapability()) {
            return PhoneCallControlResult.Failure(
                "Сброс звонка недоступен на этой прошивке / без привилегий",
            )
        }
        val snap = _snapshot.value
        if (snap.callState == PhoneCallState.IDLE || snap.callState == PhoneCallState.UNKNOWN) {
            return PhoneCallControlResult.Failure("Нет активного звонка")
        }
        if (hangupViaHeadsetClient) {
            val ok = invokeHeadsetClientHangup()
            if (ok) return PhoneCallControlResult.Success
        }
        if (hangupViaTelecom) {
            val ok = invokeTelecomEndCall()
            if (ok) return PhoneCallControlResult.Success
        }
        return PhoneCallControlResult.Failure("Не удалось сбросить звонок")
    }

    private fun answerCapability(): Boolean = answerViaHeadsetClient || answerViaTelecom

    private fun hangupCapability(): Boolean = hangupViaHeadsetClient || hangupViaTelecom

    private fun probeCapabilities() {
        val clientClass = runCatching {
            Class.forName("android.bluetooth.BluetoothHeadsetClient")
        }.getOrNull()
        if (clientClass != null) {
            answerViaHeadsetClient = runCatching {
                clientClass.getMethod(
                    "acceptCall",
                    BluetoothDevice::class.java,
                    Int::class.javaPrimitiveType,
                )
                true
            }.getOrDefault(false) || runCatching {
                clientClass.getMethod("acceptCall", BluetoothDevice::class.java)
                true
            }.getOrDefault(false)

            hangupViaHeadsetClient = runCatching {
                clientClass.getMethod("terminateCall", BluetoothDevice::class.java, Int::class.javaPrimitiveType)
                true
            }.getOrDefault(false) || runCatching {
                clientClass.getMethod(
                    "terminateCall",
                    BluetoothDevice::class.java,
                    Class.forName("android.bluetooth.BluetoothHeadsetClientCall"),
                )
                true
            }.getOrDefault(false) || runCatching {
                clientClass.getMethod("rejectCall", BluetoothDevice::class.java)
                true
            }.getOrDefault(false)
        }

        val telecomClass = TelecomManager::class.java
        answerViaTelecom = runCatching {
            telecomClass.getMethod("acceptRingingCall")
            true
        }.getOrDefault(false)
        hangupViaTelecom = runCatching {
            telecomClass.getMethod("endCall")
            true
        }.getOrDefault(false)
    }

    private fun bindHeadsetClient() {
        val ctx = appContext ?: return
        if (!hasBluetoothConnectPermission(ctx)) {
            Log.i(TAG, "BLUETOOTH_CONNECT missing; HFP observer deferred")
            return
        }
        val adapter = runCatching { BluetoothAdapter.getDefaultAdapter() }.getOrNull() ?: return
        runCatching {
            adapter.getProfileProxy(ctx, profileListener, BLUETOOTH_PROFILE_HEADSET_CLIENT)
        }.onFailure { error ->
            Log.w(TAG, "getProfileProxy(HEADSET_CLIENT) failed: ${error.message}")
        }
    }

    private fun registerReceivers() {
        val ctx = appContext ?: return
        val callAction = headsetClientAction("ACTION_CALL_CHANGED")
            ?: "android.bluetooth.headsetclient.profile.action.AG_CALL_CHANGED"
        val connAction = headsetClientAction("ACTION_CONNECTION_STATE_CHANGED")
            ?: "android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED"

        callChangedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                refreshFromProxy(intent)
            }
        }
        connectionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                publish {
                    it.copy(
                        hfpConnected = isHfpClientConnected(),
                        answerSupported = answerCapability(),
                        hangupSupported = hangupCapability(),
                    )
                }
                refreshFromProxy()
            }
        }
        val callFilter = IntentFilter(callAction)
        val connFilter = IntentFilter(connAction)
        // Also listen to adapter ACL as a coarse HFP hint.
        connFilter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                ctx.registerReceiver(callChangedReceiver, callFilter, Context.RECEIVER_NOT_EXPORTED)
                ctx.registerReceiver(connectionReceiver, connFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                ctx.registerReceiver(callChangedReceiver, callFilter)
                @Suppress("DEPRECATION")
                ctx.registerReceiver(connectionReceiver, connFilter)
            }
        }.onFailure { error ->
            Log.w(TAG, "registerReceiver failed: ${error.message}")
        }
    }

    private fun headsetClientAction(fieldName: String): String? =
        runCatching {
            Class.forName("android.bluetooth.BluetoothHeadsetClient")
                .getField(fieldName)
                .get(null) as? String
        }.getOrNull()

    private fun refreshFromProxy(intent: Intent? = null) {
        val proxy = headsetClientProxy
        val hfp = isHfpClientConnected()
        if (proxy == null) {
            publish {
                it.copy(
                    hfpConnected = hfp,
                    callState = if (it.observerActive) PhoneCallState.IDLE else PhoneCallState.UNKNOWN,
                    direction = if (it.observerActive) PhoneCallDirection.NONE else it.direction,
                    answerSupported = answerCapability(),
                    hangupSupported = hangupCapability(),
                    observerActive = false,
                )
            }
            // Still try to parse EXTRA_CALL from the broadcast without a proxy.
            intent?.let { applyCallExtra(it, hfpConnected = hfp, observerActive = false) }
            return
        }

        val calls = readCurrentCalls(proxy)
        if (calls.isEmpty()) {
            val fromIntent = intent?.let { parseCallExtra(it) }
            if (fromIntent != null) {
                applyParsedCall(fromIntent, hfpConnected = hfp, observerActive = true)
            } else {
                publish {
                    it.copy(
                        hfpConnected = hfp,
                        callState = PhoneCallState.IDLE,
                        direction = PhoneCallDirection.NONE,
                        number = it.number, // keep last number
                        answerSupported = answerCapability(),
                        hangupSupported = hangupCapability(),
                        observerActive = true,
                    )
                }
            }
            return
        }

        // Prefer INCOMING > ACTIVE > DIALING/ALERTING > HELD > first.
        val preferred = pickPreferredCall(calls)
        applyParsedCall(preferred, hfpConnected = hfp, observerActive = true)
    }

    private fun applyCallExtra(intent: Intent, hfpConnected: Boolean, observerActive: Boolean) {
        val parsed = parseCallExtra(intent) ?: return
        applyParsedCall(parsed, hfpConnected = hfpConnected, observerActive = observerActive)
    }

    private fun applyParsedCall(
        parsed: ParsedCall,
        hfpConnected: Boolean,
        observerActive: Boolean,
    ) {
        val number = normalizeDialPhoneNumber(parsed.number).ifBlank { parsed.number.trim() }
        publish {
            it.copy(
                hfpConnected = hfpConnected,
                callState = parsed.state,
                direction = parsed.direction,
                number = if (number.isNotBlank()) number else it.number,
                answerSupported = answerCapability(),
                hangupSupported = hangupCapability(),
                observerActive = observerActive,
            )
        }
    }

    private data class ParsedCall(
        val state: PhoneCallState,
        val direction: PhoneCallDirection,
        val number: String,
        val rawCall: Any? = null,
        val device: BluetoothDevice? = null,
    )

    private fun pickPreferredCall(calls: List<ParsedCall>): ParsedCall {
        fun rank(state: PhoneCallState): Int = when (state) {
            PhoneCallState.INCOMING -> 5
            PhoneCallState.ACTIVE -> 4
            PhoneCallState.DIALING -> 3
            PhoneCallState.HELD -> 2
            PhoneCallState.IDLE -> 1
            PhoneCallState.UNKNOWN -> 0
        }
        return calls.maxByOrNull { rank(it.state) } ?: calls.first()
    }

    private fun readCurrentCalls(proxy: Any): List<ParsedCall> {
        val devices = runCatching {
            @Suppress("UNCHECKED_CAST")
            (proxy as BluetoothProfile).connectedDevices as? List<BluetoothDevice>
        }.getOrNull().orEmpty()
        if (devices.isEmpty()) return emptyList()

        val results = ArrayList<ParsedCall>()
        for (device in devices) {
            val rawCalls = runCatching {
                proxy.javaClass.getMethod("getCurrentCalls", BluetoothDevice::class.java)
                    .invoke(proxy, device) as? List<*>
            }.getOrNull().orEmpty()
            for (raw in rawCalls) {
                if (raw == null) continue
                results += parseCallObject(raw, device)
            }
        }
        return results
    }

    private fun parseCallExtra(intent: Intent): ParsedCall? {
        val extraKeys = listOf(
            "android.bluetooth.headsetclient.extra.CALL",
            runCatching {
                Class.forName("android.bluetooth.BluetoothHeadsetClient")
                    .getField("EXTRA_CALL").get(null) as? String
            }.getOrNull(),
        ).filterNotNull().distinct()
        for (key in extraKeys) {
            @Suppress("DEPRECATION")
            val raw = intent.extras?.get(key) ?: continue
            return parseCallObject(raw, device = null)
        }
        return null
    }

    private fun parseCallObject(raw: Any, device: BluetoothDevice?): ParsedCall {
        val stateInt = runCatching {
            raw.javaClass.getMethod("getState").invoke(raw) as? Int
        }.getOrNull()
        val number = runCatching {
            raw.javaClass.getMethod("getNumber").invoke(raw) as? String
        }.getOrNull().orEmpty()
        val outgoing = runCatching {
            raw.javaClass.getMethod("isOutgoing").invoke(raw) as? Boolean
        }.getOrNull()

        // BluetoothHeadsetClientCall states (AOSP):
        // 0 ACTIVE, 1 HELD, 2 DIALING, 3 ALERTING, 4 INCOMING, 5 WAITING, 6 HELD_BY_RESPONSE_AND_HOLD, 7 TERMINATED
        val state = when (stateInt) {
            0 -> PhoneCallState.ACTIVE
            1, 6 -> PhoneCallState.HELD
            2, 3 -> PhoneCallState.DIALING
            4, 5 -> PhoneCallState.INCOMING
            7 -> PhoneCallState.IDLE
            else -> PhoneCallState.UNKNOWN
        }
        val direction = when {
            state == PhoneCallState.IDLE -> PhoneCallDirection.NONE
            outgoing == true -> PhoneCallDirection.OUTGOING
            outgoing == false || state == PhoneCallState.INCOMING -> PhoneCallDirection.INCOMING
            else -> PhoneCallDirection.NONE
        }
        return ParsedCall(
            state = state,
            direction = direction,
            number = number,
            rawCall = raw,
            device = device,
        )
    }

    private fun invokeHeadsetClientAnswer(): Boolean {
        val proxy = headsetClientProxy ?: return false
        val device = firstConnectedDevice(proxy) ?: return false
        // acceptCall(device, flag) — flag 0 = accept audio
        val withFlag = runCatching {
            proxy.javaClass.getMethod(
                "acceptCall",
                BluetoothDevice::class.java,
                Int::class.javaPrimitiveType,
            ).invoke(proxy, device, 0)
            true
        }.getOrDefault(false)
        if (withFlag) return true
        return runCatching {
            proxy.javaClass.getMethod("acceptCall", BluetoothDevice::class.java)
                .invoke(proxy, device)
            true
        }.getOrDefault(false)
    }

    private fun invokeHeadsetClientHangup(): Boolean {
        val proxy = headsetClientProxy ?: return false
        val device = firstConnectedDevice(proxy) ?: return false
        val calls = readCurrentCalls(proxy)
        val active = pickPreferredCall(calls)
        // terminateCall(device, call) preferred
        if (active.rawCall != null) {
            val ok = runCatching {
                val callClass = Class.forName("android.bluetooth.BluetoothHeadsetClientCall")
                proxy.javaClass.getMethod(
                    "terminateCall",
                    BluetoothDevice::class.java,
                    callClass,
                ).invoke(proxy, device, active.rawCall)
                true
            }.getOrDefault(false)
            if (ok) return true
        }
        val byIndex = runCatching {
            proxy.javaClass.getMethod(
                "terminateCall",
                BluetoothDevice::class.java,
                Int::class.javaPrimitiveType,
            ).invoke(proxy, device, 0)
            true
        }.getOrDefault(false)
        if (byIndex) return true
        if (active.state == PhoneCallState.INCOMING) {
            return runCatching {
                proxy.javaClass.getMethod("rejectCall", BluetoothDevice::class.java)
                    .invoke(proxy, device)
                true
            }.getOrDefault(false)
        }
        return false
    }

    private fun invokeTelecomAccept(): Boolean {
        val ctx = appContext ?: return false
        val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return false
        return runCatching {
            tm.javaClass.getMethod("acceptRingingCall").invoke(tm)
            true
        }.getOrDefault(false)
    }

    private fun invokeTelecomEndCall(): Boolean {
        val ctx = appContext ?: return false
        val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return false
        return runCatching {
            tm.javaClass.getMethod("endCall").invoke(tm) as? Boolean ?: true
        }.getOrDefault(false)
    }

    private fun firstConnectedDevice(proxy: Any): BluetoothDevice? =
        runCatching {
            (proxy as BluetoothProfile).connectedDevices.firstOrNull()
        }.getOrNull()

    private fun publish(transform: (PhoneCallSnapshot) -> PhoneCallSnapshot) {
        _snapshot.value = transform(_snapshot.value)
    }
}
