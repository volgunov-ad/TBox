package vad.dashing.store.disk

/**
 * Preferred language of APKs to download when an app publishes both `ru` and `en`
 * releases in `version.json`.
 */
enum class ApkLanguage(val storageValue: String, val flavor: String) {
    RU("ru", "ru"),
    EN("en", "en"),
    ;

    companion object {
        fun fromStorageValue(raw: String?): ApkLanguage =
            entries.firstOrNull { it.storageValue.equals(raw, ignoreCase = true) } ?: RU

        fun fromStoreFlavor(buildFlavor: String?): ApkLanguage =
            when (buildFlavor?.lowercase()) {
                "en" -> EN
                else -> RU
            }
    }
}
