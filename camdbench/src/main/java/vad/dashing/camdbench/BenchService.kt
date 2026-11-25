package vad.dashing.camdbench

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

/** Keeps the measurement alive while the screen is in the background. */
class BenchService : Service() {

    @Volatile
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) start(intent.getIntExtra(EXTRA_SECONDS, DEFAULT_SECONDS))
        return START_NOT_STICKY
    }

    private fun start(seconds: Int) {
        if (worker?.isAlive == true) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW),
        )
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Идёт замер нагрузки")
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        worker = Thread({
            try {
                BenchSession(applicationContext, seconds).run()
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }, "camdbench-session").apply { start() }
    }

    companion object {
        private const val CHANNEL = "camdbench"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_START = "vad.dashing.camdbench.START"
        private const val EXTRA_SECONDS = "seconds"
        private const val DEFAULT_SECONDS = 600

        fun start(context: Context, seconds: Int) {
            context.startForegroundService(
                Intent(context, BenchService::class.java).setAction(ACTION_START).putExtra(EXTRA_SECONDS, seconds),
            )
        }

        /** The session thread checks the flag once a second and then writes the report. */
        fun stop() {
            BenchState.stopRequested = true
        }
    }
}
