package vad.dashing.mqtt.ui

/**
 * The screen loaded settings from the empty fallback file. The next open that has no
 * storage error is the real store, and the on-screen values must not be written back.
 */
internal fun staleUiNeedsReload(loadedWithError: Boolean, storageError: String?): Boolean =
    loadedWithError && storageError == null

internal const val SETTINGS_RELOADED =
    "Хранилище снова доступно. Показаны сохранённые настройки, изменение не записано."
