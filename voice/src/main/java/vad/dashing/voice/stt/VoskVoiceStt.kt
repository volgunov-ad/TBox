package vad.dashing.voice.stt

import android.content.Context
import android.util.Log
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import vad.dashing.voice.tts.AssetTreeCopy
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Vosk small-ru STT. Model is copied from assets to filesDir once
 * (Vosk needs real filesystem paths).
 */
class VoskVoiceStt(
    context: Context,
) : VoiceStt {
    private val appContext = context.applicationContext
    private val lock = Any()

    @Volatile
    private var model: Model? = null

    @Volatile
    private var speechService: SpeechService? = null

    @Volatile
    override var isReady: Boolean = false
        private set

    @Volatile
    override var lastError: String? = null
        private set

    private val listening = AtomicBoolean(false)
    private val endedOnce = AtomicBoolean(false)

    override fun ensureReady() {
        synchronized(lock) {
            if (model != null) {
                isReady = true
                return
            }
            val assets = appContext.assets
            if (!AssetTreeCopy.assetExists(assets, VoskModelPaths.MARKER_ASSET)) {
                lastError = "Модель STT не найдена в APK (gradlew :voice:fetchSttModel)"
                isReady = false
                Log.w(TAG, lastError!!)
                return
            }
            try {
                val modelDir = installModel(assets)
                model = Model(modelDir)
                isReady = true
                lastError = null
                Log.i(TAG, "Vosk model ready: $modelDir")
            } catch (t: Throwable) {
                lastError = t.message ?: t.javaClass.simpleName
                isReady = false
                model = null
                Log.e(TAG, "Failed to init Vosk", t)
            }
        }
    }

    override fun startListening(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onEnded: (ListenEndReason) -> Unit,
    ) {
        ensureReady()
        val mdl = synchronized(lock) { model }
        if (mdl == null) {
            onEnded(ListenEndReason.ERROR)
            return
        }
        stopListeningInternal(cancel = true)
        endedOnce.set(false)
        listening.set(true)
        try {
            val recognizer = Recognizer(mdl, VoskModelPaths.SAMPLE_RATE)
            val service = SpeechService(recognizer, VoskModelPaths.SAMPLE_RATE)
            synchronized(lock) {
                speechService = service
            }
            val listener = object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    val text = VoskHypothesisParser.extractText(hypothesis.orEmpty())
                    if (text.isNotEmpty()) onPartial(text)
                }

                override fun onResult(hypothesis: String?) {
                    // Intermediate utterance end — keep listening until timeout/stop.
                    val text = VoskHypothesisParser.extractText(hypothesis.orEmpty())
                    if (text.isNotEmpty()) onPartial(text)
                }

                override fun onFinalResult(hypothesis: String?) {
                    val text = VoskHypothesisParser.extractText(hypothesis.orEmpty())
                    if (text.isNotEmpty()) onFinal(text)
                    finish(ListenEndReason.FINAL, onEnded)
                }

                override fun onError(exception: Exception?) {
                    lastError = exception?.message ?: "STT error"
                    Log.e(TAG, "STT error", exception)
                    finish(ListenEndReason.ERROR, onEnded)
                }

                override fun onTimeout() {
                    // Timeout path does not call getFinalResult in SpeechService —
                    // pull whatever partial we can via a manual stop for final text.
                    try {
                        val leftover = VoskHypothesisParser.extractText(
                            recognizer.finalResult,
                        )
                        if (leftover.isNotEmpty()) onFinal(leftover)
                    } catch (t: Throwable) {
                        Log.w(TAG, "timeout finalResult failed", t)
                    }
                    finish(ListenEndReason.TIMEOUT, onEnded)
                }
            }
            service.startListening(listener, VoskModelPaths.LISTEN_TIMEOUT_MS)
        } catch (t: Throwable) {
            lastError = t.message ?: t.javaClass.simpleName
            Log.e(TAG, "startListening failed", t)
            listening.set(false)
            onEnded(ListenEndReason.ERROR)
        }
    }

    override fun stopListening() {
        if (!listening.get()) return
        val service = synchronized(lock) { speechService }
        try {
            service?.stop()
        } catch (t: Throwable) {
            Log.w(TAG, "stopListening", t)
            finish(ListenEndReason.STOPPED) {}
        }
        // onFinalResult from SpeechService should follow; if not, end locally.
    }

    override fun release() {
        stopListeningInternal(cancel = true)
        synchronized(lock) {
            try {
                model?.close()
            } catch (_: Throwable) {
            }
            model = null
            isReady = false
        }
    }

    private fun finish(reason: ListenEndReason, onEnded: (ListenEndReason) -> Unit) {
        if (!endedOnce.compareAndSet(false, true)) return
        listening.set(false)
        synchronized(lock) {
            try {
                speechService?.shutdown()
            } catch (_: Throwable) {
            }
            speechService = null
        }
        onEnded(reason)
    }

    private fun stopListeningInternal(cancel: Boolean) {
        synchronized(lock) {
            val svc = speechService ?: return
            try {
                if (cancel) svc.cancel() else svc.stop()
            } catch (_: Throwable) {
            }
            try {
                svc.shutdown()
            } catch (_: Throwable) {
            }
            speechService = null
        }
        listening.set(false)
    }

    private fun installModel(assets: android.content.res.AssetManager): String {
        val destRoot = File(appContext.filesDir, "stt")
        val marker = File(destRoot, "${VoskModelPaths.MODEL_DIR}/conf/model.conf")
        if (!marker.isFile) {
            if (destRoot.exists()) destRoot.deleteRecursively()
            destRoot.mkdirs()
            AssetTreeCopy.copyAssetDir(assets, VoskModelPaths.MODEL_DIR, destRoot)
        }
        return File(destRoot, VoskModelPaths.MODEL_DIR).absolutePath
    }

    companion object {
        private const val TAG = "VoskVoiceStt"

        fun createOrNoOp(context: Context): VoiceStt {
            val assets = context.applicationContext.assets
            return if (AssetTreeCopy.assetExists(assets, VoskModelPaths.MARKER_ASSET)) {
                VoskVoiceStt(context)
            } else {
                Log.w(TAG, "STT model missing — using NoOpVoiceStt")
                NoOpVoiceStt()
            }
        }
    }
}
