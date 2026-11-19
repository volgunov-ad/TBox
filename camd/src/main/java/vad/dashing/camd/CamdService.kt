package vad.dashing.camd

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

private const val CAMD_LOG_TAG = "CamdService"

class CamdService : Service() {

    private val shutdownReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // The device is powering off: finalize MP4 segments while we still can.
            CamdRuntime.recorder?.finalizeNow()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(CAMD_LOG_TAG, "сервис создан")
        val shutdownFilter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SHUTDOWN)
            addAction("android.intent.action.QUICKBOOT_POWEROFF")
        }
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            shutdownReceiver,
            shutdownFilter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onDestroy() {
        Log.i(CAMD_LOG_TAG, "сервис уничтожается — останавливаю runtime")
        CamdRuntime.stop()
        runCatching { unregisterReceiver(shutdownReceiver) }
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(CAMD_LOG_TAG, "команда старта: intent=$intent")
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL, "Камеры ГУ", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Камеры ГУ")
            .setContentText("Окна камер открыты")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        CamdRuntime.start(this)
        return START_STICKY
    }

    private companion object {
        const val CHANNEL = "camd"
        const val NOTIFICATION_ID = 1
    }
}

object CamdRuntime {
    private const val TAG = "CamdRuntime"
    private val lock = Any()
    private var engine: CamdEngine? = null
    private var windows: CameraWindows? = null
    private var apiServer: CamdApiServer? = null
    var recorder: RecorderController? = null
        private set

    fun start(context: android.content.Context) {
        synchronized(lock) {
            if (engine == null) {
                val app = context.applicationContext
                val created = CamdEngine(app.filesDir)
                val createdWindows = CameraWindows(app)
                val createdRecorder = RecorderController(app, createdWindows)
                val createdApi = CamdApiServer(created, createdWindows)
                engine = created
                windows = createdWindows
                recorder = createdRecorder
                apiServer = createdApi
                Log.i(CAMD_LOG_TAG, "runtime запущен")
                createdApi.start()
                Thread({ createdWindows.recover() }, "camd-recover").apply { isDaemon = true }.start()
                // The dashcam master switch persists: recording starts by itself after boot.
                Thread({ createdRecorder.autoStartIfEnabled() }, "camd-rec-boot").apply { isDaemon = true }.start()
            } else if (apiServer?.isRunning() != true) {
                // Process survived but listen supervisor exited — restart API only (P1-5).
                val eng = engine
                val win = windows
                if (eng != null) {
                    Log.w(TAG, "API не слушает — перезапускаю listen")
                    val createdApi = CamdApiServer(eng, win)
                    apiServer = createdApi
                    createdApi.start()
                }
            }
        }
    }

    /**
     * Tear down overlays, recorder engines, API socket and clear the singleton (P1-2).
     * Idempotent. Called from [CamdService.onDestroy].
     */
    fun stop() {
        synchronized(lock) {
            Log.i(TAG, "runtime stop")
            runCatching { apiServer?.stop() }
            runCatching { recorder?.finalizeNow() }
            runCatching { windows?.shutdown() }
            apiServer = null
            recorder = null
            windows = null
            engine = null
        }
    }

    fun engine(context: android.content.Context): CamdEngine {
        start(context)
        return checkNotNull(engine)
    }

    fun windows(context: android.content.Context): CameraWindows {
        start(context)
        return checkNotNull(windows)
    }
}
