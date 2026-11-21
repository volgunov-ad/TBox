package vad.dashing.store.disk

enum class CloudSource(val storageValue: String) {
    AUTO("auto"),
    YANDEX("yandex"),
    MAILRU("mailru"),
    ;

    companion object {
        fun fromStorageValue(raw: String?): CloudSource =
            entries.firstOrNull { it.storageValue == raw } ?: AUTO
    }
}
