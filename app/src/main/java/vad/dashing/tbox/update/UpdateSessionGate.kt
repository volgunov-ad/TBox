package vad.dashing.tbox.update

object UpdateSessionGate {
    @Volatile
    var checkStartedThisSession: Boolean = false

    fun tryBeginSessionCheck(): Boolean {
        if (checkStartedThisSession) return false
        checkStartedThisSession = true
        return true
    }
}
