package vad.dashing.mqtt.service

/**
 * The error store has no discovery topics. After it is replaced by a working store,
 * the remembered set has to be read again or the first republish forgets them.
 */
internal fun publishedTopicsNeedReload(hadStorageError: Boolean, storageError: String?): Boolean =
    hadStorageError && storageError == null
