package vad.dashing.tbox.phone

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.CompanionDeviceService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import vad.dashing.tbox.phoneble.PhoneBleCodec
import vad.dashing.tbox.phoneble.PhoneSensorMath
import java.util.concurrent.CopyOnWriteArraySet
import java.util.regex.Pattern
import kotlin.math.abs

/**
 * Coordinates the shared [PhoneLinkRouter] and [PhoneTelemetryStreamer] between
 * [PhoneActivity] (foreground UI) and [PhoneTelemetryService] (background FGS when
 * at least one telemetry toggle is enabled).
 *
 * When all telemetry toggles (`shareLocation`, `shareGyro`, `shareCompass`) are off,
 * the foreground service is stopped and [PhoneLinkRouter] runs only while [PhoneActivity]
 * is visible, preserving the original behavior.
 */
object PhoneSessionController {
    interface Listener {
        fun onSnapshotChanged(snap: PhoneBleCodec.Snapshot, receivedAtElapsedMs: Long)
        fun onStateChanged()
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<Listener>()
    private var appContext: Context? = null
    lateinit var store: PhoneStore
        private set
    var router: PhoneLinkRouter? = null
        private set
    private var streamer: PhoneTelemetryStreamer? = null

    var snapshot: PhoneBleCodec.Snapshot = PhoneBleCodec.Snapshot()
        private set
    var lastSnapAtElapsedMs: Long = 0L
        private set
    var huDemandMask: Int = PhoneBleCodec.DEMAND_ALL
        private set

    private var uiVisible: Boolean = false
    private var serviceRunning: Boolean = false
    private var lastBgHeartbeatAtElapsedMs: Long = 0L

    private val bgTickRunnable = object : Runnable {
        override fun run() {
            val r = router ?: return
            val st = store
            if (!uiVisible && !st.anyTelemetryEnabled) {
                r.stop()
                streamer?.stopAll()
                return
            }
            if (r.bluetoothOn) {
                r.start()
            }
            syncStreamer()
            val now = SystemClock.elapsedRealtime()
            if (!uiVisible && st.anyTelemetryEnabled && r.directLinkUp) {
                if (now - lastBgHeartbeatAtElapsedMs >= BG_HEARTBEAT_MS) {
                    lastBgHeartbeatAtElapsedMs = now
                    r.writeSealedUiPacket(
                        type = PhoneBleCodec.TYPE_REFRESH,
                        body = PhoneBleCodec.refreshBody(r.textHash, PhoneBleCodec.GROUP_HEADER),
                        resetAcceptRange = false,
                        updateWaitingCounter = true,
                    )
                }
            }
            mainHandler.postDelayed(this, BG_TICK_MS)
        }
    }

