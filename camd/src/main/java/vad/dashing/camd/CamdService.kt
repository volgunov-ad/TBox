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

    fun start(context: android.content.Context) {
        synchronized(lock) {
            if (engine == null) {
                val created = CamdEngine(context.applicationContext.filesDir)
                engine = created
                CamdApiServer(created).start()
            }
        }
    }

    fun engine(context: android.content.Context): CamdEngine {
        start(context)
        return checkNotNull(engine)
    }
}
