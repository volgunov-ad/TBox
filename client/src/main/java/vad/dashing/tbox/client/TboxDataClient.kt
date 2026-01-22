package vad.dashing.tbox.client

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import vad.dashing.tbox.ITboxDataListener
import vad.dashing.tbox.ITboxDataService
import vad.dashing.tbox.client.data.CanDataRepository
import vad.dashing.tbox.client.data.CycleDataRepository
import vad.dashing.tbox.client.data.TboxRepository

object TboxDataClient {
    private const val ACTION_BIND = "vad.dashing.tbox.BIND_TBOX_DATA"
    private const val SERVICE_PACKAGE = "vad.dashing.tbox"

    private var service: ITboxDataService? = null
    private var isBound = false
    private var appContext: Context? = null

    private val listener = object : ITboxDataListener.Stub() {
        override fun onTboxDataChanged(data: Bundle?) {
            if (data != null) {
                TboxRepository.updateFromBundle(data)
            }
        }

        override fun onCanDataChanged(data: Bundle?) {
            if (data != null) {
                CanDataRepository.updateFromBundle(data)
            }
        }

        override fun onCycleDataChanged(data: Bundle?) {
            if (data != null) {
                CycleDataRepository.updateFromBundle(data)
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = ITboxDataService.Stub.asInterface(binder)
            try {
                service?.registerListener(listener, ITboxDataService.FLAG_ALL)
                fetchInitial()
            } catch (_: Exception) {
                // Remote process may die; ignore and rely on reconnect.
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    fun bind(context: Context) {
        if (isBound) return
        appContext = context.applicationContext
        val intent = Intent(ACTION_BIND).apply {
            setPackage(SERVICE_PACKAGE)
        }
        isBound = appContext?.bindService(intent, connection, Context.BIND_AUTO_CREATE) == true
    }

    fun unbind() {
        val context = appContext ?: return
        if (!isBound) return
        try {
            service?.unregisterListener(listener)
        } catch (_: Exception) {
            // Ignore remote failures.
        }
        context.unbindService(connection)
        isBound = false
        service = null
    }

    private fun fetchInitial() {
        try {
            service?.getTboxData()?.let { TboxRepository.updateFromBundle(it) }
            service?.getCanData()?.let { CanDataRepository.updateFromBundle(it) }
            service?.getCycleData()?.let { CycleDataRepository.updateFromBundle(it) }
        } catch (_: Exception) {
            // Ignore remote failures.
        }
    }
}
