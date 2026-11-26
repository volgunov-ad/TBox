package vad.dashing.camd

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val CAMD_LOG_TAG = "CamdService"

class CamdService : Service() {

    private val shutdownReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // The device is powering off: finalize MP4 segments while we still can.
            CamdRuntime.recorder?.finalizeNow()
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var notificationJob: Job? = null

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
        // stop() → closeAll → hide posts to main and awaits. Must not run on the main
        // thread or the latch deadlocks (posted Runnable never runs).
        Thread({ CamdRuntime.stop() }, "camd-runtime-stop").apply { isDaemon = true }.start()
        scope.cancel()
        runCatching { unregisterReceiver(shutdownReceiver) }
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(CAMD_LOG_TAG, "команда старта: intent=$intent")
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL, "Камеры ГУ", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
        val notification = buildNotification(RecorderController.RunState.STOPPED, emptySet())
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
        if (intent?.action == ACTION_SOS) {
            Thread({ CamdRuntime.recorder?.requestEmergencySave() }, "camd-notif-sos")
                .apply { isDaemon = true }
                .start()
        }
        observeRecorder(manager)
        return START_STICKY
    }

    /** Keeps the ongoing notification in step with the dashcam (recording, waiting, no space). */
    private fun observeRecorder(manager: NotificationManager) {
        val recorder = CamdRuntime.recorder ?: return
        if (notificationJob?.isActive == true) return
        notificationJob = scope.launch {
            recorder.state
                .map { it.runState to if (it.enabled) it.inputs else emptySet() }
                .distinctUntilChanged()
                .collect { (runState, inputs) ->
                    manager.notify(NOTIFICATION_ID, buildNotification(runState, inputs))
                }
        }
    }

    private fun buildNotification(runState: RecorderController.RunState, inputs: Set<Int>): Notification {
        val cameras = inputs.sorted().joinToString(" ") { "cam$it" }
        val text = when (runState) {
            RecorderController.RunState.RECORDING -> "Идёт запись: $cameras"
            RecorderController.RunState.WAITING_MEDIA -> "Регистратор ждёт носитель"
            RecorderController.RunState.OVERFLOW -> "Регистратор: нет места"
            RecorderController.RunState.STOPPED -> "Окна камер и регистратор"
        }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Камеры ГУ")
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
        if (runState == RecorderController.RunState.RECORDING) {
            val sos = PendingIntent.getService(
                this,
                1,
                Intent(this, CamdService::class.java).setAction(ACTION_SOS),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(
                Notification.Action.Builder(null, "Аварийно сохранить", sos).build(),
            )
        }
        return builder.build()
    }

    private companion object {
        const val CHANNEL = "camd"
        const val NOTIFICATION_ID = 1
        const val ACTION_SOS = "vad.dashing.camd.SOS"
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
                val createdApi = CamdApiServer(created, createdWindows, createdRecorder)
                engine = created
                windows = createdWindows
                recorder = createdRecorder
                apiServer = createdApi
                Log.i(CAMD_LOG_TAG, "runtime запущен")
                createdApi.start()
                Thread({ createdWindows.recover() }, "camd-recover").apply { isDaemon = true }.start()
                // The dashcam master switch persists: recording starts by itself after boot.
                Thread({ createdRecorder.autoStartIfEnabled() }, "camd-rec-boot").apply { isDaemon = true }.start()
                Thread({ BootUsbUnlock.runAfterDelay(app) }, "camd-usb-unlock").apply { isDaemon = true }.start()
            } else if (apiServer?.isRunning() != true) {
                // Process survived but listen supervisor exited — restart API only (P1-5).
                val eng = engine
                val win = windows
                val rec = recorder
                if (eng != null) {
                    Log.w(TAG, "API не слушает — перезапускаю listen")
                    val createdApi = CamdApiServer(eng, win, rec)
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
        // Detach under the lock so a quick restart builds a fresh runtime instead of
        // seeing the one that is being torn down; the slow teardown runs outside.
        val detached = synchronized(lock) {
            Log.i(TAG, "runtime stop")
            val snapshot = Triple(apiServer, recorder, windows)
            apiServer = null
            recorder = null
            windows = null
            engine = null
            snapshot
        }
        val (api, rec, win) = detached
        runCatching { api?.stop() }
        runCatching { rec?.finalizeNow() }
        runCatching { win?.shutdown() }
    }

    fun engine(context: android.content.Context): CamdEngine = synchronized(lock) {
        start(context)
        checkNotNull(engine)
    }

    fun windows(context: android.content.Context): CameraWindows = synchronized(lock) {
        start(context)
        checkNotNull(windows)
    }

    fun recorder(context: android.content.Context): RecorderController = synchronized(lock) {
        start(context)
        checkNotNull(recorder)
    }
}
