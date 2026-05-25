package vad.dashing.tbox

enum class HeadUnitCanMode(val storageValue: String) {
    Android9MbCan("android9_mbcan"),
    Android10Vhal("android10_vhal");

    companion object {
        fun fromStorageValue(raw: String?): HeadUnitCanMode {
            return entries.firstOrNull { it.storageValue == raw } ?: Android9MbCan
        }
    }
}
