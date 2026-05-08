package vad.dashing.tbox.can

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import vad.dashing.tbox.mbcan.MbCanCommand
import vad.dashing.tbox.mbcan.MbCanCommandResult

interface CanControlBackend {
    suspend fun bind(context: Context, scope: CoroutineScope)
    suspend fun unbind()
    suspend fun execute(command: MbCanCommand): MbCanCommandResult
}
