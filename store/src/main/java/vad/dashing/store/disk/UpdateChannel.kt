package vad.dashing.store.disk

enum class UpdateChannel(val storageValue: String) {
    RELEASE("release"),
    DEVELOPMENT("development"),
    ;

    override fun toString(): String = storageValue

    companion object {
        fun fromStorageValue(raw: String?): UpdateChannel =
            entries.firstOrNull { it.storageValue == raw } ?: RELEASE
    }
}
