package vad.dashing.tbox.drsensor

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import android.util.Log
import vad.dashing.tbox.drsensor.adayo.AdayoAcceleratorInfo
import vad.dashing.tbox.drsensor.adayo.AdayoGyroInfo
import vad.dashing.tbox.drsensor.adayo.AdayoMountAngleInfo
import vad.dashing.tbox.drsensor.adayo.AdayoPulseInfo

/**
 * A10 Adayo Navi DR via ServiceManager binder `adayo.service.navi.v2.0`
 * (registerINaviDrCallback + openDrData). May fail on unsigned apps.
 */
class A10NaviDrBackend : DrSensorBackend {
    override val source: DrSensorSource = DrSensorSource.A10_NAVI_DR

    private var service: IBinder? = null
    private var callback: DrCallbackBinder? = null
    private var onUpdate: ((DrSensorSnapshot) -> Unit)? = null

    private var gyroYaw: Float? = null
    private var gyroPitch: Float? = null
    private var gyroRoll: Float? = null
    private var gyroTemp: Float? = null
    private var accelX: Float? = null
    private var accelY: Float? = null
    private var accelZ: Float? = null
    private var pulseValue: Float? = null
    private var pulseGear: Int? = null
    private var mountExist: Boolean? = null
    private var mountYaw: Float? = null
    private var mountPitch: Float? = null
    private var mountRoll: Float? = null
    private var statusText: String = "idle"

    override fun start(onUpdate: (DrSensorSnapshot) -> Unit) {
        stop()
        this.onUpdate = onUpdate
        statusText = "binding…"
        publish()
        try {
            val binder = getServiceBinder(SERVICE_NAME)
            if (binder == null) {
                statusText = "binder null ($SERVICE_NAME)"
                publish()
                return
            }
            service = binder
            val cb = DrCallbackBinder()
            callback = cb
            if (!transactRegister(binder, cb, register = true)) {
                statusText = "registerINaviDrCallback failed"
                publish()
                return
            }
            if (!transactOpenClose(binder, open = true)) {
                statusText = "openDrData failed (callback registered)"
                publish()
                return
            }
            statusText = "ok (waiting samples)"
            publish()
        } catch (e: Exception) {
            statusText = "error: ${e.javaClass.simpleName}: ${e.message}"
            Log.d(TAG, statusText, e)
            publish()
        }
    }

    override fun stop() {
        val svc = service
        val cb = callback
        if (svc != null && cb != null) {
            runCatching { transactOpenClose(svc, open = false) }
            runCatching { transactRegister(svc, cb, register = false) }
        }
        service = null
        callback = null
        onUpdate = null
    }

    private fun publish() {
        onUpdate?.invoke(
            DrSensorSnapshot(
                source = source,
                statusText = statusText,
                gyroYaw = gyroYaw,
                gyroPitch = gyroPitch,
                gyroRoll = gyroRoll,
                gyroTemp = gyroTemp,
                accelX = accelX,
                accelY = accelY,
                accelZ = accelZ,
                pulseValue = pulseValue,
                pulseGear = pulseGear,
                mountExist = mountExist,
                mountYaw = mountYaw,
                mountPitch = mountPitch,
                mountRoll = mountRoll,
                lastUpdateElapsedMs = SystemClock.elapsedRealtime(),
            ),
        )
    }

