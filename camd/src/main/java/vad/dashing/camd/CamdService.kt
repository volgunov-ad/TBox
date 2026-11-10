package vad.dashing.camd

import android.app.Service
import android.content.Intent
import android.os.IBinder

class CamdService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        CamdRuntime.start(this)
        return START_STICKY
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
