package vad.dashing.store.disk

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MailRuCloudClientTest {

    @Test
    fun toWeblinkPath_joinsDiskPath() {
        val client = MailRuCloudClient("voi3/4Lx1RhqV9")
        assertEquals(
            "voi3/4Lx1RhqV9/release/apps/catalog.json",
            client.toWeblinkPath("/release/apps/catalog.json"),
        )
        assertEquals(
            "voi3/4Lx1RhqV9/dev/apps/mqtt/icon.png",
            client.toWeblinkPath("dev/apps/mqtt/icon.png"),
        )
        assertEquals(
            "voi3/4Lx1RhqV9",
            client.toWeblinkPath("/"),
        )
    }

    @Test
    fun cloudSource_defaultsToAuto() {
        assertEquals(CloudSource.AUTO, CloudSource.fromStorageValue(null))
        assertEquals(CloudSource.MAILRU, CloudSource.fromStorageValue("mailru"))
        assertEquals(CloudSource.YANDEX, CloudSource.fromStorageValue("yandex"))
    }
}
