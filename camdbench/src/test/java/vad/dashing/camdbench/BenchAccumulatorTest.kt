package vad.dashing.camdbench

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchAccumulatorTest {

    private fun feed(text: String): BenchAccumulator =
        BenchAccumulator().also { acc -> text.trimIndent().lines().forEach(acc::feed) }

    // Two cores, 100 ticks/s, samples 2 s apart: each interval has 400 ticks of total CPU.
    private val twoSamples = """
        K 100
        N 2
        ZT 0 cpu-0
        T 1000 1700000000000
        C 10000 8000
        F 0 1000000
        F 1 2000000
        Z 0 40000
        M 512000
        P 10 500 vad.dashing.camd
        H 10 11 100 camd-rec-4
        H 10 12 50 RenderThread
        P 20 300 camd_grab
        P 30 1000 surfaceflinger
        T 3000 1700000002000
        C 10400 8200
        F 0 1500000
        F 1 2000000
        Z 0 50000
        M 256000
        P 10 600 vad.dashing.camd
        H 10 11 180 camd-rec-4
        P 20 400 camd_grab
        E
    """

    @Test
    fun cpuBusyIsShareOfAllCores() {
        val s = feed(twoSamples).snapshot()
        assertEquals(2, s.samples)
        assertEquals(2.0, s.durationSec, 1e-9)
        assertEquals(2, s.cores)
        // 400 total, 200 idle in the interval.
        assertEquals(50.0, s.cpuBusyPct, 1e-9)
        assertEquals(50.0, s.cpuBusyMaxPct, 1e-9)
        assertTrue(s.ended)
    }

    @Test
    fun processPercentIsOfOneCore() {
        val s = feed(twoSamples).snapshot()
        // 100 ticks over 2 s at 100 Hz = 0.5 core.
        assertEquals(50.0, s.process("vad.dashing.camd")!!.avgPct, 1e-9)
        assertEquals(50.0, s.process("camd_grab")!!.avgPct, 1e-9)
        // Not printed in the second sample: idle, not missing.
        assertEquals(0.0, s.process("surfaceflinger")!!.avgPct, 1e-9)
    }

    @Test
    fun threadsAreKeyedByOwnerAndName() {
        val s = feed(twoSamples).snapshot()
        val rec = s.threads.first { it.name == "vad.dashing.camd/camd-rec-4" }
        assertEquals(40.0, rec.avgPct, 1e-9)
        assertEquals(0.0, s.threads.first { it.name == "vad.dashing.camd/RenderThread" }.avgPct, 1e-9)
    }

    @Test
    fun freqZonesAndMemory() {
        val s = feed(twoSamples).snapshot()
        assertEquals(1250.0, s.freqMhz[0], 1e-9)
        assertEquals(2000.0, s.freqMhz[1], 1e-9)
        assertEquals("cpu-0", s.zones.single().name)
        assertEquals(45.0, s.zones.single().avgC, 1e-9)
        assertEquals(50.0, s.zones.single().maxC, 1e-9)
        assertEquals(250L, s.memAvailableMinMb)
    }

    @Test
    fun processStartedDuringRunCountsFromZero() {
        val s = feed(
            """
            K 100
            N 1
            T 0 0
            C 0 0
            T 1000 0
            C 100 50
            P 40 30 newproc
            E
            """,
        ).snapshot()
        assertEquals(30.0, s.process("newproc")!!.avgPct, 1e-9)
    }

    @Test
    fun reusedPidWithLowerTicksIsANewProcess() {
        val s = feed(
            """
            K 100
            N 1
            T 0 0
            C 0 0
            P 7 1000 old
            T 1000 0
            C 100 0
            P 7 20 fresh
            E
            """,
        ).snapshot()
        assertEquals(20.0, s.process("fresh")!!.avgPct, 1e-9)
        assertEquals(0.0, s.process("old")!!.avgPct, 1e-9)
    }

    @Test
    fun peakIsTheBusiestInterval() {
        val s = feed(
            """
            K 100
            N 1
            T 0 0
            C 0 0
            P 1 0 a
            T 1000 0
            C 100 90
            P 1 10 a
            T 2000 0
            C 200 110
            P 1 90 a
            E
            """,
        ).snapshot()
        assertEquals(45.0, s.process("a")!!.avgPct, 1e-9)
        assertEquals(80.0, s.process("a")!!.maxPct, 1e-9)
        assertEquals(80.0, s.cpuBusyMaxPct, 1e-9)
        assertEquals(45.0, s.cpuBusyPct, 1e-9)
    }

    @Test
    fun liveShowsTheLastClosedInterval() {
        val acc = feed(
            """
            K 100
            N 1
            T 0 0
            C 0 0
            P 10 0 vad.dashing.camd
            T 1000 0
            C 100 75
            P 10 25 vad.dashing.camd
            T 2000 0
            """,
        )
        val live = acc.live()
        assertEquals(25.0, live.cpuBusyPct, 1e-9)
        assertEquals(25.0, live.camdPct, 1e-9)
        assertEquals(0.0, live.grabPct, 1e-9)
        assertFalse(acc.snapshot().ended)
    }

    @Test
    fun emptyInputIsSafe() {
        val s = BenchAccumulator().snapshot()
        assertEquals(0, s.samples)
        assertEquals(0.0, s.cpuBusyPct, 1e-9)
        assertNull(s.process("vad.dashing.camd"))
        assertNotNull(s.freqMhz)
    }
}