    fun ensureInitialized(context: Context): PhoneLinkRouter {
        val ctx = context.applicationContext
        appContext = ctx
        if (!::store.isInitialized) {
            store = PhoneStore(ctx)
        }
        var r = router
        if (r == null) {
            r = PhoneLinkRouter(
                context = ctx,
                store = store,
                onSnap = { next ->
                    val now = SystemClock.elapsedRealtime()
                    snapshot = next
                    lastSnapAtElapsedMs = now
                    if (!store.paired) {
                        store.paired = true
                    }
                    listeners.forEach { it.onSnapshotChanged(next, now) }
                },
                onTelemetryAck = { ack ->
                    val now = SystemClock.elapsedRealtime()
                    lastSnapAtElapsedMs = now
                    if (ack.outsideTenths != null || ack.insideTenths != null) {
                        snapshot = snapshot.copy(
                            outsideTenths = ack.outsideTenths ?: snapshot.outsideTenths,
                            insideTenths = ack.insideTenths ?: snapshot.insideTenths,
                            gen = if (snapshot.gen == 0) 1 else snapshot.gen,
                        )
                        listeners.forEach { it.onSnapshotChanged(snapshot, now) }
                    }
                    if (huDemandMask != ack.demandMask) {
                        huDemandMask = ack.demandMask
                        syncStreamer()
                        listeners.forEach { it.onStateChanged() }
                    }
                },
            )
            r.storeKey = store.key
            router = r
            streamer = PhoneTelemetryStreamer(ctx, r)
        }
        return r
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun onUiStarted(context: Context, permissionsReady: Boolean) {
        val r = ensureInitialized(context)
        uiVisible = true
        r.foreground = true
        r.storeKey = store.key
        if (permissionsReady) {
            r.start()
            syncServiceAndStreamer(context)
        }
        scheduleBgLoop()
    }

    fun onUiStopped(context: Context) {
        uiVisible = false
        router?.foreground = false
        val st = store
        if (!st.anyTelemetryEnabled) {
            mainHandler.removeCallbacks(bgTickRunnable)
            streamer?.stopAll()
            router?.stop()
            PhoneTelemetryService.stop(context)
        } else {
            syncServiceAndStreamer(context)
            scheduleBgLoop()
        }
    }

    fun onServiceStarted(context: Context) {
        ensureInitialized(context)
        serviceRunning = true
        router?.storeKey = store.key
        router?.start()
        syncStreamer()
        scheduleBgLoop()
    }

    fun onServiceStopped() {
        serviceRunning = false
        if (!uiVisible) {
            mainHandler.removeCallbacks(bgTickRunnable)
            streamer?.stopAll()
            router?.stop()
        }
    }

    fun updateTelemetryToggles(
        context: Context,
        shareLocation: Boolean = store.shareLocation,
        shareGyro: Boolean = store.shareGyro,
        shareCompass: Boolean = store.shareCompass,
    ) {
        ensureInitialized(context)
        store.shareLocation = shareLocation
        store.shareGyro = shareGyro
        store.shareCompass = shareCompass
        router?.keepDirectForTelemetry = store.anyTelemetryEnabled
        if (uiVisible || store.anyTelemetryEnabled) {
            router?.start()
        }
        syncServiceAndStreamer(context)
        listeners.forEach { it.onStateChanged() }
    }

    fun onPathChanged(context: Context, next: PhonePath) {
        ensureInitialized(context)
        val r = router ?: return
        r.path = next
        r.keepDirectForTelemetry = store.anyTelemetryEnabled
        syncServiceAndStreamer(context)
        listeners.forEach { it.onStateChanged() }
    }

    fun syncServiceAndStreamer(context: Context) {
        ensureInitialized(context)
        if (store.anyTelemetryEnabled) {
            PhoneTelemetryService.startOrUpdate(context)
        } else {
            streamer?.stopAll()
            PhoneTelemetryService.stop(context)
            if (!uiVisible) {
                router?.stop()
            }
        }
        syncStreamer()
    }

    private fun syncStreamer() {
        val r = router ?: return
        val s = streamer ?: return
        val st = store
        val directActive = st.path != PhonePath.COMPANION && r.directLinkUp && (r.directConfirmed || st.directPaired)
        if (!directActive) {
            huDemandMask = PhoneBleCodec.DEMAND_ALL
            s.stopAll()
            return
        }
        val wantLoc = st.shareLocation && (huDemandMask and PhoneBleCodec.DEMAND_LOC) != 0
        val wantYaw = st.shareGyro && (huDemandMask and PhoneBleCodec.DEMAND_YAW) != 0
        val wantMag = st.shareCompass && (huDemandMask and PhoneBleCodec.DEMAND_MAG) != 0
        s.updateActiveStreams(
            wantLocation = wantLoc,
            wantGyro = wantYaw,
            wantCompass = wantMag,
        )
    }

    private fun scheduleBgLoop() {
        mainHandler.removeCallbacks(bgTickRunnable)
        if (uiVisible || store.anyTelemetryEnabled || serviceRunning) {
            mainHandler.postDelayed(bgTickRunnable, BG_TICK_MS)
        }
    }

    private const val BG_TICK_MS = 1_000L
    private const val BG_HEARTBEAT_MS = 5_000L
}

/**
 * Streams raw `GPS_PROVIDER` (`LOC_POS` + `LOC_MOTION`), gravity-projected gyroscope (`YAW`),
 * and tilt-compensated magnetometer (`MAG`) over the direct RFCOMM link when connected.
 */
@SuppressLint("MissingPermission")
class PhoneTelemetryStreamer(
    context: Context,
    private val router: PhoneLinkRouter,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val locationManager = appContext.getSystemService(LocationManager::class.java)
    private val sensorManager = appContext.getSystemService(SensorManager::class.java)

    private var locationActive = false
    private var gyroActive = false
    private var compassActive = false
    private var sensorListenerRegistered = false

    private var locGen = 0
    private var visibleSats = 0
    private var usedSats = 0

    // Gravity / mount tracking
    private var latestGravity: PhoneSensorMath.Vec3? = null
    private var windowStartGravity: PhoneSensorMath.Vec3? = null
    private var settledMountGravity: PhoneSensorMath.Vec3? = null
    private var windowAccelAnomaly = false
    private var mountMovedPending = false
    private var mountTiltSinceElapsedMs = 0L
    private var hasGravitySensor = false

    // Gyro window integration (150 ms)
    /** `SensorEvent.timestamp` (ns) of the last gyro sample and of the current window start. */
    private var lastGyroEventNs = 0L
    private var windowStartEventNs = 0L
    private var windowAccumPsiDeg = 0.0

    // Magnetometer state (1000 ms)
    private var latestMagUt: PhoneSensorMath.Vec3? = null
    private var latestMagAccuracy: Int = SensorManager.SENSOR_STATUS_UNRELIABLE
    private var lastMagSentElapsedMs = 0L

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            val total = status.satelliteCount
            var used = 0
            for (i in 0 until total) {
                if (status.usedInFix(i)) used++
            }
            visibleSats = total
            usedSats = used
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!locationActive || !router.directLinkUp) return
            val nowElapsedMs = SystemClock.elapsedRealtime()
            val fixElapsedMs = location.elapsedRealtimeNanos / 1_000_000L
            val ageMs = (nowElapsedMs - fixElapsedMs).coerceAtLeast(0L).toInt()
            locGen = (locGen + 1) and 0xFF
            val phoneLoc = PhoneBleCodec.PhoneLocation(
                gen = locGen,
                latitude = location.latitude,
                longitude = location.longitude,
                horizontalAccuracyM = if (location.hasAccuracy()) location.accuracy else null,
                altitudeM = if (location.hasAltitude()) location.altitude.toFloat() else null,
                speedKmh = if (location.hasSpeed()) location.speed * 3.6f else null,
                bearingDeg = if (location.hasBearing()) location.bearing else null,
                usedSatellites = usedSats,
                visibleSatellites = maxOf(visibleSats, usedSats),
                fixAgeMs = ageMs,
                locateStatus = true,
            )
            val (posBody, motionBody) = PhoneBleCodec.locBodies(locGen, phoneLoc)
            router.writeDirectTelemetryBatch(
                listOf(
                    PhoneBleCodec.TYPE_LOC_POS to posBody,
                    PhoneBleCodec.TYPE_LOC_MOTION to motionBody,
                ),
            )
        }

