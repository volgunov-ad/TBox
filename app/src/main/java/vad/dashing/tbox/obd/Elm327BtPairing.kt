package vad.dashing.tbox.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Classic Bluetooth bonding helpers for ELM327 adapters (API ≤ 30 mainly;
 * on API 31+ still tries [BluetoothDevice.createBond] when CONNECT is granted).
 */
object Elm327BtPairing {
    private const val TAG = "Elm327BtPairing"
    private const val BOND_TIMEOUT_MS = 35_000L
    private val DEFAULT_PINS = listOf("1234", "0000", "6789", "8888")

    @SuppressLint("MissingPermission")
    fun isBonded(address: String): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        val mac = address.trim()
        if (mac.isEmpty()) return false
        return runCatching {
            adapter.bondedDevices.any { it.address.equals(mac, ignoreCase = true) }
        }.getOrDefault(false)
    }

    /**
     * Ensure [address] is bonded. Uses [preferredPin] when non-blank, otherwise tries
     * common ELM pins.
     *
     * @return [BondResult] with success flag and the PIN that worked when bonding was
     * performed in this call (null if already bonded / PIN unknown).
     */
    @SuppressLint("MissingPermission")
    suspend fun ensureBonded(
        context: Context,
        address: String,
        preferredPin: String,
    ): BondResult = withContext(Dispatchers.IO) {
        val mac = address.trim().uppercase()
        if (mac.isEmpty()) return@withContext BondResult(success = false, usedPin = null)
        if (isBonded(mac)) return@withContext BondResult(success = true, usedPin = null)

        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@withContext BondResult(false, null)
        if (!adapter.isEnabled) return@withContext BondResult(false, null)
        val device = try {
            adapter.getRemoteDevice(mac)
        } catch (_: IllegalArgumentException) {
            return@withContext BondResult(false, null)
        }

        when (device.bondState) {
            BluetoothDevice.BOND_BONDED -> return@withContext BondResult(true, null)
            BluetoothDevice.BOND_BONDING -> {
                val ok = awaitBondResult(context, mac) == true
                return@withContext BondResult(success = ok, usedPin = null)
            }
        }

        val pinsToTry = buildList {
            val preferred = preferredPin.trim()
            if (preferred.isNotEmpty()) add(preferred)
            DEFAULT_PINS.filterTo(this) { it != preferred }
        }

        for (pin in pinsToTry) {
            Log.i(TAG, "createBond $mac pinLen=${pin.length}")
            val bonded = bondOnce(context, device, mac, pin)
            if (bonded || isBonded(mac)) {
                return@withContext BondResult(success = true, usedPin = pin)
            }
        }
        BondResult(success = false, usedPin = null)
    }

    data class BondResult(
        val success: Boolean,
        /** PIN that succeeded during this attempt; null if already bonded or unknown. */
        val usedPin: String?,
    )

    @SuppressLint("MissingPermission")
    private suspend fun bondOnce(
        context: Context,
        device: BluetoothDevice,
        mac: String,
        pin: String,
    ): Boolean {
        applyPinHints(device, pin)
        val deferred = CompletableDeferred<Boolean>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                        val d = intent.bluetoothDeviceExtra() ?: return
                        if (!d.address.equals(mac, ignoreCase = true)) return
                        applyPinHints(d, pin)
                        abortBroadcast()
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val d = intent.bluetoothDeviceExtra() ?: return
                        if (!d.address.equals(mac, ignoreCase = true)) return
                        when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                            BluetoothDevice.BOND_BONDED -> deferred.complete(true)
                            BluetoothDevice.BOND_NONE -> {
                                val prev = intent.getIntExtra(
                                    BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                                    BluetoothDevice.ERROR,
                                )
                                if (prev == BluetoothDevice.BOND_BONDING || prev == BluetoothDevice.BOND_NONE) {
                                    deferred.complete(false)
                                }
                            }
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
            priority = IntentFilter.SYSTEM_HIGH_PRIORITY
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(
            context.applicationContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
        return try {
            val started = runCatching { device.createBond() }.getOrDefault(false)
            if (!started && device.bondState == BluetoothDevice.BOND_NONE) {
                Log.w(TAG, "createBond returned false for $mac")
            }
            withTimeoutOrNull(BOND_TIMEOUT_MS) { deferred.await() } == true
        } finally {
            runCatching {
                context.applicationContext.unregisterReceiver(receiver)
            }
        }
    }

    private suspend fun awaitBondResult(context: Context, mac: String): Boolean? {
        val deferred = CompletableDeferred<Boolean>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
                val d = intent.bluetoothDeviceExtra() ?: return
                if (!d.address.equals(mac, ignoreCase = true)) return
                when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                    BluetoothDevice.BOND_BONDED -> deferred.complete(true)
                    BluetoothDevice.BOND_NONE -> deferred.complete(false)
                }
            }
        }
        ContextCompat.registerReceiver(
            context.applicationContext,
            receiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        return try {
            withTimeoutOrNull(BOND_TIMEOUT_MS) { deferred.await() }
        } finally {
            runCatching {
                context.applicationContext.unregisterReceiver(receiver)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun applyPinHints(device: BluetoothDevice, pin: String) {
        if (pin.isEmpty()) return
        val pinBytes = pin.toByteArray(Charsets.UTF_8)
        runCatching {
            device.javaClass.getMethod("setPin", ByteArray::class.java).invoke(device, pinBytes)
        }
        runCatching {
            device.javaClass
                .getMethod("setPairingConfirmation", Boolean::class.javaPrimitiveType)
                .invoke(device, true)
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.bluetoothDeviceExtra(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
}
