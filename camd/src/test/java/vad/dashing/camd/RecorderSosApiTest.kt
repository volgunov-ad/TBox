package vad.dashing.camd

import org.junit.Assert.assertEquals
import org.junit.Test

class RecorderSosApiTest {
    @Test
    fun notRecordingIsAnError() {
        val plan = RecorderSosApi.plan(armed = false, hasSosFolder = true, saveRunning = false)
        assertEquals("state=error error=rec_off", plan.reply)
        assertEquals(RecorderSosApi.Action.ERROR_NOT_RECORDING, plan.action)
    }

    @Test
    fun missingSosFolderIsAnErrorEvenWhileRecording() {
        val plan = RecorderSosApi.plan(armed = true, hasSosFolder = false, saveRunning = false)
        assertEquals("state=error error=no_sos_folder", plan.reply)
        assertEquals(RecorderSosApi.Action.ERROR_NO_SOS_FOLDER, plan.action)
    }

    @Test
    fun secondTapWhileSaveRunsIsBusy() {
        val plan = RecorderSosApi.plan(armed = true, hasSosFolder = true, saveRunning = true)
        assertEquals("state=rec_sos busy", plan.reply)
        assertEquals(RecorderSosApi.Action.BUSY, plan.action)
    }

    @Test
    fun recordingWithFolderStartsTheSave() {
        val plan = RecorderSosApi.plan(armed = true, hasSosFolder = true, saveRunning = false)
        assertEquals("state=rec_sos started", plan.reply)
        assertEquals(RecorderSosApi.Action.START, plan.action)
    }
}