        @Deprecated("Deprecated in API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val nowElapsedMs = SystemClock.elapsedRealtime()
            when (event.sensor.type) {
                Sensor.TYPE_GRAVITY -> {
                    val g = PhoneSensorMath.Vec3(
                        event.values[0],
                        event.values[1],
                        event.values[2],
                    )
                    onGravityVector(g, nowElapsedMs)
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    val ax = event.values[0]
                    val ay = event.values[1]
                    val az = event.values[2]
                    val raw = PhoneSensorMath.Vec3(ax, ay, az)
                    val norm = raw.norm()
                    if (abs(norm - PhoneSensorMath.STANDARD_GRAVITY_MPS2) > 3.5f) {
                        windowAccelAnomaly = true
                    }
                    // The low-passed accelerometer is the apparent vertical (includes braking and
                    // cornering), so it may only stand in for gravity when TYPE_GRAVITY is absent.
                    if (hasGravitySensor) return
                    val prev = latestGravity
                    val filtered = if (prev == null) {
                        raw
                    } else {
                        PhoneSensorMath.Vec3(
                            prev.x * 0.85f + ax * 0.15f,
                            prev.y * 0.85f + ay * 0.15f,
                            prev.z * 0.85f + az * 0.15f,
                        )
                    }
                    onGravityVector(filtered, nowElapsedMs)
                }
                Sensor.TYPE_GYROSCOPE -> {
                    if (!gyroActive) return
                    val g = latestGravity ?: return
                    val gyro = PhoneSensorMath.Vec3(
                        event.values[0],
                        event.values[1],
                        event.values[2],
                    )
                    val rateDegPerSec = PhoneSensorMath.projectYawRateDegPerSec(gyro, g) ?: return
                    // Callbacks run on the main looper and arrive in bursts after UI stalls;
                    // the event timestamp is when the sample was actually taken.
                    val eventNs = event.timestamp
                    if (windowStartEventNs == 0L || eventNs <= lastGyroEventNs) {
                        windowStartEventNs = eventNs
                        lastGyroEventNs = eventNs
                        windowStartGravity = g
                        windowAccumPsiDeg = 0.0
                        return
                    }
                    val dtSec = ((eventNs - lastGyroEventNs) / 1e9).coerceIn(0.0, 0.25)
                    lastGyroEventNs = eventNs
                    windowAccumPsiDeg += rateDegPerSec * dtSec

                    val windowDtMs = ((eventNs - windowStartEventNs) / 1_000_000L).toInt()
                    if (windowDtMs >= YAW_WINDOW_MS) {
                        flushYawWindow(nowElapsedMs, windowDtMs, g)
                        windowStartEventNs = eventNs
                    }
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    if (!compassActive) return
                    latestMagUt = PhoneSensorMath.Vec3(
                        event.values[0],
                        event.values[1],
                        event.values[2],
                    )
                    latestMagAccuracy = event.accuracy
                    if (nowElapsedMs - lastMagSentElapsedMs >= MAG_PERIOD_MS) {
                        flushMagSample(nowElapsedMs)
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
                latestMagAccuracy = accuracy
            }
        }
    }

    private fun onGravityVector(g: PhoneSensorMath.Vec3, nowElapsedMs: Long) {
        latestGravity = g
        val settled = settledMountGravity
        if (settled == null) {
            settledMountGravity = g
            mountTiltSinceElapsedMs = 0L
            return
        }
        val tiltFromMount = PhoneSensorMath.angleBetweenDeg(settled, g)
        if (tiltFromMount <= PhoneSensorMath.MOUNT_MOVED_GRAVITY_TILT_DEG) {
            mountTiltSinceElapsedMs = 0L
            return
        }
        // A real mount change persists; a stop or a turn tilts the estimate only briefly.
        if (mountTiltSinceElapsedMs == 0L) {
            mountTiltSinceElapsedMs = nowElapsedMs
        } else if (nowElapsedMs - mountTiltSinceElapsedMs >= MOUNT_MOVED_CONFIRM_MS) {
            mountMovedPending = true
            settledMountGravity = g
            mountTiltSinceElapsedMs = 0L
        }
    }

    private fun flushYawWindow(nowElapsedMs: Long, windowDtMs: Int, currentGravity: PhoneSensorMath.Vec3) {
        val startG = windowStartGravity ?: currentGravity
        val tiltDeg = PhoneSensorMath.angleBetweenDeg(startG, currentGravity)
        val gravDeltaMg = PhoneSensorMath.gravityDeltaMilliG(startG, currentGravity)
        val stable = !windowAccelAnomaly && tiltDeg <= PhoneSensorMath.MAX_WINDOW_GRAVITY_TILT_DEG
        val moved = mountMovedPending || tiltDeg > PhoneSensorMath.MOUNT_MOVED_GRAVITY_TILT_DEG
        if (stable && mountMovedPending) {
            mountMovedPending = false
        }

        val sample = PhoneBleCodec.PhoneYawSample(
            dtMs = windowDtMs,
            dPsiDeg = windowAccumPsiDeg.toFloat(),
            monoLowMs = (nowElapsedMs and 0xFFFFL).toInt(),
            mountStable = stable,
            mountMoved = moved,
            gravDeltaMg = gravDeltaMg,
        )
        windowStartGravity = currentGravity
        windowAccumPsiDeg = 0.0
        windowAccelAnomaly = false

        if (router.directLinkUp) {
            router.writeDirectTelemetry(PhoneBleCodec.TYPE_YAW, PhoneBleCodec.yawBody(sample))
        }
    }

    private fun flushMagSample(nowElapsedMs: Long) {
        val mag = latestMagUt ?: return
        val g = latestGravity ?: return
        val tc = PhoneSensorMath.computeTiltCompensatedMag(mag, g) ?: return
        lastMagSentElapsedMs = nowElapsedMs
        val moved = mountMovedPending
        if (!gyroActive && mountMovedPending) {
            mountMovedPending = false
        }
        val sample = PhoneBleCodec.PhoneMagSample(
            magneticAzimuthDeg = tc.azimuthDeg,
            fieldStrengthUt = tc.fieldStrengthUt,
            accuracy = latestMagAccuracy.coerceIn(0, 3),
            pitchDeg = tc.pitchDeg,
            rollDeg = tc.rollDeg,
            monoLowMs = (nowElapsedMs and 0xFFFFL).toInt(),
            mountMoved = moved,
        )
        if (router.directLinkUp) {
            router.writeDirectTelemetry(PhoneBleCodec.TYPE_MAG, PhoneBleCodec.magBody(sample))
        }
    }

    fun updateActiveStreams(
        wantLocation: Boolean,
        wantGyro: Boolean,
        wantCompass: Boolean,
    ) {
        val canReadLocation = hasFineLocationPermission(appContext)
        val targetLoc = wantLocation && canReadLocation
        if (targetLoc != locationActive) {
            if (targetLoc) startLocationUpdates() else stopLocationUpdates()
        }
        if (wantGyro != gyroActive || wantCompass != compassActive) {
            gyroActive = wantGyro
            compassActive = wantCompass
            rebuildSensorSubscriptions()
        }
    }

    fun stopAll() {
        stopLocationUpdates()
        gyroActive = false
        compassActive = false
        rebuildSensorSubscriptions()
    }

    private fun startLocationUpdates() {
        val lm = locationManager ?: return
        if (locationActive) return
        try {
            if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return
            lm.registerGnssStatusCallback(gnssCallback, mainHandler)
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                LOC_PERIOD_MS,
                0f,
                locationListener,
                Looper.getMainLooper(),
            )
            locationActive = true
        } catch (e: Exception) {
            Log.w(TAG, "startLocationUpdates failed: ${e.message}")
        }
    }

