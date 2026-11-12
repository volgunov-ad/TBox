package vad.dashing.camd

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

class CamdService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
    private val lock = Any()
    private var engine: CamdEngine? = null
    private var windows: CameraWindows? = null

    fun start(context: android.content.Context) {
        synchronized(lock) {
            if (engine == null) {
                val app = context.applicationContext
                val created = CamdEngine(app.filesDir)
                val createdWindows = CameraWindows(app)
                engine = created
                windows = createdWindows
                CamdApiServer(created, createdWindows).start()
                Thread({ createdWindows.recover() }, "camd-recover").apply { isDaemon = true }.start()
            }
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
