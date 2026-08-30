package vad.dashing.tbox

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OemOverlayAppMonitorTest {
    @Test
    fun avmState_zeroAndHideAreOff() {
        assertFalse(OemOverlayAvmState.isShowing(null))
        assertFalse(OemOverlayAvmState.isShowing(""))
        assertFalse(OemOverlayAvmState.isShowing("0"))
        assertFalse(OemOverlayAvmState.isShowing("hide"))
        assertFalse(OemOverlayAvmState.isShowing("AVM_STATE_HIDE"))
        assertFalse(OemOverlayAvmState.isShowing("false"))
    }

    @Test
    fun avmState_nonzeroAndShowAreOn() {
        assertTrue(OemOverlayAvmState.isShowing("1"))
        assertTrue(OemOverlayAvmState.isShowing("AVM_STATE_SHOW"))
        assertTrue(OemOverlayAvmState.isShowing("show"))
        assertTrue(OemOverlayAvmState.isShowing("true"))
    }
}
