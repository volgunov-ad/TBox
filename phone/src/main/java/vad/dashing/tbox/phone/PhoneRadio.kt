package vad.dashing.tbox.phone

import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import vad.dashing.tbox.phoneble.PhoneBleCodec

/**
 * All calls are made from the main thread. Bluetooth may be switched off or the
 * permission revoked at any time; the radio calls then fail quietly instead of crashing.
 */
class PhoneRadio(
    context: Context,
    private val onSnap: (PhoneBleCodec.Snapshot) -> Unit,
) {
    private val manager = context.getSystemService(BluetoothManager::class.java)
    private val uuid = ParcelUuid(PhoneBleCodec.SERVICE_UUID)
    private var snapshot = PhoneBleCodec.Snapshot()
    private var scanning = false

    // The adapter returns null LE objects while Bluetooth is off, so they are not cached.
    private val advertiser: BluetoothLeAdvertiser?
        get() = runCatching { manager?.adapter?.takeIf { it.isEnabled }?.bluetoothLeAdvertiser }.getOrNull()
    private val scanner: BluetoothLeScanner?
        get() = runCatching { manager?.adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner }.getOrNull()

    val canAdvertise: Boolean
        get() = runCatching { manager?.adapter?.isMultipleAdvertisementSupported == true }.getOrDefault(false)

    val bluetoothOn: Boolean
        get() = runCatching { manager?.adapter?.isEnabled == true }.getOrDefault(false)

    private val advertiseCallback = object : AdvertiseCallback() {}

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val payload = result.scanRecord?.getServiceData(uuid) ?: return
            val open = PhoneBleCodec.open(storeKey, payload) ?: return
            if (open.type != PhoneBleCodec.TYPE_SNAP) return
            if (open.counter != waitingCounter) return
            snapshot = PhoneBleCodec.overlaySnapshot(snapshot, open.body)
            onSnap(snapshot)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "scan failed: $errorCode")
            scanning = false
        }
    }

    var storeKey: ByteArray = ByteArray(PhoneBleCodec.KEY_LEN)
    var waitingCounter: Long = -1L

    fun startScan() {
        if (scanning) return
        val radio = scanner ?: return
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        // Without a filter Android stops delivering results while the screen is off.
        val filters = listOf(ScanFilter.Builder().setServiceData(uuid, ByteArray(0)).build())
        scanning = guarded("startScan") { radio.startScan(filters, settings, scanCallback) }
    }

    fun stopScan() {
        if (!scanning) return
        scanning = false
        val radio = scanner ?: return
        guarded("stopScan") { radio.stopScan(scanCallback) }
    }

    /** Restarts the scan after Bluetooth was toggled. */
    fun ensureScan() {
        if (!bluetoothOn) {
            scanning = false
            return
        }
        startScan()
    }

    fun advertise(payload: ByteArray): Boolean {
        val radio = advertiser ?: return false
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()
        // Service payload is at most 24 bytes so flags plus the AD header stay inside 31.
        val data = AdvertiseData.Builder()
            .addServiceData(uuid, payload)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()
        return guarded("advertise") {
            radio.stopAdvertising(advertiseCallback)
            radio.startAdvertising(settings, data, advertiseCallback)
        }
    }

    fun stopAdvertise() {
        val radio = advertiser ?: return
        guarded("stopAdvertise") { radio.stopAdvertising(advertiseCallback) }
    }

    private inline fun guarded(what: String, block: () -> Unit): Boolean =
        try {
            block()
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "$what: no permission", e)
            false
        } catch (e: IllegalStateException) {
            // Thrown when the adapter turns off between the check and the call.
            Log.w(TAG, "$what: adapter off", e)
            false
        }

    private companion object {
        const val TAG = "PhoneRadio"
    }
}
