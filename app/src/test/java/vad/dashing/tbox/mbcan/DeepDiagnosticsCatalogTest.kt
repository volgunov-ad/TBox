package vad.dashing.tbox.mbcan

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeepDiagnosticsCatalogTest {

    @Test
    fun `vhal catalog has no duplicate ids`() {
        val ids = DeepDiagnosticsCatalog.vhalPropertyIds
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `vhal catalog excludes write-only MFS pulse and request ids`() {
        val ids = DeepDiagnosticsCatalog.vhalPropertyIds
        assertFalse(FirmwareVehicleJsonMapper.VHAL_MFS_CRUISE_CONTROL in ids)
        assertFalse(FirmwareVehicleJsonMapper.VHAL_MFS_CANCEL in ids)
        assertFalse(FirmwareVehicleJsonMapper.VHAL_MFS_RES_PLUS in ids)
        assertFalse(FirmwareVehicleJsonMapper.VHAL_MFS_SET_MINUS in ids)
        assertFalse(FirmwareVehicleJsonMapper.VHAL_SLA_ON_OFF_REQ in ids)
    }

    @Test
    fun `vhal catalog includes telemetry, read translations and experimental ids`() {
        val ids = DeepDiagnosticsCatalog.vhalPropertyIds
        assertTrue(FirmwareVehicleJsonMapper.VHAL_ENGINE_RPM_PROPERTY_ID in ids)
        assertTrue(FirmwareVehicleJsonMapper.VHAL_CAR_SPEED_PROPERTY_ID in ids)
        val readPair = FirmwareVehicleJsonMapper.explicitReadEntries()
            .first { it.first == MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH }
        assertTrue(readPair.second in ids)
        assertTrue(289_411_329 in ids) // RadarCh1
        assertTrue(289_412_346 in ids) // WasherFluidLevel
    }

    @Test
    fun `vhal catalog includes door ajar and seatbelt experimental ids`() {
        val ids = DeepDiagnosticsCatalog.vhalPropertyIds
        assertTrue(289_412_271 in ids) // Cem2DriverDoorSts
        assertTrue(289_412_269 in ids) // Cem2HoodSts
        assertTrue(289_414_928 in ids) // Icm1DriverSeatBeltWarning
        assertEquals("Cem2DriverDoorSts", DeepDiagnosticsCatalog.annotateVhalPropertyId(289_412_271))
        assertEquals(
            "Icm1DriverSeatBeltWarning",
            DeepDiagnosticsCatalog.annotateVhalPropertyId(289_414_928),
        )
    }

    @Test
    fun `vhal catalog subscribes every firmware R_ read id`() {
        val ids = DeepDiagnosticsCatalog.vhalPropertyIds.toSet()
        assertEquals(374, VhalFirmwareReadIds.all.size)
        VhalFirmwareReadIds.all.forEach { (name, id) -> assertTrue(name, id in ids) }
        assertTrue(VhalFirmwareReadIds.all.all { it.first.startsWith("R_") })
    }

    @Test
    fun `firmware names annotate ids, short app names win`() {
        val (lowBeamName, lowBeamId) = VhalFirmwareReadIds.all.first { it.first.endsWith("_LowBeamSts") }
        assertEquals(289_412_250, lowBeamId)
        assertEquals("LowBeamSts", DeepDiagnosticsCatalog.annotateVhalPropertyId(lowBeamId))
        val fogFront = VhalFirmwareReadIds.all.first { it.first.contains("FrontFog") }
        assertNotNull(DeepDiagnosticsCatalog.annotateVhalPropertyId(fogFront.second))
        assertTrue(lowBeamName.startsWith("R_"))
    }

    @Test
    fun `callback methods map to data types with cb fallback`() {
        assertEquals("eMBCAN_VEHICLE_BCM_STATUS", DeepDiagnosticsCatalog.mbcanCallbackDataType("onVehicleBcmStatusChange"))
        assertEquals("eMBCAN_VEHICLE_WHEEL", DeepDiagnosticsCatalog.mbcanCallbackDataType("onPull"))
        assertEquals("cb.onSomethingNew", DeepDiagnosticsCatalog.mbcanCallbackDataType("onSomethingNew"))
    }

    @Test
    fun `object data types skip cfg and service channels`() {
        val names = DeepDiagnosticsCatalog.mbcanObjectDataTypes.map { it.first }
        assertTrue("eMBCAN_VEHICLE_BCM_STATUS" in names)
        assertTrue("eMBCAN_VEHICLE_EPB_STATUS" in names)
        assertFalse("eMBCAN_CFG_VEHICLE" in names)
        assertFalse("eMBCAN_UART_TEST_RESULT" in names)
        assertEquals(names.size, names.toSet().size)
        assertEquals(21, DeepDiagnosticsCatalog.mbcanObjectDataTypes.toMap()["eMBCAN_VEHICLE_BCM_STATUS"])
    }

    @Test
    fun `vhal annotations cover experimental ids`() {
        assertEquals("WasherFluidLevel", DeepDiagnosticsCatalog.annotateVhalPropertyId(289_412_346))
        assertEquals("RadarCh8", DeepDiagnosticsCatalog.annotateVhalPropertyId(289_411_336))
        assertNull(DeepDiagnosticsCatalog.annotateVhalPropertyId(123456789))
    }

    @Test
    fun `read translation annotation falls back to mbcan logical name`() {
        val vhalId = FirmwareVehicleJsonMapper.explicitReadEntries()
            .first { it.first == MbCanKnownVehiclePropertyId.WIPER_MAINTENANCE_SWITCH }
            .second
        assertNotNull(DeepDiagnosticsCatalog.annotateVhalPropertyId(vhalId))
    }

    @Test
    fun `mbcan data types contain production and experimental sets`() {
        assertTrue("eMBCAN_CFG_VEHICLE" in DeepDiagnosticsCatalog.mbcanDataTypes)
        assertTrue("eMBCAN_CFG_AUDIO" in DeepDiagnosticsCatalog.mbcanDataTypes)
        assertTrue("eMBCAN_VEHICLE_DOOR" in DeepDiagnosticsCatalog.mbcanDataTypes)
        assertTrue("eMBCAN_VEHICLE_ENGINE_GEAR" in DeepDiagnosticsCatalog.mbcanDataTypes)
        assertEquals(
            DeepDiagnosticsCatalog.mbcanProductionDataTypes.size +
                DeepDiagnosticsCatalog.mbcanExperimentalDataTypes.size,
            DeepDiagnosticsCatalog.mbcanDataTypes.size,
        )
    }

    @Test
    fun `mbcan cfg item annotation uses known vehicle and audio ids`() {
        assertEquals(
            "STEERING_WHEEL_HEAT_SWITCH",
            DeepDiagnosticsCatalog.annotateMbCanItem(MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH),
        )
        assertEquals(
            "VOLUME_RADAR",
            DeepDiagnosticsCatalog.annotateMbCanItem(MbCanKnownAudioPropertyId.VOLUME_RADAR),
        )
        // Documented collision: vehicle map wins (2 = DOOR_IGNOFF_UNLOCK vs audio VOLUME).
        assertEquals(
            "DOOR_IGNOFF_UNLOCK",
            DeepDiagnosticsCatalog.annotateMbCanItem(MbCanKnownAudioPropertyId.VOLUME),
        )
        assertNull(DeepDiagnosticsCatalog.annotateMbCanItem(999_999))
    }
}

class DeepCanDiagnosticsTest {

    private val lines = mutableListOf<Pair<String, String>>()
    private val defaultSink = DeepCanDiagnostics.sink

    @Before
    fun setUp() {
        DeepCanDiagnostics.reset()
        lines.clear()
        DeepCanDiagnostics.sink = { tag, line -> lines.add(tag to line) }
    }

    @After
    fun tearDown() {
        DeepCanDiagnostics.sink = defaultSink
        DeepCanDiagnostics.reset()
    }

    @Test
    fun `first value emits immediately, repeats are suppressed`() {
        assertTrue(DeepCanDiagnostics.decide("k", "1", nowMs = 0L, continuous = false) == 0L)
        assertNull(DeepCanDiagnostics.decide("k", "1", nowMs = 100L, continuous = false))
        assertNull(DeepCanDiagnostics.decide("k", "1", nowMs = 10_000L, continuous = false))
        assertTrue(DeepCanDiagnostics.stats().contains("suppressedTotal=2"))
    }

    @Test
    fun `changed value within discrete window is suppressed`() {
        DeepCanDiagnostics.decide("k", "1", nowMs = 0L, continuous = false)
        assertNull(DeepCanDiagnostics.decide("k", "2", nowMs = 500L, continuous = false))
        val suppressed = DeepCanDiagnostics.decide("k", "3", nowMs = 1_500L, continuous = false)
        assertNotNull(suppressed)
        assertEquals(1L, suppressed)
    }

    @Test
    fun `continuous window is longer than discrete`() {
        DeepCanDiagnostics.decide("speed", "10", nowMs = 0L, continuous = true)
        assertNull(DeepCanDiagnostics.decide("speed", "20", nowMs = 2_000L, continuous = true))
        assertNotNull(DeepCanDiagnostics.decide("speed", "30", nowMs = 6_000L, continuous = true))
    }

    @Test
    fun `recordMbCanCmdChanged formats machine readable line with annotation`() {
        DeepCanDiagnostics.recordMbCanCmdChanged(
            dataType = "eMBCAN_CFG_VEHICLE",
            modular = 2,
            rev = 0,
            item = MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH,
            value = 2,
        )
        assertEquals(1, lines.size)
        val (tag, line) = lines.first()
        assertEquals(DeepCanDiagnostics.MBCAN_TAG, tag)
        assertTrue(line.startsWith("mbcan dt=eMBCAN_CFG_VEHICLE modular=2 rev=0 item=188 value=2"))
        assertTrue(line.contains("name=STEERING_WHEEL_HEAT_SWITCH"))
        assertFalse(line.contains("ts="))
    }

    @Test
    fun `recordMbCanObjectSnapshot formats typed object line`() {
        DeepCanDiagnostics.recordMbCanObjectSnapshot(
            "eMBCAN_VEHICLE_DOOR",
            "FL=2 FR=1 RL=1 RR=1 trunk=1 hood=2 lock=1",
        )
        assertEquals(1, lines.size)
        val (tag, line) = lines.first()
        assertEquals(DeepCanDiagnostics.MBCAN_TAG, tag)
        assertTrue(line.contains("dt=eMBCAN_VEHICLE_DOOR"))
        assertTrue(line.contains("object=FL=2"))
    }

    @Test
    fun `recordMbCanObjectFields emits each field as its own delta key`() {
        val dt = "eMBCAN_VEHICLE_BCM_STATUS"
        DeepCanDiagnostics.recordMbCanObjectFields(dt, listOf("stLightSts.nHighBeamSts" to "1", "nWiperSts" to "0"))
        assertEquals(2, lines.size)
        assertEquals(DeepCanDiagnostics.MBCAN_TAG, lines[0].first)
        assertEquals("mbcan dt=$dt field=stLightSts.nHighBeamSts value=1", lines[0].second)
        DeepCanDiagnostics.recordMbCanObjectFields(dt, listOf("stLightSts.nHighBeamSts" to "1", "nWiperSts" to "0"))
        assertEquals(2, lines.size)
    }

    @Test
    fun `recordVhalEvent sanitizes whitespace and includes name`() {
        DeepCanDiagnostics.recordVhalEvent(
            propertyId = FirmwareVehicleJsonMapper.VHAL_CAR_SPEED_PROPERTY_ID,
            areaId = 0,
            value = 42,
            valueType = "java.lang.Integer",
            status = 0,
            timestampNanos = 123L,
        )
        assertEquals(1, lines.size)
        val (tag, line) = lines.first()
        assertEquals(DeepCanDiagnostics.VHAL_TAG, tag)
        assertTrue(line.contains("propertyId=${FirmwareVehicleJsonMapper.VHAL_CAR_SPEED_PROPERTY_ID}"))
        assertTrue(line.contains("name=CarSpeed"))
        assertTrue(line.contains("tsNanos=123"))
    }

    @Test
    fun `ring buffer keeps only last RING_BUFFER_LIMIT lines`() {
        repeat(DeepCanDiagnostics.RING_BUFFER_LIMIT + 120) { index ->
            DeepCanDiagnostics.report(DeepCanDiagnostics.VHAL_TAG, "line=$index")
        }
        val recent = DeepCanDiagnostics.recentLines()
        assertEquals(DeepCanDiagnostics.RING_BUFFER_LIMIT, recent.size)
        assertTrue(recent.last().endsWith("line=${DeepCanDiagnostics.RING_BUFFER_LIMIT + 119}"))
    }

    @Test
    fun `recordVhalError logs each property id once per session`() {
        DeepCanDiagnostics.recordVhalError(101, 0)
        DeepCanDiagnostics.recordVhalError(101, 0)
        DeepCanDiagnostics.recordVhalError(202, 3)
        val errorLines = DeepCanDiagnostics.recentLines().filter { it.startsWith("vhal error") }
        assertEquals(2, errorLines.size)
        assertTrue(errorLines.any { it.contains("propertyId=101") })
        assertTrue(errorLines.any { it.contains("propertyId=202 areaId=3") })
    }

    @Test
    fun `reset clears buffer and key states`() {
        DeepCanDiagnostics.recordMbCanCmdChanged("eMBCAN_CFG_VEHICLE", 2, 0, 188, 1)
        DeepCanDiagnostics.reset()
        assertTrue(DeepCanDiagnostics.recentLines().isEmpty())
        // After reset the same value emits again as a fresh key.
        DeepCanDiagnostics.recordMbCanCmdChanged("eMBCAN_CFG_VEHICLE", 2, 0, 188, 1)
        assertEquals(1, DeepCanDiagnostics.recentLines().size)
    }

    @Test
    fun `two areaIds of one property are independent keys`() {
        DeepCanDiagnostics.recordVhalEvent(289_415_169, 1, 20, "java.lang.Integer", 0, 1L)
        DeepCanDiagnostics.recordVhalEvent(289_415_169, 4, 20, "java.lang.Integer", 0, 2L)
        assertEquals(2, DeepCanDiagnostics.recentLines().size)
        assertNotEquals(
            DeepCanDiagnostics.recentLines()[0],
            DeepCanDiagnostics.recentLines()[1],
        )
    }
}
