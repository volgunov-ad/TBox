package vad.dashing.tbox.phone

import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import vad.dashing.tbox.phoneble.PhoneBleCodec

class PhoneRadio(
    context: Context,
    private val onSnap: (PhoneBleCodec.Snapshot) -> Unit,
) {
    private val manager = context.getSystemService(BluetoothManager::class.java)
    private val adapter = manager?.adapter
    private val advertiser = adapter?.bluetoothLeAdvertiser
    private val scanner = adapter?.bluetoothLeScanner
    private val uuid = ParcelUuid(PhoneBleCodec.SERVICE_UUID)
    private var snapshot = PhoneBleCodec.Snapshot()
    private var scanning = false

    val canAdvertise: Boolean get() = advertiser != null

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
    }

    var storeKey: ByteArray = ByteArray(PhoneBleCodec.KEY_LEN)
    var waitingCounter: Long = -1L

    fun startScan() {
        if (scanning || scanner == null) return
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, settings, scanCallback)
        scanning = true
    }

    fun stopScan() {
        if (!scanning || scanner == null) return
        scanner.stopScan(scanCallback)
        scanning = false
    }

    fun advertise(payload: ByteArray) {
        val radio = advertiser ?: return
        radio.stopAdvertising(advertiseCallback)
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
        radio.startAdvertising(settings, data, advertiseCallback)
    }

    fun stopAdvertise() {
        advertiser?.stopAdvertising(advertiseCallback)
    }
}
