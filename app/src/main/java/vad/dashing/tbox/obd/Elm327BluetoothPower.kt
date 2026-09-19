package vad.dashing.tbox.obd

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Helpers to read / enable classic Bluetooth for ELM327 on the HU (API 28 primarily).
 */
@SuppressLint("MissingPermission")
object Elm327BluetoothPower {
    private const val TAG = "Elm327BtPower"

    fun adapterOrNull(): BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    fun isEnabled(): Boolean = adapterOrNull()?.isEnabled == true

    /**
     * Request Bluetooth on and wait until [BluetoothAdapter.STATE_ON] or [timeoutMs].
     * Returns true when Bluetooth is on.
     */
    @SuppressLint("MissingPermission")
    suspend fun ensureEnabled(context: Context, timeoutMs: Long = 20_000L): Boolean =
        withContext(Dispatchers.Main) {
            val adapter = adapterOrNull() ?: return@withContext false
            if (adapter.isEnabled) return@withContext true
            if (!canToggleBluetooth(context)) {
                Log.w(TAG, "missing permission to enable Bluetooth")
                return@withContext false
            }
            val enabled = runCatching { adapter.enable() }.getOrDefault(false)
            if (!enabled && !adapter.isEnabled) {
                Log.w(TAG, "BluetoothAdapter.enable() returned false")
                return@withContext adapter.isEnabled
            }
            if (adapter.isEnabled) return@withContext true
            waitUntilState(context, BluetoothAdapter.STATE_ON, timeoutMs)
            adapter.isEnabled
        }

    @SuppressLint("MissingPermission")
    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        val adapter = adapterOrNull() ?: return false
        if (!canToggleBluetooth(context)) return false
        return if (enabled) {
            if (adapter.isEnabled) true
            else runCatching { adapter.enable() }.getOrDefault(false)
        } else {
            if (!adapter.isEnabled) true
            else runCatching { adapter.disable() }.getOrDefault(false)
        }
    }

    fun canToggleBluetooth(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun enabledFlow(context: Context): Flow<Boolean> = callbackFlow {
        val adapter = adapterOrNull()
        trySend(adapter?.isEnabled == true)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
                val state = intent.getIntExtra(
                    BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR,
                )
                when (state) {
                    BluetoothAdapter.STATE_ON -> trySend(true)
                    BluetoothAdapter.STATE_OFF -> trySend(false)
                    BluetoothAdapter.STATE_TURNING_ON,
                    BluetoothAdapter.STATE_TURNING_OFF,
                    -> Unit
                }
            }
        }
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    private suspend fun waitUntilState(
        context: Context,
        targetState: Int,
        timeoutMs: Long,
    ): Boolean {
        val done = CompletableDeferred<Boolean>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
                val state = intent.getIntExtra(
                    BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR,
                )
                if (state == targetState) {
                    done.complete(true)
                } else if (state == BluetoothAdapter.STATE_OFF &&
                    targetState == BluetoothAdapter.STATE_ON
                ) {
                    // Still turning on from enable(); ignore OFF if we just requested ON.
                }
            }
        }
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        return try {
            withTimeoutOrNull(timeoutMs) { done.await() } == true
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
}
