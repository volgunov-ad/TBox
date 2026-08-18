package vad.dashing.tbox.location

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Pulls a map point out of a share / VIEW intent (Yandex, 2GIS, geo:, pasted text).
 * Same decode rules as the road-match map «Paste coordinates» button.
 */
object GeoShareIntentParser {

    fun parse(intent: Intent?, context: Context? = null): GeoCoordinateParse.LatLon? {
        for (text in candidateTexts(intent, context)) {
            GeoCoordinateParse.parse(text)?.let { return it }
        }
        return null
    }

    fun candidateTexts(intent: Intent?, context: Context? = null): List<String> {
        if (intent == null) return emptyList()
        val out = ArrayList<String>(8)
        fun add(value: CharSequence?) {
            val text = value?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) out.add(text)
        }
        add(intent.getCharSequenceExtra(Intent.EXTRA_TEXT))
        add(intent.getStringExtra(Intent.EXTRA_HTML_TEXT))
        add(intent.dataString)
        intent.data?.let { add(uriAsShareText(it)) }
        val clip = intent.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) {
                val item = clip.getItemAt(i)
                if (context != null) {
                    add(item.coerceToText(context))
                } else {
                    add(item.text)
                }
                item.uri?.let { add(uriAsShareText(it)) }
            }
        }
        return out.distinct()
    }

    private fun uriAsShareText(uri: Uri): String = uri.toString()
}
