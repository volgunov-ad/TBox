package vad.dashing.dataresize.gpt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GptPlannerTest {
    private val disk = 124_993_536L

    private fun resource(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/gpt/$name")) { name }.use { it.readBytes() }

    private fun benchImages() = GptImages(resource("a10_old_pgpt.bin"), resource("a10_old_sgpt.bin"), disk)

    private fun benchPlan(): MovePlan {
        val state = GptPlanner.evaluate(benchImages())
        assertTrue(state.toString(), state is PartitionState.Movable)
        return (state as PartitionState.Movable).plan
    }

    @Test
    fun benchLayout_matchesHardwareVerifiedTables() {
        val plan = benchPlan()
        assertEquals(39, plan.entry.index)
        assertEquals(30_445_568L, plan.entry.first)
        assertEquals(4_194_304L, plan.entry.sectors)
        assertEquals(60_407_808L, plan.newFirst)
        assertEquals(64_585_677L, plan.newSectors)
        assertEquals(124_993_485L, plan.secondaryLba)
        assertEquals(52L, plan.primarySectors)
        assertEquals(51L, plan.secondarySectors)
        assertEquals(29_496L, plan.copySeekMiB)
        assertEquals(2_048L, plan.copyMiB)
        assertEquals("27461877dc483fc6e10bb87cf5e1bf4c", GptPlanner.md5(plan.oldPrimary))
        assertEquals("bf19c0f6c3f53b7b3ac77478922de882", GptPlanner.md5(plan.oldSecondary))
        assertEquals("1bab74e6969950c8da77499e5badeaeb", GptPlanner.md5(plan.newPrimary))
        assertEquals("1b3211dbeb4145f9a3575bd38ca4333f", GptPlanner.md5(plan.newSecondary))
    }

    @Test
    fun newTables_areReportedAsAlreadyMoved() {
        val plan = benchPlan()
        val state = GptPlanner.evaluate(GptImages(plan.newPrimary, plan.newSecondary, disk))
        assertTrue(state is PartitionState.AlreadyMoved)
        assertEquals(60_407_808L, (state as PartitionState.AlreadyMoved).entry.first)
    }

    @Test(expected = GptException::class)
    fun corruptedEntry_isRejected() {
        val primary = resource("a10_old_pgpt.bin")
        primary[1024 + 38 * 256 + 40] = (primary[1024 + 38 * 256 + 40] + 1).toByte()
        GptPlanner.evaluate(GptImages(primary, resource("a10_old_sgpt.bin"), disk))
    }

    @Test(expected = GptException::class)
    fun otherDiskSize_isRejected() {
        GptPlanner.evaluate(GptImages(resource("a10_old_pgpt.bin"), resource("a10_old_sgpt.bin"), disk * 2))
    }

    @Test(expected = GptException::class)
    fun differentCopies_areRejected() {
        val plan = benchPlan()
        GptPlanner.evaluate(GptImages(plan.newPrimary, plan.oldSecondary, disk))
    }
}
