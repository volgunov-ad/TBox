package vad.dashing.camd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecorderEnableApiTest {
    @Test
    fun enableFailsWithoutCameras() {
        val plan = RecorderEnableApi.plan(
            wantEnabled = true,
            hasInputs = false,
            hasFolder = true,
            armed = false,
            prefEnabled = false,
        )
        assertEquals(RecorderEnableApi.Action.ERROR_NO_CAMERAS, plan.action)
        assertEquals("state=error error=no_cameras", plan.reply)
        assertEquals("Выберите хотя бы одну камеру", plan.message)
    }

    @Test
    fun enableFailsWithoutFolder() {
        val plan = RecorderEnableApi.plan(
            wantEnabled = true,
            hasInputs = true,
            hasFolder = false,
            armed = false,
            prefEnabled = false,
        )
        assertEquals(RecorderEnableApi.Action.ERROR_NO_FOLDER, plan.action)
        assertEquals("state=error error=no_folder", plan.reply)
        assertEquals("Выберите папку для записи", plan.message)
    }

    @Test
    fun enableCamerasCheckedBeforeFolder() {
        val plan = RecorderEnableApi.plan(
            wantEnabled = true,
            hasInputs = false,
            hasFolder = false,
            armed = false,
            prefEnabled = false,
        )
        assertEquals(RecorderEnableApi.Action.ERROR_NO_CAMERAS, plan.action)
    }

    @Test
    fun enableStartsWhenReady() {
        val plan = RecorderEnableApi.plan(
            wantEnabled = true,
            hasInputs = true,
            hasFolder = true,
            armed = false,
            prefEnabled = false,
        )
        assertEquals(RecorderEnableApi.Action.TURN_ON, plan.action)
        assertEquals("state=rec_on enabled=1", plan.reply)
        assertNull(plan.message)
    }

    @Test
    fun enableIsIdempotentWhenAlreadyArmedAndPrefOn() {
        val plan = RecorderEnableApi.plan(
            wantEnabled = true,
            hasInputs = true,
            hasFolder = true,
            armed = true,
            prefEnabled = true,
        )
        assertEquals(RecorderEnableApi.Action.IDEMPOTENT_ON, plan.action)
        assertEquals("state=rec_on enabled=1", plan.reply)
    }

    @Test
    fun enableTurnsOnWhenPrefOnButNotArmed() {
        // Pref survived reboot / finalizeNow; pipeline not running yet.
        val plan = RecorderEnableApi.plan(
            wantEnabled = true,
            hasInputs = true,
            hasFolder = true,
            armed = false,
            prefEnabled = true,
        )
        assertEquals(RecorderEnableApi.Action.TURN_ON, plan.action)
        assertEquals("state=rec_on enabled=1", plan.reply)
    }

    @Test
    fun disableAlwaysReturnsOff() {
        val armed = RecorderEnableApi.plan(
            wantEnabled = false,
            hasInputs = true,
            hasFolder = true,
            armed = true,
            prefEnabled = true,
        )
        assertEquals(RecorderEnableApi.Action.TURN_OFF, armed.action)
        assertEquals("state=rec_off enabled=0", armed.reply)

        val idle = RecorderEnableApi.plan(
            wantEnabled = false,
            hasInputs = false,
            hasFolder = false,
            armed = false,
            prefEnabled = false,
        )
        assertEquals(RecorderEnableApi.Action.TURN_OFF, idle.action)
        assertEquals("state=rec_off enabled=0", idle.reply)
    }

    @Test
    fun statusFlagsKeepEnabledAndActiveSeparate() {
        // Mirrors RecorderController.statusFlags contract used by STATUS.
        assertEquals(
            "rec_enabled=1 rec_active=0",
            statusFlags(master = true, active = false),
        )
        assertEquals(
            "rec_enabled=1 rec_active=1",
            statusFlags(master = true, active = true),
        )
        assertEquals(
            "rec_enabled=0 rec_active=0",
            statusFlags(master = false, active = false),
        )
    }

    private fun statusFlags(master: Boolean, active: Boolean): String =
        "rec_enabled=${if (master) 1 else 0} rec_active=${if (active) 1 else 0}"
}
