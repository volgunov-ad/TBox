package vad.dashing.tbox.can

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import vad.dashing.tbox.mbcan.MbCanCommand
import vad.dashing.tbox.mbcan.MbCanCommandResult
import vad.dashing.tbox.mbcan.MbCanRepository

object MbCanControlBackend : CanControlBackend {
    override suspend fun bind(context: Context, scope: CoroutineScope) {
        MbCanRepository.bind(scope)
    }

    override suspend fun unbind() {
        MbCanRepository.unbind()
    }

    override suspend fun execute(command: MbCanCommand): MbCanCommandResult {
        return MbCanRepository.execute(command)
    }
}
