package vad.dashing.tbox

import android.content.Context

object AppContextHolder {
    @Volatile
    private var _appContext: Context? = null

    val appContextOrNull: Context?
        get() = _appContext

    fun init(context: Context) {
        _appContext = context.applicationContext
    }
}
