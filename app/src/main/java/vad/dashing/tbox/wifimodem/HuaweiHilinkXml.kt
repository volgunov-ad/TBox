package vad.dashing.tbox.wifimodem

/**
 * Minimal tag extractors for Huawei HiLink XML responses (no full XML parser dependency).
 */
object HuaweiHilinkXml {
    fun tagValue(xml: String, tag: String): String? {
        val open = "<$tag>"
        val close = "</$tag>"
        val start = xml.indexOf(open)
        if (start < 0) return null
        val from = start + open.length
        val end = xml.indexOf(close, from)
        if (end < 0) return null
        return xml.substring(from, end).trim()
    }

    fun tagValues(xml: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        var i = 0
        while (i < xml.length) {
            val lt = xml.indexOf('<', i)
            if (lt < 0) break
            if (lt + 1 < xml.length && (xml[lt + 1] == '/' || xml[lt + 1] == '?' || xml[lt + 1] == '!')) {
                i = lt + 1
                continue
            }
            val gt = xml.indexOf('>', lt + 1)
            if (gt < 0) break
            val rawName = xml.substring(lt + 1, gt).substringBefore(' ').trim()
            if (rawName.isEmpty() || rawName.endsWith("/")) {
                i = gt + 1
                continue
            }
            val close = "</$rawName>"
            val end = xml.indexOf(close, gt + 1)
            if (end < 0) {
                i = gt + 1
                continue
            }
            val value = xml.substring(gt + 1, end).trim()
            if ('<' !in value) {
                result[rawName] = value
                i = end + close.length
            } else {
                // Descend into nested content instead of skipping the whole block.
                i = gt + 1
            }
        }
        return result
    }
}
