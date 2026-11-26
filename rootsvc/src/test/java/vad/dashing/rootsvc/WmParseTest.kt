package vad.dashing.rootsvc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.rootsvc.client.WmStackListParser

class WmParseTest {
    @Test
    fun parseFormattedStackList() {
        val text = """
            task id=406 stack=4 pkg=vad.dashing.tbox activity=vad.dashing.tbox.MainActivity mode=freeform bounds=344,37,1920,1000 visible=1
            task id=402 stack=0 pkg=ras.dashing.tbox.launcher activity=ras.dashing.tbox.launcher.LauncherHomeActivity mode=fullscreen bounds=0,0,1920,1080 visible=1
        """.trimIndent()
        val tasks = WmStackListParser.parse(text)
        assertEquals(2, tasks.size)
        assertEquals(406, tasks[0].id)
        assertEquals("vad.dashing.tbox", tasks[0].pkg)
        assertEquals(344, tasks[0].left)
        assertTrue(tasks[0].visible)
        assertEquals("ras.dashing.tbox.launcher", tasks[1].pkg)
    }
}