    private fun stopLocationUpdates() {
        if (!locationActive) return
        locationActive = false
        runCatching { locationManager?.removeUpdates(locationListener) }
        runCatching { locationManager?.unregisterGnssStatusCallback(gnssCallback) }
    }

    private fun rebuildSensorSubscriptions() {
        val sm = sensorManager ?: return
        if (sensorListenerRegistered) {
            runCatching { sm.unregisterListener(sensorListener) }
            sensorListenerRegistered = false
        }
        windowStartEventNs = 0L
        lastGyroEventNs = 0L
        windowAccumPsiDeg = 0.0
        windowAccelAnomaly = false
        if (!gyroActive && !compassActive) return

        val gravitySensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val accelSensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        hasGravitySensor = gravitySensor != null &&
            sm.registerListener(sensorListener, gravitySensor, SensorManager.SENSOR_DELAY_GAME)
        mountTiltSinceElapsedMs = 0L
        if (accelSensor != null) {
            sm.registerListener(sensorListener, accelSensor, SensorManager.SENSOR_DELAY_GAME)
        }
        if (gyroActive) {
            sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
                sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
        if (compassActive) {
            sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let {
                sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
        sensorListenerRegistered = true
    }

    companion object {
        private const val TAG = "PhoneTelemetryStreamer"
        private const val LOC_PERIOD_MS = 1_000L
        private const val YAW_WINDOW_MS = 150
        private const val MAG_PERIOD_MS = 1_000L
        /** Tilt beyond MOUNT_MOVED_GRAVITY_TILT_DEG must persist this long to count as a mount move. */
        private const val MOUNT_MOVED_CONFIRM_MS = 3_000L

        fun hasFineLocationPermission(context: Context): Boolean =
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        fun hasBackgroundLocationPermission(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= 29) {
                context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                hasFineLocationPermission(context)
            }
    }
}

/**
 * Foreground service that runs **only** while at least one telemetry toggle
 * (`shareLocation`, `shareGyro`, `shareCompass`) is enabled in [PhoneStore].
 */
class PhoneTelemetryService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    /** Foreground service type currently held, or `null` before the first `startForeground`. */
    private var promotedFgsType: Int? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val store = PhoneStore(this)
        // Every start comes from startForegroundService: startForeground must run before any
        // stopSelf, otherwise the system kills the app ("did not then call startForeground").
        val promoted = promoteToForeground(store)
        if (!promoted || !store.anyTelemetryEnabled) {
            PhoneSessionController.onServiceStopped()
            if (promoted) stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        PhoneSessionController.onServiceStarted(this)
        return START_STICKY
    }

    override fun onDestroy() {
        promotedFgsType = null
        PhoneSessionController.onServiceStopped()
        super.onDestroy()
    }

    @RequiresApi(29)
    private fun desiredFgsType(store: PhoneStore): Int {
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        // Android 14 only needs the while-in-use grant when the location type is started from
        // the foreground; background starts without ACCESS_BACKGROUND_LOCATION fall back below.
        if (store.anyTelemetryEnabled && store.shareLocation &&
            PhoneTelemetryStreamer.hasFineLocationPermission(this)
        ) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        return type
    }

    private fun promoteToForeground(store: PhoneStore): Boolean {
        val desired = if (Build.VERSION.SDK_INT >= 29) desiredFgsType(store) else 0
        val current = promotedFgsType
        if (current == desired) return true
        val notification = buildNotification()
        if (tryStartForeground(notification, desired)) {
            promotedFgsType = desired
            return true
        }
        // Already foreground (e.g. the location type was granted while the UI was visible and
        // a background re-promote was refused): keep what we hold instead of stopping.
        if (current != null) return true
        // Android 14+ background start without a location grant: connectedDevice alone keeps
        // gyro/compass streaming.
        if (Build.VERSION.SDK_INT >= 29) {
            val fallback = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            if (desired != fallback && tryStartForeground(notification, fallback)) {
                promotedFgsType = fallback
                return true
            }
        }
        return false
    }

    private fun tryStartForeground(notification: Notification, fgsType: Int): Boolean =
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, fgsType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "startForeground(type=$fgsType) failed: ${e.message}")
            false
        }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.telemetry_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        nm?.createNotificationChannel(channel)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, PhoneActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.telemetry_notification_text))
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "PhoneTelemetryService"
        private const val CHANNEL_ID = "phone_telemetry"
        private const val NOTIFICATION_ID = 1001

        fun startOrUpdate(context: Context) {
            val app = context.applicationContext
            if (!PhoneStore(app).anyTelemetryEnabled) {
                stop(app)
                return
            }
            if (!canHoldConnectedDeviceFgs(app)) {
                Log.w(TAG, "BLUETOOTH_CONNECT not granted: telemetry service not started")
                return
            }
            val intent = Intent(app, PhoneTelemetryService::class.java)
            runCatching {
                app.startForegroundService(intent)
            }.onFailure { e ->
                Log.w(TAG, "startForegroundService failed: ${e.message}")
            }
        }

        /** Android 14+ refuses the connectedDevice FGS type without a Bluetooth runtime grant. */
        private fun canHoldConnectedDeviceFgs(context: Context): Boolean =
            Build.VERSION.SDK_INT < 34 ||
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED

        fun stop(context: Context) {
            val app = context.applicationContext
            runCatching {
                app.stopService(Intent(app, PhoneTelemetryService::class.java))
            }
        }
    }
}

