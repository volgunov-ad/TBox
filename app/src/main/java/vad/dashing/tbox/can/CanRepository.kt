package vad.dashing.tbox.can

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vad.dashing.tbox.mbcan.MbCanCommand
import vad.dashing.tbox.mbcan.MbCanCommandResult
import vad.dashing.tbox.mbcan.MbCanAvailability
import vad.dashing.tbox.mbcan.MbCanEngineFacade

object CanRepository {
    private const val TAG = "CanRepository"

    private val _backendSelection = MutableStateFlow(
        CanBackendSelection(CanBackendType.None, "Not initialized")
    )
    val backendSelection: StateFlow<CanBackendSelection> = _backendSelection.asStateFlow()

    private var activeBackend: CanControlBackend? = null

    suspend fun bind(context: Context, scope: CoroutineScope) {
        val selection = selectBackend()
        _backendSelection.value = selection
        activeBackend = when (selection.type) {
            CanBackendType.MbCan -> MbCanControlBackend
            CanBackendType.Vhal -> VhalControlBackend
            CanBackendType.None -> null
        }
        Log.i(TAG, "Selected backend=${selection.type} reason=${selection.reason}")
        activeBackend?.bind(context, scope)
    }

    suspend fun unbind() {
        try {
            activeBackend?.unbind()
        } finally {
            activeBackend = null
        }
    }

    suspend fun execute(command: MbCanCommand): MbCanCommandResult {
        val backend = activeBackend
            ?: return MbCanCommandResult(false, "CAN backend is not initialized")
        return backend.execute(command)
    }

    private fun selectBackend(): CanBackendSelection {
        val mbCanAvailability = MbCanEngineFacade.probeAvailability()
        if (mbCanAvailability !is MbCanAvailability.Unavailable) {
            return CanBackendSelection(CanBackendType.MbCan, "mbCAN classes are present")
        }

        if (hasAndroidCarFramework()) {
            return CanBackendSelection(
                CanBackendType.Vhal,
                "android.car detected; VHAL backend scaffold selected"
            )
        }

        return CanBackendSelection(
            CanBackendType.None,
            "No supported CAN backend found. mbCAN reason=${mbCanAvailability.reason}"
        )
    }

    private fun hasAndroidCarFramework(): Boolean {
        return try {
            Class.forName("android.car.Car", false, CanRepository::class.java.classLoader)
            true
        } catch (_: Throwable) {
            false
        }
    }
}
