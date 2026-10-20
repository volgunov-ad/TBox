package vad.dashing.dataresize

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vad.dashing.dataresize.gpt.GptImages
import vad.dashing.dataresize.gpt.GptPlanner
import vad.dashing.dataresize.gpt.PartitionState

class MoveFilesTest {
    private fun resource(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/gpt/$name")) { name }.use { it.readBytes() }

    @Test
    fun benchParams_matchHardwareVerifiedScript() {
        val state = GptPlanner.evaluate(
            GptImages(resource("a10_old_pgpt.bin"), resource("a10_old_sgpt.bin"), 124_993_536L),
        ) as PartitionState.Movable
        assertEquals(
            """
            DISK_SECT=124993536
            OLD_FIRST=30445568
            OLD_SECT=4194304
            NEW_FIRST=60407808
            NEW_SECT=64585677
            COPY_SEEK_MIB=29496
            COPY_MIB=2048
            PGPT_SECT=52
            SGPT_LBA=124993485
            SGPT_SECT=51
            MD5_OLD_P=27461877dc483fc6e10bb87cf5e1bf4c
            MD5_NEW_P=1bab74e6969950c8da77499e5badeaeb
            MD5_OLD_S=bf19c0f6c3f53b7b3ac77478922de882
            MD5_NEW_S=1b3211dbeb4145f9a3575bd38ca4333f
            """.trimIndent() + "\n",
            MoveFiles.params(state.plan),
        )
    }

    @Test
    fun mixedCopies_onlyForOneCopyOfEachKnownTable() {
        val state = GptPlanner.evaluate(
            GptImages(resource("a10_old_pgpt.bin"), resource("a10_old_sgpt.bin"), 124_993_536L),
        ) as PartitionState.Movable
        val params = MoveFiles.fields(MoveFiles.params(state.plan))
        assertEquals("124993536", params["DISK_SECT"])
        val oldP = GptPlanner.md5(state.plan.oldPrimary)
        val newP = GptPlanner.md5(state.plan.newPrimary)
        val oldS = GptPlanner.md5(state.plan.oldSecondary)
        val newS = GptPlanner.md5(state.plan.newSecondary)
        assertEquals(false, MoveFiles.mixedPrimaryNew(params, oldP, newS))
        assertEquals(true, MoveFiles.mixedPrimaryNew(params, newP, oldS))
        assertNull(MoveFiles.mixedPrimaryNew(params, oldP, oldS))
        assertNull(MoveFiles.mixedPrimaryNew(params, newP, newS))
        assertNull(MoveFiles.mixedPrimaryNew(params, oldP, "0".repeat(32)))
        assertNull(MoveFiles.mixedPrimaryNew(emptyMap(), oldP, newS))
    }

    @Test
    fun rollbackParams_swapTablesAndKeepMoveLayout() {
        val move = GptPlanner.evaluate(
            GptImages(resource("a10_old_pgpt.bin"), resource("a10_old_sgpt.bin"), 124_993_536L),
        ) as PartitionState.Movable
        val moveParams = MoveFiles.fields(MoveFiles.params(move.plan))
        val rebooted = GptImages(resource("a10_rebooted_pgpt.bin"), resource("a10_rebooted_sgpt.bin"), 124_993_536L)
        val plan = GptPlanner.relocate(rebooted, 30_445_568L, 30_445_568L + 4_194_304L - 1)
        val params = MoveFiles.fields(MoveFiles.rollbackParams(moveParams, plan))
        assertEquals(moveParams.keys.toList(), params.keys.toList())
        for (key in listOf("DISK_SECT", "OLD_FIRST", "OLD_SECT", "NEW_FIRST", "NEW_SECT", "PGPT_SECT", "SGPT_LBA")) {
            assertEquals(key, moveParams[key], params[key])
        }
        assertEquals(GptPlanner.md5(rebooted.primary), params["MD5_NEW_P"])
        assertEquals(GptPlanner.md5(rebooted.secondary), params["MD5_NEW_S"])
        assertEquals(GptPlanner.md5(plan.newPrimary), params["MD5_OLD_P"])
        assertEquals(GptPlanner.md5(plan.newSecondary), params["MD5_OLD_S"])
        val files = MoveFiles.rollbackBinaries(plan)
        assertEquals(params["MD5_NEW_P"], GptPlanner.md5(files.getValue("new_pgpt.bin")))
        assertEquals(params["MD5_OLD_S"], GptPlanner.md5(files.getValue("old_sgpt.bin")))
    }

    @Test
    fun statusFields_parsed() {
        assertEquals(
            mapOf("RUNNING" to "1", "MODE" to "stage2"),
            MoveFiles.fields("RUNNING=1 MODE=stage2\n"),
        )
        assertEquals(emptyMap<String, String>(), MoveFiles.fields("usage: dataresize.sh status|move"))
    }
}