/**
 * Wakes the phone app when the associated head-unit Bluetooth device appears in range,
 * **only** when at least one telemetry toggle is enabled in [PhoneStore].
 *
 * `CompanionDeviceService` exists since API 31; older systems never bind this manifest entry.
 */
@RequiresApi(31)
class PhoneCompanionDeviceService : CompanionDeviceService() {
    @Deprecated("Deprecated in API 33")
    override fun onDeviceAppeared(address: String) {
        super.onDeviceAppeared(address)
        maybeStartForHeadUnit()
    }

    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        super.onDeviceAppeared(associationInfo)
        maybeStartForHeadUnit()
    }

    private fun maybeStartForHeadUnit() {
        val store = PhoneStore(this)
        if (!store.anyTelemetryEnabled) return
        PhoneTelemetryService.startOrUpdate(this)
    }
}

/**
 * Starts [PhoneTelemetryService] on Bluetooth ACL connect / boot **only** when
 * at least one telemetry toggle is enabled in [PhoneStore].
 */
class PhoneBluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val store = PhoneStore(context)
        if (!store.anyTelemetryEnabled) return
        when (intent?.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                // Headphones and watches connect too; only the known head unit should wake us.
                val known = store.directAddress
                @Suppress("DEPRECATION")
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (known.isNullOrBlank() || device?.address.equals(known, ignoreCase = true)) {
                    PhoneTelemetryService.startOrUpdate(context)
                }
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                PhoneTelemetryService.startOrUpdate(context)
            }
        }
    }
}

