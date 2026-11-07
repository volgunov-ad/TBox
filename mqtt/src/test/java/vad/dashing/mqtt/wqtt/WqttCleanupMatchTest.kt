package vad.dashing.mqtt.wqtt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.mqtt.ha.Topics

class WqttCleanupMatchTest {

    @Test
    fun matchesTopicsUnderCurrentBase() {
        val base = Topics.base("tbox", "dashing")
        val detail = detail(
            id = 10,
            room = "Автомобиль",
            states = setOf("tbox/dashing/hvac_power/state"),
            cmds = setOf("tbox/dashing/hvac_power/set"),
        )
        assertTrue(WqttTboxDeviceMatch.matches(detail, base))
        assertTrue(WqttTboxDeviceMatch.topicUnderBase("tbox/dashing/outside_temperature/state", base))
        assertFalse(WqttTboxDeviceMatch.topicUnderBase("home/living/light/state", base))
    }

    @Test
    fun roomAloneDoesNotMatch() {
        val base = Topics.base("tbox", "dashing")
        val other = detail(
            id = 11,
            name = "Лампа",
            room = "Автомобиль",
            states = setOf("home/kitchen/lamp/state"),
            cmds = setOf("home/kitchen/lamp/set"),
        )
        assertFalse(WqttTboxDeviceMatch.matches(other, base))
        assertFalse(WqttTboxDeviceMatch.hasTopicUnderBase(other, base))
    }

    @Test
    fun customPrefixAndDeviceIdAreRespected() {
        val base = Topics.base("car", "jetour1")
        val detail = detail(
            id = 12,
            room = "Гараж",
            states = setOf("car/jetour1/speed/state"),
        )
        assertTrue(WqttTboxDeviceMatch.matches(detail, base))
        assertFalse(
            WqttTboxDeviceMatch.matches(
                detail,
                Topics.base("tbox", "dashing"),
            ),
        )
    }

    @Test
    fun knownIdsMatchWithoutTopics() {
        val base = Topics.base("tbox", "dashing")
        val detail = detail(
            id = 42,
            room = "Кухня",
            states = emptySet(),
            cmds = emptySet(),
        )
        assertFalse(WqttTboxDeviceMatch.matches(detail, base))
        assertTrue(WqttTboxDeviceMatch.matches(detail, base, knownIds = setOf(42)))
    }

    @Test
    fun findUsesTopicOrKnownIdAndSkipsUnrelated() {
        val api = FakeWqttApi(
            devices = listOf(
                WqttDeviceSummary(1, 2, "Климат", "Автомобиль"),
                WqttDeviceSummary(2, 2, "Лампа", "Автомобиль"),
                WqttDeviceSummary(3, 2, "Старое", "Гостиная"),
            ),
            details = mapOf(
                1 to detail(
                    id = 1,
                    name = "Климат",
                    room = "Автомобиль",
                    states = setOf("tbox/dashing/hvac_power/state"),
                    cmds = setOf("tbox/dashing/hvac_power/set"),
                ),
                2 to detail(
                    id = 2,
                    name = "Лампа",
                    room = "Автомобиль",
                    states = setOf("home/lamp/state"),
                    cmds = setOf("home/lamp/set"),
                ),
                3 to detail(
                    id = 3,
                    name = "Старое",
                    room = "Гостиная",
                    states = emptySet(),
                ),
            ),
        )
        val cleanup = WqttCleanup(api)
        val found = cleanup.findTboxDevices(
            token = "tok",
            topicPrefix = "tbox",
            deviceId = "dashing",
            knownIds = setOf(3),
        )
        assertEquals(listOf(1, 3), found.map { it.id })
        // Topic match fetches detail; known id skips the detail round-trip.
        assertEquals(setOf(1, 2), api.detailRequested)
        assertFalse(3 in api.detailRequested)
    }

    @Test
    fun deleteCallsRefreshAfterSuccess() {
        val api = FakeWqttApi(
            devices = emptyList(),
            details = emptyMap(),
        )
        val cleanup = WqttCleanup(api)
        val report = cleanup.deleteDevices(
            token = "tok",
            candidates = listOf(
                WqttCleanupCandidate(1, "Климат", "Автомобиль"),
                WqttCleanupCandidate(2, "Скорость", "Автомобиль"),
            ),
        )
        assertEquals(2, report.deleted)
        assertEquals(0, report.failed)
        assertTrue(report.refreshed)
        assertEquals(listOf(1, 2), api.deletedIds)
        assertEquals(1, api.refreshCount)
    }

    private fun detail(
        id: Int,
        name: String = "device",
        room: String = "",
        states: Set<String> = emptySet(),
        cmds: Set<String> = emptySet(),
    ): WqttDeviceDetail = WqttDeviceDetail(
        id = id,
        type = 2,
        name = name,
        room = room,
        topicStates = states,
        topicCmds = cmds,
    )

    private class FakeWqttApi(
        private val devices: List<WqttDeviceSummary>,
        private val details: Map<Int, WqttDeviceDetail>,
    ) : WqttApi(client = defaultClient(), baseUrl = "http://127.0.0.1") {
        val detailRequested = linkedSetOf<Int>()
        val deletedIds = mutableListOf<Int>()
        var refreshCount = 0

        override fun listDevices(token: String): List<WqttDeviceSummary> = devices

        override fun deviceDetail(token: String, id: Int): WqttDeviceDetail {
            detailRequested += id
            return details[id] ?: throw WqttApiException(404, "missing $id")
        }

        override fun deleteDevice(token: String, id: Int) {
            deletedIds += id
        }

        override fun refresh(token: String): Int {
            refreshCount += 1
            return 200
        }
    }
}
