package vad.dashing.tbox

/**
 * Flavor switch for user-visible text in code that has no [android.content.Context]
 * (catalogs, validators, JVM unit tests). `ru` is the default flavor; `en` is the
 * English product flavor.
 */
internal fun ruEn(ru: String, en: String): String =
    if (BuildConfig.FLAVOR.trim().lowercase() == "en") en else ru