object PhoneSystemHelpers {
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) {
        runCatching {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                "package:${context.packageName}".toUri(),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure {
            runCatching {
                val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallback)
            }
        }
    }

    /**
     * Android 12 has no `onAssociationCreated`; the chosen device only comes back in the
     * chooser result, so presence observation is armed here for API 31–32.
     */
    fun onCompanionAssociationResult(context: Context, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK || data == null) return
        if (Build.VERSION.SDK_INT < 31 || Build.VERSION.SDK_INT >= 33) return
        @Suppress("DEPRECATION")
        val device = data.getParcelableExtra<BluetoothDevice>(CompanionDeviceManager.EXTRA_DEVICE)
            ?: return
        val cdm = context.getSystemService(CompanionDeviceManager::class.java) ?: return
        runCatching { cdm.startObservingDevicePresence(device.address) }
            .onFailure { Log.w("PhoneCompanion", "startObservingDevicePresence failed: ${it.message}") }
    }

    fun requestCompanionDeviceAssociation(
        context: Context,
        directAddress: String?,
        onIntentSender: (IntentSender) -> Unit,
    ) {
        val cdm = context.getSystemService(CompanionDeviceManager::class.java) ?: return
        val filterBuilder = BluetoothDeviceFilter.Builder()
        if (!directAddress.isNullOrBlank()) {
            filterBuilder.setAddress(directAddress)
        } else {
            filterBuilder.setNamePattern(Pattern.compile(".*"))
        }
        val request = AssociationRequest.Builder()
            .addDeviceFilter(filterBuilder.build())
            .setSingleDevice(!directAddress.isNullOrBlank())
            .build()
        cdm.associate(
            request,
            object : CompanionDeviceManager.Callback() {
                @Deprecated("Deprecated in API 33")
                override fun onDeviceFound(chooserLauncher: IntentSender) {
                    onIntentSender(chooserLauncher)
                }

                override fun onAssociationCreated(associationInfo: AssociationInfo) {
                    // Callback and AssociationInfo.deviceMacAddress are both API 33.
                    if (Build.VERSION.SDK_INT >= 33) {
                        runCatching { cdm.startObservingDevicePresence(associationInfo.deviceMacAddress?.toString().orEmpty()) }
                    }
                }

                override fun onFailure(error: CharSequence?) {
                    Log.w("PhoneCompanion", "associate failed: $error")
                }
            },
            Handler(Looper.getMainLooper()),
        )
    }
}
