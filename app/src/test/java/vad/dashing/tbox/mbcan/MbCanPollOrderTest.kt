package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Test

class MbCanPollOrderTest {
    @Test
    fun merge_emptyPriority_keepsActiveOrder() {
        val active = listOf(
            MbCanSignal.EngineRpm,
            MbCanSignal.AudioEqMode,
        )
        assertEquals(active, MbCanPollOrder.merge(active, emptyList()))
    }

    @Test
    fun merge_priorityFirst_thenRemainingActive() {
        val active = listOf(
            MbCanSignal.EngineRpm,
            MbCanSignal.AudioEqMode,
            MbCanSignal.HudSwitch,
        )
        val ordered = MbCanPollOrder.merge(
            active,
            listOf(MbCanSignal.HudSwitch, MbCanSignal.AudioEqMode),
        )
        assertEquals(
            listOf(
                MbCanSignal.HudSwitch,
                MbCanSignal.AudioEqMode,
                MbCanSignal.EngineRpm,
            ),
            ordered,
        )
    }

    @Test
    fun merge_ignoresPriorityNotInActive() {
        val ordered = MbCanPollOrder.merge(
            listOf(MbCanSignal.EngineRpm),
            listOf(MbCanSignal.HudSwitch),
        )
        assertEquals(listOf(MbCanSignal.EngineRpm), ordered)
    }

    @Test
    fun prepend_putsVisibleSignalsAheadOfQueuedAdds() {
        val queued = listOf(MbCanSignal.AudioEqMode, MbCanSignal.HudSwitch)
        val prepended = MbCanPollOrder.prepend(
            listOf(MbCanSignal.HudSwitch),
            queued,
        ).toList()
        assertEquals(
            listOf(MbCanSignal.HudSwitch, MbCanSignal.AudioEqMode),
            prepended,
        )
    }
}
