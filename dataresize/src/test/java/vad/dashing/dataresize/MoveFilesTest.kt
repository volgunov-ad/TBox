package vad.dashing.dataresize

import org.junit.Assert.assertEquals
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
}
