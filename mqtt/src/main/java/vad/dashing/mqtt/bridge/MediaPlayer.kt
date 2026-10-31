package vad.dashing.mqtt.bridge

import org.json.JSONObject
import vad.dashing.mqtt.ha.MediaBundle

data class MediaSample(
    val available: Boolean,
    val text: String?,
    val number: Double?,
)

data class MediaView(
    val state: String,
    val title: String,
    val artist: String,
    val positionSec: Int?,
    val durationSec: Int?,
    val volume: Int?,
) {
    fun json(): String {
        val body = JSONObject()
            .put("state", state)
            .put("title", title)
            .put("artist", artist)
        if (positionSec != null) body.put("position", positionSec)
        if (durationSec != null) body.put("duration", durationSec)
        if (volume != null) body.put("volume", volume)
        return body.toString()
    }

    fun sameExceptPosition(other: MediaView): Boolean =
        state == other.state &&
            title == other.title &&
            artist == other.artist &&
            durationSec == other.durationSec &&
            volume == other.volume
}

fun mergeMedia(previous: MediaView?, samples: Map<String, MediaSample>): MediaView {
    val playing = samples["media_playing"]
    val title = samples["media_title"]
    val artist = samples["media_artist"]
    val position = samples["media_position_ms"]
    val duration = samples["media_duration_ms"]
    val volume = samples["hu_media_volume"]
    val nextTitle = textOf(title, previous?.title.orEmpty())
    val nextState = when {
        playing == null -> previous?.state ?: "idle"
        !playing.available -> if (nextTitle.isBlank()) "idle" else "paused"
        playing.text.equals("on", ignoreCase = true) -> "playing"
        nextTitle.isBlank() -> "idle"
        else -> "paused"
    }
    return MediaView(
        state = nextState,
        title = nextTitle,
        artist = textOf(artist, previous?.artist.orEmpty()),
        positionSec = secondsOf(position, previous?.positionSec),
        durationSec = secondsOf(duration, previous?.durationSec),
        volume = intOf(volume, previous?.volume),
    )
}

fun mediaViewOf(json: String?): MediaView? {
    if (json.isNullOrBlank()) return null
    val body = runCatching { JSONObject(json) }.getOrNull() ?: return null
    return MediaView(
        state = body.optString("state", "idle"),
        title = body.optString("title"),
        artist = body.optString("artist"),
        positionSec = body.optIntOrNull("position"),
        durationSec = body.optIntOrNull("duration"),
        volume = body.optIntOrNull("volume"),
    )
}

sealed class MediaCommand {
    data object Play : MediaCommand()
    data object Pause : MediaCommand()
    data object Next : MediaCommand()
    data object Previous : MediaCommand()
    data class Volume(val level: Int) : MediaCommand()
}

fun parseMediaCommand(payload: String): MediaCommand? {
    val text = payload.trim()
    if (text.isEmpty()) return null
    val volume = Regex("^volume\\s+(-?\\d+)$", RegexOption.IGNORE_CASE).matchEntire(text)
    if (volume != null) return MediaCommand.Volume(volume.groupValues[1].toInt())
    return when (text.uppercase()) {
        "PLAY" -> MediaCommand.Play
        "PAUSE" -> MediaCommand.Pause
        "NEXT" -> MediaCommand.Next
        "PREVIOUS" -> MediaCommand.Previous
        else -> null
    }
}

/** Null means the command is ignored (pause while already paused). */
fun mediaInvoke(bundle: MediaBundle, command: MediaCommand, playing: Boolean): CommandResult? {
    return when (command) {
        MediaCommand.Play -> CommandResult(
            InvokeRequest.Actions(builtinBody(bundle.playAction)),
            publishedState = null,
        )
        MediaCommand.Pause -> {
            if (!playing) return null
            CommandResult(
                InvokeRequest.Actions(builtinBody(bundle.pauseToggleAction)),
                publishedState = null,
            )
        }
        MediaCommand.Next -> CommandResult(
            InvokeRequest.Actions(builtinBody(bundle.nextAction)),
            publishedState = null,
        )
        MediaCommand.Previous -> CommandResult(
            InvokeRequest.Actions(builtinBody(bundle.previousAction)),
            publishedState = null,
        )
        is MediaCommand.Volume -> {
            if (command.level !in bundle.volumeMin..bundle.volumeMax) return null
            CommandResult(
                InvokeRequest.Actions(builtinBody(bundle.volumeAction, command.level)),
                publishedState = null,
            )
        }
    }
}

private fun textOf(sample: MediaSample?, previous: String): String {
    if (sample == null) return previous
    if (!sample.available) return ""
    return sample.text?.trim().orEmpty()
}

private fun secondsOf(sample: MediaSample?, previous: Int?): Int? {
    if (sample == null) return previous
    if (!sample.available) return null
    val ms = sample.number ?: return null
    if (!ms.isFinite() || ms < 0) return null
    return (ms / 1000.0).toInt()
}

private fun intOf(sample: MediaSample?, previous: Int?): Int? {
    if (sample == null) return previous
    if (!sample.available) return null
    val number = sample.number ?: return null
    if (!number.isFinite()) return null
    return number.toInt()
}

private fun JSONObject.optIntOrNull(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return optInt(key)
}