    private inner class DrCallbackBinder : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            when (code) {
                INTERFACE_TRANSACTION -> {
                    reply?.writeString(CALLBACK_DESCRIPTOR)
                    return true
                }
                TXN_ON_GYRO -> {
                    data.enforceInterface(CALLBACK_DESCRIPTOR)
                    val info = if (data.readInt() != 0) {
                        AdayoGyroInfo.CREATOR.createFromParcel(data)
                    } else {
                        null
                    }
                    if (info != null) {
                        gyroPitch = info.pitch
                        gyroYaw = info.yaw
                        gyroRoll = info.roll
                        gyroTemp = info.temperature
                        statusText = "ok"
                        publish()
                    }
                    reply?.writeNoException()
                    reply?.writeInt(1)
                    return true
                }
                TXN_ON_ACCEL -> {
                    data.enforceInterface(CALLBACK_DESCRIPTOR)
                    val info = if (data.readInt() != 0) {
                        AdayoAcceleratorInfo.CREATOR.createFromParcel(data)
                    } else {
                        null
                    }
                    if (info != null) {
                        accelX = info.pitch
                        accelY = info.yaw
                        accelZ = info.roll
                        statusText = "ok"
                        publish()
                    }
                    reply?.writeNoException()
                    reply?.writeInt(1)
                    return true
                }
                TXN_ON_PULSE -> {
                    data.enforceInterface(CALLBACK_DESCRIPTOR)
                    val info = if (data.readInt() != 0) {
                        AdayoPulseInfo.CREATOR.createFromParcel(data)
                    } else {
                        null
                    }
                    if (info != null) {
                        pulseValue = info.value
                        pulseGear = info.gear
                        statusText = "ok"
                        publish()
                    }
                    reply?.writeNoException()
                    reply?.writeInt(1)
                    return true
                }
                TXN_ON_MOUNT -> {
                    data.enforceInterface(CALLBACK_DESCRIPTOR)
                    val info = if (data.readInt() != 0) {
                        AdayoMountAngleInfo.CREATOR.createFromParcel(data)
                    } else {
                        null
                    }
                    if (info != null) {
                        mountExist = info.isExistMountAngle
                        mountPitch = info.pitch
                        mountYaw = info.yaw
                        mountRoll = info.roll
                        statusText = "ok"
                        publish()
                    }
                    reply?.writeNoException()
                    reply?.writeInt(1)
                    return true
                }
            }
            return super.onTransact(code, data, reply, flags)
        }
    }

    companion object {
        private const val TAG = "A10NaviDr"
        private const val SERVICE_NAME = "adayo.service.navi.v2.0"
        private const val SERVICE_DESCRIPTOR =
            "com.adayo.proxy.navigation.navi.aidl.INaviService"
        private const val CALLBACK_DESCRIPTOR =
            "com.adayo.proxy.navigation.navi.aidl.INaviDrCallback"
        private const val TXN_REGISTER_DR = 5
        private const val TXN_UNREGISTER_DR = 6
        private const val TXN_OPEN_DR = 24
        private const val TXN_CLOSE_DR = 25
        private const val TXN_ON_GYRO = 1
        private const val TXN_ON_ACCEL = 2
        private const val TXN_ON_PULSE = 3
        private const val TXN_ON_MOUNT = 4

        private fun getServiceBinder(name: String): IBinder? {
            return try {
                val sm = Class.forName("android.os.ServiceManager")
                val method = sm.getMethod("getService", String::class.java)
                method.invoke(null, name) as? IBinder
            } catch (e: Exception) {
                Log.d(TAG, "ServiceManager.getService failed: ${e.message}")
                null
            }
        }

        private fun transactRegister(service: IBinder, callback: IBinder, register: Boolean): Boolean {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            return try {
                data.writeInterfaceToken(SERVICE_DESCRIPTOR)
                data.writeStrongBinder(callback)
                val code = if (register) TXN_REGISTER_DR else TXN_UNREGISTER_DR
                service.transact(code, data, reply, 0)
                reply.readException()
                reply.readInt() != 0
            } catch (e: Exception) {
                Log.d(TAG, "register/unregister failed: ${e.message}")
                false
            } finally {
                data.recycle()
                reply.recycle()
            }
        }

        private fun transactOpenClose(service: IBinder, open: Boolean): Boolean {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            return try {
                data.writeInterfaceToken(SERVICE_DESCRIPTOR)
                val code = if (open) TXN_OPEN_DR else TXN_CLOSE_DR
                service.transact(code, data, reply, 0)
                reply.readException()
                reply.readInt() != 0
            } catch (e: Exception) {
                Log.d(TAG, "open/closeDrData failed: ${e.message}")
                false
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }
}
