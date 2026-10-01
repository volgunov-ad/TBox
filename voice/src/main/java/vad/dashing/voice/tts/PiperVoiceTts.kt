package vad.dashing.voice.tts

import android.content.Context
import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Piper RU (Irina) via sherpa-onnx OfflineTts.
 * Model files live under assets ([PiperModelPaths]); espeak-ng-data is copied
 * once to app filesDir because espeak needs real filesystem paths.
 */
class PiperVoiceTts(
    context: Context,
) : VoiceTts {
    private val appContext = context.applicationContext
    private val lock = Any()
    private val stopRequested = AtomicBoolean(false)

    @Volatile
    private var tts: OfflineTts? = null

    @Volatile
    private var track: AudioTrack? = null

    @Volatile
    private var readyError: String? = null

    val lastError: String?
        get() = readyError

    override fun ensureReady() {
        synchronized(lock) {
            if (tts != null) return
            val assets = appContext.assets
            if (!AssetTreeCopy.assetExists(assets, PiperModelPaths.MARKER_ASSET)) {
                readyError = "Модель TTS не найдена в APK (запустите tools/fetch_voice_tts_model.py)"
                Log.w(TAG, readyError!!)
                return
            }
            try {
                val dataDir = installEspeakData(assets)
                val vits = OfflineTtsVitsModelConfig().apply {
                    model = PiperModelPaths.MODEL_ONNX
                    tokens = PiperModelPaths.TOKENS
                    lexicon = ""
                    this.dataDir = dataDir
                    dictDir = ""
                }
                val modelConfig = OfflineTtsModelConfig().apply {
                    this.vits = vits
                    numThreads = 2
                    debug = false
                    provider = "cpu"
                }
                val config = OfflineTtsConfig().apply {
                    model = modelConfig
                    maxNumSentences = 1
                }
                tts = OfflineTts(assets, config)
                readyError = null
                Log.i(TAG, "Piper TTS ready, sampleRate=${tts!!.sampleRate()}")
            } catch (t: Throwable) {
                readyError = t.message ?: t.javaClass.simpleName
                Log.e(TAG, "Failed to init Piper TTS", t)
                tts = null
            }
        }
    }

    override fun speak(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        ensureReady()
        val engine = synchronized(lock) { tts } ?: return
        stop()
        stopRequested.set(false)
        try {
            val audio = engine.generate(trimmed, sid = 0, speed = 1.0f)
            val samples = audio.samples
            if (samples.isEmpty() || stopRequested.get()) return
            playFloatPcm(samples, audio.sampleRate)
        } catch (t: Throwable) {
            readyError = t.message ?: t.javaClass.simpleName
            Log.e(TAG, "speak failed", t)
        }
    }

    override fun stop() {
        stopRequested.set(true)
        synchronized(lock) {
            try {
                track?.pause()
                track?.flush()
                track?.stop()
            } catch (_: Throwable) {
            }
            try {
                track?.release()
            } catch (_: Throwable) {
            }
            track = null
        }
    }

    override fun release() {
        stop()
        synchronized(lock) {
            try {
                tts?.release()
            } catch (_: Throwable) {
            }
            tts = null
        }
    }

    private fun installEspeakData(assets: AssetManager): String {
        val destRoot = File(appContext.filesDir, "tts")
        val marker = File(destRoot, "${PiperModelPaths.ESPEAK_ASSET_DIR}/.installed")
        if (!marker.isFile) {
            if (destRoot.exists()) {
                destRoot.deleteRecursively()
            }
            destRoot.mkdirs()
            AssetTreeCopy.copyAssetDir(assets, PiperModelPaths.ESPEAK_ASSET_DIR, destRoot)
            marker.parentFile?.mkdirs()
            marker.writeText("1")
        }
        return File(destRoot, PiperModelPaths.ESPEAK_ASSET_DIR).absolutePath
    }

    private fun playFloatPcm(samples: FloatArray, sampleRate: Int) {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        if (minBuf <= 0) {
            readyError = "AudioTrack buffer size invalid ($minBuf)"
            return
        }
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setSampleRate(sampleRate)
            .build()
        val local = AudioTrack(
            attrs,
            format,
            maxOf(minBuf, samples.size * 4),
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
        synchronized(lock) {
            track?.release()
            track = local
        }
        local.play()
        var offset = 0
        while (offset < samples.size && !stopRequested.get()) {
            val written = local.write(
                samples,
                offset,
                samples.size - offset,
                AudioTrack.WRITE_BLOCKING,
            )
            if (written <= 0) break
            offset += written
        }
        if (!stopRequested.get()) {
            // Allow the buffer to drain roughly.
            val ms = ((samples.size.toLong() * 1000L) / sampleRate).toInt().coerceAtLeast(50)
            try {
                Thread.sleep(ms.toLong().coerceAtMost(30_000L))
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        synchronized(lock) {
            if (track === local) {
                try {
                    local.stop()
                    local.release()
                } catch (_: Throwable) {
                }
                track = null
            }
        }
    }

    companion object {
        private const val TAG = "PiperVoiceTts"

        fun createOrNoOp(context: Context): VoiceTts {
            val assets = context.applicationContext.assets
            return if (AssetTreeCopy.assetExists(assets, PiperModelPaths.MARKER_ASSET)) {
                PiperVoiceTts(context)
            } else {
                Log.w(TAG, "TTS model missing — using NoOpVoiceTts")
                NoOpVoiceTts()
            }
        }
    }
}
