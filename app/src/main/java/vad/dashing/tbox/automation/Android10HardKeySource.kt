package vad.dashing.tbox.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import vad.dashing.tbox.mbcan.Android10VhalRepository
import vad.dashing.tbox.mbcan.KeyPressDiagnosticFormat

/**
 * Feeds A10 steering-wheel broadcasts and door-panel VHAL values into
 * [AutomationTriggerHardKeyEventBus], the same bus widgets and automations already use.
 */
internal object Android10HardKeySource {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger(0)
    private val forwarder = AutomationHardKeyForwarder()
    private val doorPressed = mutableSetOf<Int>()
    private val doorLock = Any()
    @Volatile private var suppressVoiceUntilMillis = 0L
    @Volatile private var appContext: Context? = null
    private var receiver: BroadcastReceiver? = null

    fun install(context: Context) {
        appContext = context.applicationContext
    }

    fun setEnabled(enabled: Boolean) {
        val ticket = generation.incrementAndGet()
        if (!enabled) {
            Android10VhalRepository.stopDoorHardKeyTrackingAsync()
            main.post { if (generation.get() == ticket) unregisterReceiver() }
            synchronized(doorLock) { doorPressed.clear() }
            return
        }
        val context = appContext ?: return
        main.post {
            if (generation.get() == ticket) registerReceiver(context)
        }
        scope.launch {
            if (generation.get() != ticket) return@launch
            val started = Android10VhalRepository.startDoorHardKeyTracking { event ->
                if (generation.get() == ticket) onDoor(event.propertyId, event.value)
            }
            if (!started || generation.get() != ticket) {
                Android10VhalRepository.stopDoorHardKeyTrackingAsync()
            }
        }
    }

    private fun registerReceiver(context: Context) {
        if (receiver != null) return
        val next = object : BroadcastReceiver() {
            override fun onReceive(receiveContext: Context, intent: Intent) {
                onBroadcast(intent)
            }
        }
        val filter = IntentFilter().apply {
            addAction("adayo.keyEvent.onKeyDown")
            addAction("adayo.keyEvent.onKeyUp")
            addAction("adayo.keyEvent.onKeyLongPress")
            addAction("adayo.keyEvent.onSingleValue")
        }
        val registered = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(next, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(next, filter)
            }
        }
        if (registered.isSuccess) receiver = next
    }

    private fun unregisterReceiver() {
        val current = receiver ?: return
        receiver = null
        val context = appContext ?: return
        runCatching { context.unregisterReceiver(current) }
    }

    private fun onBroadcast(intent: Intent) {
        val status = Android10HardKeyDecode.wheelStatus(intent.action) ?: return
        if (!intent.hasExtra("InputKey")) return
        val inputKey = intent.getIntExtra("InputKey", 0)
        val now = System.currentTimeMillis()
        val doorHeld = synchronized(doorLock) { doorPressed.isNotEmpty() }
        if (Android10HardKeyDecode.suppressVoiceAssist(inputKey, doorHeld, now, suppressVoiceUntilMillis)) {
            return
        }
        val raw = status.rawValue ?: return
        forwarder.onHardKey(inputKey, raw)
    }

    private fun onDoor(propertyId: Int, value: Any?) {
        val keyCode = KeyPressDiagnosticFormat.vhalDoorKeyCode(propertyId) ?: return
        val numeric = when (value) {
            is Number -> value.toInt()
            is IntArray -> value.firstOrNull()
            else -> null
        } ?: return
        val status = Android10HardKeyDecode.doorStatus(numeric) ?: return
        val now = System.currentTimeMillis()
        synchronized(doorLock) {
            if (status == AutomationHardKeyStatus.PRESSED) {
                doorPressed.add(propertyId)
            } else if (doorPressed.remove(propertyId)) {
                suppressVoiceUntilMillis = now + Android10HardKeyDecode.VOICE_ASSIST_SUPPRESS_AFTER_DOOR_MS
            } else {
                return
            }
        }
        val raw = status.rawValue ?: return
        forwarder.onHardKey(keyCode, raw)
    }
}
