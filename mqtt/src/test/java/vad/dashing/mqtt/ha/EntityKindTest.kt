package vad.dashing.mqtt.ha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityKindTest {
    @Test
    fun readableAndWritableSignalIsOneRow() {
        val rows = buildEntities(CATALOG)
        val drive = rows.single { it.objectId == "drive_mode" }
        assertEquals(HaComponent.SELECT, drive.component)
        assertEquals("head_unit", drive.source)
        assertTrue(drive.writable)
        assertEquals(listOf("ECO", "NOR", "SPT"), drive.options)
        assertEquals(0.0, drive.pairs.first { it.state == "NOR" }.numberValue!!, 0.0)
        assertNull(rows.find { it.objectId.startsWith("can_") && it.label.contains("вождения") })
    }

    @Test
    fun hiddenCommandsAndPlainSensorsStayInTheirGroups() {
        val rows = buildEntities(CATALOG, listOf(AutomationRow("abc", "Прогрев")))
        assertEquals(HaComponent.SENSOR, rows.single { it.objectId == "outside_temperature" }.component)
        assertEquals(HaComponent.SWITCH, rows.single { it.objectId == "hvac_power" }.component)
        assertEquals(HaComponent.DEVICE_TRACKER, rows.single { it.objectId == "geo_position" }.component)
        assertEquals(HaComponent.BUTTON, rows.single { it.objectId == "builtin_media_next" }.component)
        assertEquals(HaComponent.BUTTON, rows.single { it.objectId == "automation_abc" }.component)
        assertTrue(rows.none { it.objectId.contains("adb") })
        assertTrue(rows.none { it.objectId.contains("restart_tbox") })
        assertEquals(2, rows.count { it.objectId.startsWith("can_vehicle_99_") })
        assertEquals(EntityGroup.CLIMATE, rows.single { it.objectId == "hvac_power" }.group)
        assertEquals(EntityGroup.TRIP, rows.single { it.objectId == "geo_position" }.group)
    }

    @Test
    fun selectListsReadOnlyStatesSoHaKeepsTheValue() {
        val catalog = """
            {
              "signals": [
                {"id": "window_fl", "label": "Окно", "valueType": "state", "sources": ["head_unit"],
                 "stateOptions": ["closed", "open", "vent", "opened"]}
              ],
              "actionTypes": [
                {"type": "can_command", "bus": "vehicle", "propertyId": 5, "label": "Окно",
                 "safety": "confirm", "signalId": "window_fl",
                 "write": {"kind": "options", "options": [
                   {"state": "closed", "value": "close"},
                   {"state": "vent", "value": "vent"},
                   {"state": "opened", "value": "open"}
                 ]}}
              ]
            }
        """.trimIndent()
        val window = buildEntities(catalog).single()
        assertEquals(listOf("closed", "open", "vent", "opened"), window.options)
        assertEquals(3, window.pairs.size)
    }

    private companion object {
        val CATALOG = """
            {
              "signals": [
                {
                  "id": "outside_temperature",
                  "label": "Температура снаружи",
                  "unit": "°C",
                  "valueType": "number",
                  "sources": ["head_unit", "tbox"],
                  "stateOptions": []
                },
                {
                  "id": "hvac_power",
                  "label": "Питание климата",
                  "unit": "",
                  "valueType": "state",
                  "sources": ["head_unit"],
                  "stateOptions": ["off", "on"]
                },
                {
                  "id": "drive_mode",
                  "label": "Режим вождения",
                  "unit": "",
                  "valueType": "state",
                  "sources": ["head_unit"],
                  "stateOptions": ["ECO", "NOR", "SPT"]
                },
                {
                  "id": "geo_position",
                  "label": "Геопозиция",
                  "unit": "",
                  "valueType": "position",
                  "sources": ["app"],
                  "stateOptions": []
                }
              ],
              "actionTypes": [
                {
                  "type": "can_command",
                  "bus": "vehicle",
                  "propertyId": 10,
                  "label": "Режим вождения",
                  "safety": "confirm",
                  "signalId": "drive_mode",
                  "write": {
                    "kind": "options",
                    "options": [
                      {"state": "ECO", "value": 2},
                      {"state": "NOR", "value": 0},
                      {"state": "SPT", "value": 1}
                    ]
                  }
                },
                {
                  "type": "can_command",
                  "bus": "vehicle",
                  "propertyId": 11,
                  "label": "Питание климата",
                  "safety": "confirm",
                  "signalId": "hvac_power",
                  "write": {
                    "kind": "binary",
                    "options": [
                      {"state": "off", "value": "off"},
                      {"state": "on", "value": "on"}
                    ]
                  }
                },
                {
                  "type": "can_command",
                  "bus": "vehicle",
                  "propertyId": 99,
                  "label": "Багажник",
                  "safety": "confirm",
                  "signalId": null,
                  "write": {
                    "kind": "pulse",
                    "options": [
                      {"state": "open", "value": "open"},
                      {"state": "close", "value": "close"}
                    ]
                  }
                },
                {"type": "builtin", "actionType": "media_next", "safety": "safe"},
                {"type": "builtin", "actionType": "restart_tbox", "safety": "confirm"},
                {"type": "builtin", "actionType": "adb_shell", "safety": "dangerous"},
                {"type": "launch_application", "safety": "confirm"},
                {"type": "http_request", "safety": "confirm"},
                {"type": "delay", "safety": "safe"}
              ]
            }
        """.trimIndent()
    }
}
