package vad.dashing.camd

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CamdCommandTest {
    @Test
    fun parsesOpenCloseAndStatus() {
        val open = CamdCommands.parse("OPEN 0 0.0200 0.7000 0.2800 0.2800")
        assertEquals(CamdCommand.Open(0, 0.02f, 0.70f, 0.28f, 0.28f), open)
        assertEquals(
            CamdCommand.Open(4, 0.02f, 0.55f, 0.40f, 0.40f, 90),
            CamdCommands.parse("OPEN 4 0.0200 0.5500 0.4000 0.4000 90"),
        )
        // Arbitrary integer angles 0..360 are accepted now (360 ≡ 0).
        assertEquals(
            CamdCommand.Open(4, 0.02f, 0.55f, 0.40f, 0.40f, 45),
            CamdCommands.parse("OPEN 4 0.0200 0.5500 0.4000 0.4000 45"),
        )
        assertEquals(
            CamdCommand.Open(4, 0.02f, 0.55f, 0.40f, 0.40f, 0),
            CamdCommands.parse("OPEN 4 0.0200 0.5500 0.4000 0.4000 360"),
        )
        assertNull(CamdCommands.parse("OPEN 4 0.0200 0.5500 0.4000 0.4000 361"))
        assertNull(CamdCommands.parse("OPEN 4 0.0200 0.5500 0.4000 0.4000 -90"))
        assertNull(CamdCommands.parse("OPEN 4 0.0200 0.5500 0.4000 0.4000 45.5"))
        assertEquals(CamdCommand.CloseAll, CamdCommands.parse("close"))
        assertEquals(CamdCommand.Close(4), CamdCommands.parse("CLOSE 4"))
        assertEquals(CamdCommand.Status, CamdCommands.parse("STATUS"))
        assertEquals(CamdCommand.SetKeepAvm(true), CamdCommands.parse("AVM ON"))
        assertEquals(CamdCommand.SetKeepAvm(false), CamdCommands.parse("avm off"))
        assertEquals(CamdCommand.SetRecorderEnabled(true), CamdCommands.parse("REC ON"))
        assertEquals(CamdCommand.SetRecorderEnabled(false), CamdCommands.parse("rec off"))
        assertEquals(CamdCommand.SetRecorderEnabled(true), CamdCommands.parse("REC 1"))
        assertEquals(CamdCommand.SetRecorderEnabled(false), CamdCommands.parse("REC 0"))
        assertEquals(CamdCommand.RecorderEmergencySave, CamdCommands.parse("REC SOS"))
        assertEquals(CamdCommand.RecorderEmergencySave, CamdCommands.parse("rec save"))
        assertNull(CamdCommands.parse("REC"))
        assertNull(CamdCommands.parse("REC YES"))
        assertNull(CamdCommands.parse("REC SOS extra"))
        assertNull(CamdCommands.parse("AVM"))
    }

    @Test
    fun parsesOpenWithOptionalFlips_6to9Tokens() {
        assertEquals(
            CamdCommand.Open(8, 0.02f, 0.55f, 0.40f, 0.40f, 0, flipH = false, flipV = false),
            CamdCommands.parse("OPEN 8 0.0200 0.5500 0.4000 0.4000"),
        )
        assertEquals(
            CamdCommand.Open(8, 0.02f, 0.55f, 0.40f, 0.40f, 0, flipH = false, flipV = false),
            CamdCommands.parse("OPEN 8 0.0200 0.5500 0.4000 0.4000 0"),
        )
        assertEquals(
            CamdCommand.Open(8, 0.02f, 0.55f, 0.40f, 0.40f, 0, flipH = true, flipV = false),
            CamdCommands.parse("OPEN 8 0.0200 0.5500 0.4000 0.4000 0 1"),
        )
        assertEquals(
            CamdCommand.Open(8, 0.02f, 0.55f, 0.40f, 0.40f, 90, flipH = true, flipV = true),
            CamdCommands.parse("OPEN 8 0.0200 0.5500 0.4000 0.4000 90 1 1"),
        )
        assertEquals(
            CamdCommand.Open(8, 0.02f, 0.55f, 0.40f, 0.40f, 0, flipH = false, flipV = true),
            CamdCommands.parse("OPEN 8 0.0200 0.5500 0.4000 0.4000 0 0 1"),
        )
        // "1" is a valid rotation now; flips still need the rotation slot (0..360).
        assertEquals(
            CamdCommand.Open(8, 0.02f, 0.55f, 0.40f, 0.40f, 1),
            CamdCommands.parse("OPEN 8 0.0200 0.5500 0.4000 0.4000 1"),
        )
        assertNull(CamdCommands.parse("OPEN 8 0.0200 0.5500 0.4000 0.4000 0 2"))
        assertNull(CamdCommands.parse("OPEN 8 0.0200 0.5500 0.4000 0.4000 0 1 1 0"))
    }

    @Test
    fun openDefaultsHaveNoFlipAndNoCrop() {
        val open = CamdCommands.parse("OPEN 0 0.1 0.1 0.2 0.2") as CamdCommand.Open
        assertFalse(open.flipH)
        assertFalse(open.flipV)
        assertEquals(0, open.rotation)
        assertNull(open.crop)
    }

    @Test
    fun parsesOpenWithOptionalCropSuffix() {
        assertEquals(
            CamdCommand.Open(8, 0.02f, 0.55f, 0.40f, 0.40f, crop = CamdCrop(0.25f, 0.10f, 0.50f, 0.40f)),
            CamdCommands.parse("OPEN 8 0.0200 0.5500 0.4000 0.4000 crop=0.2500,0.1000,0.5000,0.4000"),
        )
        assertEquals(
            CamdCommand.Open(8, 0.02f, 0.55f, 0.40f, 0.40f, 90, flipH = true, flipV = true, crop = CamdCrop(0f, 0f, 1f, 0.5f)),
            CamdCommands.parse("OPEN 8 0.0200 0.5500 0.4000 0.4000 90 1 1 crop=0,0,1,0.5"),
        )
        // The prefix is case-insensitive and the token may sit anywhere in the line.
        assertEquals(
            CamdCommand.Open(4, 0.02f, 0.55f, 0.40f, 0.40f, 0, flipH = false, flipV = false, crop = CamdCrop(0.1f, 0.1f, 0.8f, 0.8f)),
            CamdCommands.parse("OPEN CROP=0.1,0.1,0.8,0.8 4 0.0200 0.5500 0.4000 0.4000"),
        )
        assertEquals(
            CamdCommand.Open(8, 0.02f, 0.55f, 0.40f, 0.40f, crop = CamdCrop(0.02f, 0.01f, 1f, 1f)),
            CamdCommands.parse("OPEN 8 0.0200 0.5500 0.4000 0.4000 crop=0.02,0.01,1.0,1.0"),
        )
    }

    @Test
    fun rejectsBadCropTokens() {
        assertNull(CamdCommands.parse("OPEN 8 0.02 0.55 0.40 0.40 crop=0.1,0.1,0.8"))
        assertNull(CamdCommands.parse("OPEN 8 0.02 0.55 0.40 0.40 crop=0.1,0.1,0.8,0.8,0.1"))
        assertNull(CamdCommands.parse("OPEN 8 0.02 0.55 0.40 0.40 crop=0.1,0.1,0.8,x"))
        assertNull(CamdCommands.parse("OPEN 8 0.02 0.55 0.40 0.40 crop=0.1,0.1,0.8,-0.2"))
        assertNull(CamdCommands.parse("OPEN 8 0.02 0.55 0.40 0.40 crop=0.1,0.1,0.8,1.2"))
        assertNull(CamdCommands.parse("OPEN 8 0.02 0.55 0.40 0.40 crop=0.1,0.1,0.04,0.8"))
        assertNull(CamdCommands.parse("OPEN 8 0.02 0.55 0.40 0.40 crop=0.1,0.1,0.8,0.04"))
        assertNull(CamdCommands.parse("OPEN 8 0.02 0.55 0.40 0.40 crop=0.5,0.1,0.9,0.8"))
        assertNull(CamdCommands.parse("OPEN 8 0.02 0.55 0.40 0.40 crop=0.1,0.1,0.8,0.8 crop=0,0,1,1"))
        assertNull(CamdCommands.parse("OPEN 8 0.02 0.55 0.40 0.40 0 0 0 crop=0.1,0.1,0.8,0.8 9"))
        assertNull(CamdCommands.parse("OPEN 8 crop=x"))
    }

    @Test
    fun parsesOpenAsIsAndShapeToken() {
        assertEquals(
            CamdCommand.Open(8, 0.02f, 0.55f, 0.40f, 0.40f),
            CamdCommands.parse("OPEN 8 0.0200 0.5500 0.4000 0.4000"),
        )
        assertEquals(
            CamdCommand.Open(8, 0.02f, 0.55f, 0.40f, 0.40f, shape = CamdShape.CIRCLE),
            CamdCommands.parse("OPEN 8 0.02 0.55 0.40 0.40 SHAPE=circle"),
        )
        assertEquals(
            CamdCommand.Open(
                4,
                0.02f,
                0.55f,
                0.40f,
                0.40f,
                90,
                flipH = true,
                flipV = false,
                crop = CamdCrop(0f, 0f, 1f, 0.5f),
                shape = CamdShape.CIRCLE,
            ),
            CamdCommands.parse("OPEN 4 0.02 0.55 0.40 0.40 90 1 crop=0,0,1,0.5 shape=circle"),
        )
        assertNull(CamdCommands.parse("OPEN 8 0.02 0.55 0.40 0.40 shape=oval"))
        assertNull(CamdCommands.parse("OPEN 8 0.02 0.55 0.40 0.40 shape=circle shape=circle"))
    }

    @Test
    fun parsesPresetFormWithoutIdAndRejectsMixing() {
        assertEquals(
            CamdCommand.Open(-1, 0.1f, 0.2f, 0.3f, 0.3f, presetName = "front_left", shape = CamdShape.CIRCLE),
            CamdCommands.parse("OPEN preset=front_left 0.1 0.2 0.3 0.3 shape=circle"),
        )
        // Preset settings cannot be overridden, and there is no id slot in this form.
        assertNull(CamdCommands.parse("OPEN preset=front_left 0.1 0.2 0.3 0.3 90"))
        assertNull(CamdCommands.parse("OPEN preset=front_left 0.1 0.2 0.3 0.3 crop=0,0,1,1"))
        assertNull(CamdCommands.parse("OPEN 4 preset=front_left 0.1 0.2 0.3 0.3"))
        assertNull(CamdCommands.parse("OPEN preset=Bad-Name 0.1 0.2 0.3 0.3"))
        assertNull(CamdCommands.parse("OPEN preset=front_left"))
    }

    @Test
    fun parsesPresetsCommand() {
        assertEquals(CamdCommand.ListPresets, CamdCommands.parse("PRESETS"))
        assertEquals(CamdCommand.ListPresets, CamdCommands.parse("presets"))
        assertNull(CamdCommands.parse("PRESETS 4"))
        assertNull(CamdCommands.parse("STATUS extra"))
    }

    @Test
    fun rejectsUnknownInputAndAWindowThatLeavesTheScreen() {
        assertNull(CamdCommands.parse("OPEN 2 0.1 0.1 0.2 0.2"))
        assertNull(CamdCommands.parse("OPEN 0 0.9 0.1 0.3 0.2"))
        assertNull(CamdCommands.parse("OPEN 0 0 0 0.01 0.2"))
        assertNull(CamdCommands.parse("OPEN 39 0.1 0.1 0.2 0.2"))
        assertNull(CamdCommands.parse("OPEN 46 0.1 0.1 0.2 0.2"))
        assertNull(CamdCommands.parse("MOVE 0"))
    }

    @Test
    fun parsesStockSourceIds() {
        assertEquals(
            CamdCommand.Open(40, 0.02f, 0.55f, 0.40f, 0.40f),
            CamdCommands.parse("OPEN 40 0.0200 0.5500 0.4000 0.4000"),
        )
        assertEquals(
            CamdCommand.Open(45, 0.1f, 0.2f, 0.3f, 0.3f, 90, flipH = true, flipV = false),
            CamdCommands.parse("OPEN 45 0.1 0.2 0.3 0.3 90 1"),
        )
    }
}

class CamdLensCorrectionTest {
    @Test
    fun clampAndIdentity() {
        assertTrue(CamdLensCorrection.IDENTITY.isIdentity())
        assertFalse(CamdLensCorrection.PRESET_MILD_BARREL.isIdentity())
        val clamped = CamdLensCorrection.clamp(
            CamdLensCorrection(k1 = 5f, k2 = -5f, cx = 1f, cy = -1f),
        )
        assertEquals(1f, clamped.k1, 0f)
        assertEquals(-1f, clamped.k2, 0f)
        assertEquals(0.25f, clamped.cx, 0f)
        assertEquals(-0.25f, clamped.cy, 0f)
    }
}

class CamdCropTest {
    @Test
    fun identityAndValidity() {
        assertTrue(CamdCrop.IDENTITY.isIdentity())
        assertTrue(CamdCrop.isValid(CamdCrop.IDENTITY))
        assertFalse(CamdCrop(0.1f, 0.1f, 0.8f, 0.8f).isIdentity())
        assertTrue(CamdCrop.isValid(CamdCrop(0.02f, 0.01f, 1f, 1f)))
        assertFalse(CamdCrop.isValid(CamdCrop(0.1f, 0.1f, 0.04f, 0.8f)))
        assertFalse(CamdCrop.isValid(CamdCrop(0.5f, 0.1f, 0.9f, 0.8f)))
        assertFalse(CamdCrop.isValid(CamdCrop(-0.1f, 0f, 0.5f, 0.5f)))
    }

    @Test
    fun clampKeepsTheRectInsideTheFrameAndRespectsMinSize() {
        assertEquals(CamdCrop.IDENTITY, CamdCrop.clamp(CamdCrop.IDENTITY))
        assertEquals(
            CamdCrop(0.2f, 0.3f, 0.8f, 0.7f),
            CamdCrop.clamp(CamdCrop(0.2f, 0.3f, 0.8f, 0.7f)),
        )
        // Right/bottom overflow shifts the origin back so size wins.
        assertEquals(
            CamdCrop(0.2f, 0.3f, 0.8f, 0.7f),
            CamdCrop.clamp(CamdCrop(0.5f, 0.6f, 0.8f, 0.7f)),
        )
        assertEquals(
            CamdCrop(0.95f, 0f, CamdCrop.MIN_SIZE, 1f),
            CamdCrop.clamp(CamdCrop(1f, -1f, 0.04f, 9f)),
        )
    }
}

class CamdJsonTest {
    @Test
    fun writesAndParsesRoundTrip() {
        val value = CamdJson.obj(
            listOf(
                "name" to CamdJson.str("a\"b\\c\n"),
                "n" to CamdJson.num(3),
                "f" to CamdJson.num(0.25f),
                "b" to CamdJson.bool(true),
                "list" to CamdJson.arr(listOf(CamdJson.num(1), CamdJson.Null)),
            )
        )
        assertEquals(value, CamdJson.parse(CamdJson.write(value)))
    }

    @Test
    fun parseRejectsBrokenDocuments() {
        assertNull(CamdJson.parse("{"))
        assertNull(CamdJson.parse("{\"a\":1}x"))
        assertNull(CamdJson.parse("[1,]"))
        assertNull(CamdJson.parse("nul"))
        assertNull(CamdJson.parse(""))
    }

    @Test
    fun parsesSpacesNumbersAndUnicodeEscapes() {
        val parsed = CamdJson.parse("{ \"a\" : \"\\u0041\" , \"b\" : [ 1.5 , 2e2 ] }")!!
        val fields = parsed.asObj()!!
        assertEquals("A", fields["a"]?.asStr())
        assertEquals(1.5, fields["b"]?.asArr()!![0].asNum()!!, 0.0)
        assertEquals(200.0, fields["b"]?.asArr()!![1].asNum()!!, 0.0)
    }
}

class CamdPresetStoreTest {
    @Test
    fun builtinDefaultsCoverAllCamerasAndMergeOverrides() {
        val prefs = FakePrefs()
        val all = CamdPresetStore.all(prefs)
        assertEquals(27, all.size)
        assertTrue(all.all { it.builtin })
        val dclNames = setOf(
            "dcl_left_blind_zone",
            "dcl_left_side",
            "dcl_right_blind_zone",
            "dcl_right_side",
            "dcl_front",
            "dcl_rear",
        )
        assertTrue(all.filter { it.name !in dclNames }.all { it.defaultCorrection })
        assertTrue(all.filter { it.name in dclNames }.all { !it.defaultCorrection })
        assertEquals(CamdCrop(0f, 0f, 0.5f, 1f), CamdPresetStore.byName(prefs, "front_left")!!.crop)
        assertTrue(
            CamdPresetStore.saveBuiltinOverride(
                prefs,
                "front_left",
                180,
                true,
                false,
                CamdCrop(0.1f, 0.2f, 0.8f, 0.7f),
                defaultCorrection = false,
                correction = CamdLensCorrection(k1 = -0.2f, tiltV = 0.3f),
            )
        )
        val edited = CamdPresetStore.byName(prefs, "front_left")!!
        assertEquals(180, edited.rotation)
        assertTrue(edited.flipH)
        assertFalse(edited.flipV)
        // Crop must persist too: the override value differs from the built-in default.
        assertEquals(CamdCrop(0.1f, 0.2f, 0.8f, 0.7f), edited.crop)
        assertFalse(edited.defaultCorrection)
        assertEquals(CamdLensCorrection(k1 = -0.2f, tiltV = 0.3f), edited.correction)
        assertEquals(0, CamdPresetStore.byName(prefs, "front_right")!!.rotation)
        assertEquals(false, CamdPresetStore.saveBuiltinOverride(
            prefs, "nope", 0, false, false, CamdCrop.IDENTITY, true, CamdLensCorrection.IDENTITY,
        ))
    }

    @Test
    fun normalizeRotationWrapsAnyAngle() {
        assertEquals(0, CamdPreset.normalizeRotation(0))
        assertEquals(45, CamdPreset.normalizeRotation(45))
        assertEquals(359, CamdPreset.normalizeRotation(-1))
        assertEquals(10, CamdPreset.normalizeRotation(370))
        assertEquals(0, CamdPreset.normalizeRotation(360))
        assertEquals(270, CamdPreset.normalizeRotation(-90))
    }

    @Test
    fun userPresetsValidateNamesAndUniqueness() {
        val prefs = FakePrefs()
        assertEquals("bad_name", CamdPresetStore.saveUser(prefs, CamdPreset("Bad-1", 8)))
        assertEquals("unknown_camera", CamdPresetStore.saveUser(prefs, CamdPreset("ok_name", 2)))
        assertNull(CamdPresetStore.saveUser(prefs, CamdPreset("my_corner", 4, rotation = 90)))
        assertEquals("name_taken", CamdPresetStore.saveUser(prefs, CamdPreset("front_left", 8)))
        val saved = CamdPresetStore.byName(prefs, "my_corner")!!
        assertEquals(4, saved.cameraId)
        assertFalse(saved.builtin)
        assertTrue(saved.defaultCorrection)
        assertNull(
            CamdPresetStore.saveUser(
                prefs,
                CamdPreset(
                    "my_corner",
                    5,
                    defaultCorrection = false,
                    correction = CamdLensCorrection(k2 = 0.1f, cx = 0.05f),
                ),
            )
        )
        val edited = CamdPresetStore.byName(prefs, "my_corner")!!
        assertEquals(5, edited.cameraId)
        assertFalse(edited.defaultCorrection)
        assertEquals(CamdLensCorrection(k2 = 0.1f, cx = 0.05f), edited.correction)
        assertTrue(CamdPresetStore.deleteUser(prefs, "my_corner"))
        assertEquals(null, CamdPresetStore.byName(prefs, "my_corner"))
        assertFalse(CamdPresetStore.deleteUser(prefs, "my_corner"))
    }

    @Test
    fun stockPresetsTargetStockSources() {
        val stock = CamdBuiltinPresets.all.filter { CamdSources.isStock(it.cameraId) }
        assertEquals(
            listOf(
                "stock_front" to 40,
                "stock_right" to 41,
                "stock_rear" to 42,
                "stock_left" to 43,
                "stock_wide_front" to 44,
                "stock_wide_rear" to 45,
            ),
            stock.map { it.name to it.cameraId },
        )
        assertTrue(stock.all { it.builtin && it.rotation == 0 && it.crop.isIdentity() })
    }

    @Test
    fun userPresetsMayTargetStockSources() {
        val prefs = FakePrefs()
        assertNull(CamdPresetStore.saveUser(prefs, CamdPreset("my_stock", 42, rotation = 90)))
        val saved = CamdPresetStore.byName(prefs, "my_stock")!!
        assertEquals(42, saved.cameraId)
        assertFalse(saved.builtin)
        assertEquals("unknown_camera", CamdPresetStore.saveUser(prefs, CamdPreset("bad_stock", 46)))
    }

    @Test
    fun decodePresetsSavedWithoutCorrectionFieldsDefaultToCameraCorrections() {
        val legacy = CamdJson.obj(
            listOf(
                "name" to CamdJson.str("old_entry"),
                "camera" to CamdJson.num(4),
            )
        )
        val decoded = CamdPresetStore.decodePreset(legacy, builtinDefault = false)!!
        assertTrue(decoded.defaultCorrection)
        assertEquals(CamdLensCorrection.IDENTITY, decoded.correction)
    }
}

class CamdBackupTest {
    @Test
    fun exportImportRoundTrip() {
        val prefs = FakePrefs()
        CamdLensCorrectionStore.save(prefs, 8, CamdLensCorrection(k1 = -0.2f, tiltV = 0.3f))
        CamdPresetStore.saveUser(prefs, CamdPreset("my_corner", 4, rotation = 90))
        CamdPresetStore.saveBuiltinOverride(
            prefs,
            "front_left",
            180,
            true,
            false,
            CamdCrop(0f, 0f, 0.5f, 1f),
            defaultCorrection = false,
            correction = CamdLensCorrection(k1 = -0.2f),
        )
        prefs.edit()
            .putBoolean(CameraWindows.KEEP_AVM_STOPPED_KEY, true)
            .putBoolean(CameraWindows.SHOW_CLOSE_BUTTON_KEY, false)
            .putBoolean(CameraWindows.TAP_TO_CLOSE_KEY, true)
            .apply()

        val json = CamdBackup.exportJson(prefs)

        val fresh = FakePrefs()
        val summary = CamdBackup.importJson(fresh, json)!!
        assertEquals(7, summary.corrections)
        assertEquals(1, summary.userPresets)
        assertEquals(27, summary.builtinPresets)
        assertTrue(summary.keepAvm)
        assertEquals(CamdLensCorrection(k1 = -0.2f, tiltV = 0.3f), CamdLensCorrectionStore.load(fresh, 8))
        assertEquals(90, CamdPresetStore.byName(fresh, "my_corner")!!.rotation)
        assertEquals(180, CamdPresetStore.byName(fresh, "front_left")!!.rotation)
        assertTrue(fresh.getBoolean(CameraWindows.KEEP_AVM_STOPPED_KEY, false))
        assertFalse(fresh.getBoolean(CameraWindows.SHOW_CLOSE_BUTTON_KEY, true))
        assertTrue(fresh.getBoolean(CameraWindows.TAP_TO_CLOSE_KEY, false))
    }

    @Test
    fun backupRoundTripsStockSourcePresets() {
        val prefs = FakePrefs()
        assertNull(CamdPresetStore.saveUser(prefs, CamdPreset("my_stock", 44, rotation = 180)))
        val json = CamdBackup.exportJson(prefs)
        val fresh = FakePrefs()
        val summary = CamdBackup.importJson(fresh, json)!!
        assertEquals(1, summary.userPresets)
        assertEquals(44, CamdPresetStore.byName(fresh, "my_stock")!!.cameraId)
        assertEquals(180, CamdPresetStore.byName(fresh, "my_stock")!!.rotation)
    }

    @Test
    fun correctionsAcceptOnlyRawInputs() {
        val prefs = FakePrefs()
        val document = CamdJson.write(
            CamdJson.obj(
                listOf(
                    "version" to CamdJson.num(1),
                    "app" to CamdJson.str("camd"),
                    "corrections" to CamdJson.obj(
                        listOf("40" to CamdJson.obj(listOf("k1" to CamdJson.num(0.1f))))
                    ),
                    "presets" to CamdJson.arr(emptyList()),
                    "keepAvmStopped" to CamdJson.bool(false),
                )
            )
        )
        assertNull(CamdBackup.importJson(prefs, document))
    }

    @Test
    fun importKeepsCurrentValuesWhenOptionalFieldsAreAbsent() {
        val prefs = FakePrefs()
        prefs.edit()
            .putBoolean(CameraWindows.SHOW_CLOSE_BUTTON_KEY, false)
            .putBoolean(CameraWindows.TAP_TO_CLOSE_KEY, true)
            .apply()
        // Document without the window UI fields (older export).
        val oldDocument = CamdJson.write(
            CamdJson.obj(
                listOf(
                    "version" to CamdJson.num(1),
                    "app" to CamdJson.str("camd"),
                    "corrections" to CamdJson.obj(emptyList()),
                    "presets" to CamdJson.arr(emptyList()),
                    "keepAvmStopped" to CamdJson.bool(false),
                )
            )
        )
        val summary = CamdBackup.importJson(prefs, oldDocument)!!
        assertEquals(0, summary.corrections)
        assertFalse(summary.keepAvm)
        assertFalse(prefs.getBoolean(CameraWindows.SHOW_CLOSE_BUTTON_KEY, true))
        assertTrue(prefs.getBoolean(CameraWindows.TAP_TO_CLOSE_KEY, false))
    }

    @Test
    fun importRejectsForeignBrokenAndAmbiguousDocuments() {
        val prefs = FakePrefs()
        assertNull(CamdBackup.importJson(prefs, "not json"))
        assertNull(CamdBackup.importJson(prefs, "{}"))
        assertNull(
            CamdBackup.importJson(
                prefs,
                CamdJson.write(CamdJson.obj(listOf("version" to CamdJson.num(1), "app" to CamdJson.str("other")))),
            )
        )
        assertNull(
            CamdBackup.importJson(
                prefs,
                CamdJson.write(CamdJson.obj(listOf("version" to CamdJson.num(2), "app" to CamdJson.str("camd")))),
            )
        )
        assertNull(CamdBackup.importJson(prefs, documentWithDuplicatePresets()))
    }

    private fun documentWithDuplicatePresets(): String = CamdJson.write(
        CamdJson.obj(
            listOf(
                "version" to CamdJson.num(1),
                "app" to CamdJson.str("camd"),
                "corrections" to CamdJson.obj(emptyList()),
                "presets" to CamdJson.arr(listOf(presetJson("dup", 4), presetJson("dup", 8))),
                "keepAvmStopped" to CamdJson.bool(false),
            )
        )
    )

    private fun presetJson(name: String, camera: Int): CamdJson = CamdJson.obj(
        listOf(
            "name" to CamdJson.str(name),
            "camera" to CamdJson.num(camera),
        )
    )
}

class CamdPresetsBackupTest {
    @Test
    fun presetsExportImportRoundTrip() {
        val prefs = FakePrefs()
        CamdPresetStore.saveUser(prefs, CamdPreset("my_corner", 4, rotation = 90))
        CamdPresetStore.saveBuiltinOverride(
            prefs,
            "front_left",
            180,
            true,
            false,
            CamdCrop(0f, 0f, 0.5f, 1f),
            defaultCorrection = false,
            correction = CamdLensCorrection(k1 = -0.2f),
        )

        val json = CamdBackup.exportPresetsJson(prefs)

        val fresh = FakePrefs()
        val summary = CamdBackup.importPresetsJson(fresh, json)!!
        assertEquals(1, summary.userPresets)
        assertEquals(27, summary.builtinPresets)
        assertEquals(90, CamdPresetStore.byName(fresh, "my_corner")!!.rotation)
        assertEquals(180, CamdPresetStore.byName(fresh, "front_left")!!.rotation)
    }

    @Test
    fun presetsImportKeepsCorrectionsAndFlags() {
        val prefs = FakePrefs()
        CamdLensCorrectionStore.save(prefs, 8, CamdLensCorrection(k1 = -0.2f))
        prefs.edit()
            .putBoolean(CameraWindows.KEEP_AVM_STOPPED_KEY, true)
            .putBoolean(CameraWindows.SHOW_CLOSE_BUTTON_KEY, false)
            .apply()
        val other = FakePrefs()
        CamdPresetStore.saveUser(other, CamdPreset("my_corner", 4))
        val presets = CamdBackup.exportPresetsJson(other)

        val summary = CamdBackup.importPresetsJson(prefs, presets)!!

        assertEquals(1, summary.userPresets)
        // Corrections and flags are untouched by the presets-only import.
        assertEquals(CamdLensCorrection(k1 = -0.2f), CamdLensCorrectionStore.load(prefs, 8))
        assertTrue(prefs.getBoolean(CameraWindows.KEEP_AVM_STOPPED_KEY, false))
        assertFalse(prefs.getBoolean(CameraWindows.SHOW_CLOSE_BUTTON_KEY, true))
    }

    @Test
    fun presetsImportAcceptsFullBackupPresetsSection() {
        val prefs = FakePrefs()
        CamdLensCorrectionStore.save(prefs, 8, CamdLensCorrection(k1 = -0.2f, tiltV = 0.3f))
        CamdPresetStore.saveUser(prefs, CamdPreset("my_corner", 4, rotation = 90))
        prefs.edit().putBoolean(CameraWindows.KEEP_AVM_STOPPED_KEY, true).apply()
        val fullBackup = CamdBackup.exportJson(prefs)

        val fresh = FakePrefs()
        val summary = CamdBackup.importPresetsJson(fresh, fullBackup)!!
        assertEquals(1, summary.userPresets)
        assertEquals(27, summary.builtinPresets)
        assertEquals(90, CamdPresetStore.byName(fresh, "my_corner")!!.rotation)
        // Corrections and flags are not applied by the presets-only import.
        assertEquals(CamdLensCorrection.IDENTITY, CamdLensCorrectionStore.load(fresh, 8))
        assertFalse(fresh.getBoolean(CameraWindows.KEEP_AVM_STOPPED_KEY, false))
    }

    @Test
    fun presetsImportRejectsForeignAndBrokenDocuments() {
        val prefs = FakePrefs()
        assertNull(CamdBackup.importPresetsJson(prefs, "not json"))
        assertNull(CamdBackup.importPresetsJson(prefs, "{}"))
        assertNull(
            CamdBackup.importPresetsJson(
                prefs,
                CamdJson.write(CamdJson.obj(listOf("version" to CamdJson.num(1), "app" to CamdJson.str("other")))),
            )
        )
        assertNull(
            CamdBackup.importPresetsJson(
                prefs,
                CamdJson.write(CamdJson.obj(listOf("version" to CamdJson.num(2), "app" to CamdJson.str("camd_presets")))),
            )
        )
        // A presets document without the presets array is rejected.
        assertNull(
            CamdBackup.importPresetsJson(
                prefs,
                CamdJson.write(CamdJson.obj(listOf("version" to CamdJson.num(1), "app" to CamdJson.str("camd_presets")))),
            )
        )
    }

    @Test
    fun presetsImportRejectsDuplicateNames() {
        val prefs = FakePrefs()
        val document = CamdJson.write(
            CamdJson.obj(
                listOf(
                    "version" to CamdJson.num(1),
                    "app" to CamdJson.str("camd_presets"),
                    "presets" to CamdJson.arr(listOf(presetEntry("dup", 4), presetEntry("dup", 8))),
                )
            )
        )
        assertNull(CamdBackup.importPresetsJson(prefs, document))
    }

    private fun presetEntry(name: String, camera: Int): CamdJson = CamdJson.obj(
        listOf(
            "name" to CamdJson.str(name),
            "camera" to CamdJson.num(camera),
        )
    )

    @Test
    fun presetsImportMergesIntoExistingPresets() {
        val prefs = FakePrefs()
        CamdPresetStore.saveUser(prefs, CamdPreset("kept", 8))
        val other = FakePrefs()
        CamdPresetStore.saveUser(other, CamdPreset("incoming", 4, rotation = 90))
        val json = CamdBackup.exportPresetsJson(other)

        val summary = CamdBackup.importPresetsJson(prefs, json)!!

        assertEquals(1, summary.userPresets)
        assertEquals(8, CamdPresetStore.byName(prefs, "kept")!!.cameraId)
        assertEquals(90, CamdPresetStore.byName(prefs, "incoming")!!.rotation)
    }
}

private class FakePrefs : SharedPreferences {
    val map = HashMap<String, Any?>()

    override fun getAll(): Map<String, *> = map
    override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        map[key] as? MutableSet<String> ?: defValues

    override fun getInt(key: String, defValue: Int): Int = map[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = map[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = map[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = map.containsKey(key)
    override fun edit(): SharedPreferences.Editor = FakeEditor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class FakeEditor : SharedPreferences.Editor {
        private val edits = HashMap<String, Any?>()
        private val removals = HashSet<String>()

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            edits[key] = value
            return this
        }

        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
            edits[key] = values
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            edits[key] = value
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            edits[key] = value
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            edits[key] = value
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            edits[key] = value
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            removals.add(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            edits.clear()
            removals.add("*")
            return this
        }

        override fun commit(): Boolean {
            applyChanges()
            return true
        }

        override fun apply() {
            applyChanges()
        }

        private fun applyChanges() {
            if (removals.contains("*")) {
                map.clear()
            } else {
                removals.forEach { map.remove(it) }
            }
            removals.clear()
            map.putAll(edits)
            edits.clear()
        }
    }
}

class GrabberScriptTest {
    @Test
    fun launcherStopsAvmBeforeTheGrabberAndReturns() {
        val text = GrabberScripts.launcher("n1", stopAvm = true)
        val stop = text.indexOf("setprop ctl.stop AVMapp")
        val process = text.indexOf("camd_grab")
        assertTrue(stop >= 0)
        assertTrue(process > stop)
        assertTrue(text.contains("appops set vad.dashing.camd SYSTEM_ALERT_WINDOW allow"))
        assertTrue(text.indexOf("SYSTEM_ALERT_WINDOW") < process)
        assertFalse(text.contains("\r"))
        assertTrue(text.trimEnd().endsWith("exit 0"))
    }

    @Test
    fun launcherKeepsAvmWhenTheCameraIsNotSurround() {
        val text = GrabberScripts.launcher("n2", stopAvm = false)
        assertFalse(text.contains("ctl.stop"))
        assertTrue(text.contains("exit 0"))
    }

    @Test
    fun startAvmWritesTheNonceBeforeExit() {
        val text = GrabberScripts.startAvm("nonce9")
        val echo = text.indexOf("echo nonce9")
        val exit = text.indexOf("exit 0")
        assertTrue(echo >= 0)
        assertTrue(exit > echo)
        assertTrue(text.contains("ctl.start AVMapp"))
    }

    @Test
    fun grantOverlayAllowsTheWindowBeforeExit() {
        val text = GrabberScripts.grantOverlay("nonce3")
        val grant = text.indexOf("appops set vad.dashing.camd SYSTEM_ALERT_WINDOW allow")
        val exit = text.indexOf("exit 0")
        assertTrue(grant >= 0)
        assertTrue(exit > grant)
        assertTrue(text.contains("echo nonce3"))
    }

    @Test
    fun grantAccessibilityAppendsAndNeverOverwrites() {
        val text = GrabberScripts.grantAccessibility("nonce7")
        val svc = "vad.dashing.camd/vad.dashing.camd.CamdAccessibilityService"
        val get = text.indexOf("cur=$(settings get secure enabled_accessibility_services")
        val put = text.indexOf("settings put secure enabled_accessibility_services")
        val enabled = text.indexOf("settings put secure accessibility_enabled 1")
        val exit = text.indexOf("exit 0")
        assertTrue(get >= 0)
        assertTrue(put > get)
        assertTrue(enabled > put)
        assertTrue(exit > enabled)
        assertTrue(text.contains("*\"$svc\"*) : ;;"))
        assertTrue(text.contains("null|\"\") cur=\"$svc\""))
        assertTrue(text.contains("*) cur=\"\$cur:$svc\" ;;"))
        assertTrue(text.contains("echo nonce7"))
        assertFalse(text.contains("\r"))
        assertTrue(text.trimEnd().endsWith("exit 0"))
    }

    @Test
    fun grantAccessibilityContainsNoSecondPutOfTheSameKey() {
        val text = GrabberScripts.grantAccessibility("n8")
        assertEquals(1, countOccurrences(text, "settings put secure enabled_accessibility_services"))
        assertEquals(1, countOccurrences(text, "settings put secure accessibility_enabled"))
    }

    private fun countOccurrences(text: String, needle: String): Int {
        var count = 0
        var index = text.indexOf(needle)
        while (index >= 0) {
            count++
            index = text.indexOf(needle, index + needle.length)
        }
        return count
    }

    @Test
    fun stopAvmDoesNotRestartTheGrabber() {
        val text = GrabberScripts.stopAvm("nonce4")
        assertTrue(text.contains("setprop ctl.stop AVMapp"))
        assertFalse(text.contains("camd_grab"))
        assertTrue(text.trimEnd().endsWith("exit 0"))
    }

    @Test
    fun launcherStampsTheGrabberVersion() {
        val text = GrabberScripts.launcher("n5", stopAvm = false)
        val copy = text.indexOf("cp ")
        val stamp = text.indexOf("echo ${GrabberScripts.GRAB_VERSION} > ${GrabberScripts.VERSION_FILE}")
        val launch = text.indexOf("camd_grab > ")
        assertTrue(copy >= 0)
        assertTrue(stamp > copy)
        assertTrue(launch > stamp)
        assertTrue(text.contains("chmod 666 ${GrabberScripts.VERSION_FILE}"))
    }
}

class CamdPortTest {
    @Test
    fun apiPortDoesNotCollideWithMonitorOrMqtt() {
        assertTrue(DaemonScripts.API_PORT != 8765)
        assertTrue(DaemonScripts.API_PORT != 8766)
    }
}

class GrabDesiredIdsTest {
    @Test
    fun mapsStockOpenIdsToPhysicalAndUnionsRecorder() {
        assertEquals(
            sortedSetOf(8, 4),
            GrabDesiredIds.compute(listOf(40, 43), emptyList()),
        )
        assertEquals(
            sortedSetOf(4, 5, 8),
            GrabDesiredIds.compute(listOf(40), listOf(4, 5)),
        )
        // Raw + stock of the same physical input collapse to one grabber id.
        assertEquals(
            sortedSetOf(8),
            GrabDesiredIds.compute(listOf(8, 40, 44), emptyList()),
        )
        assertEquals(
            emptySet<Int>(),
            GrabDesiredIds.compute(emptyList(), emptyList()),
        )
    }

    @Test
    fun ensureGrabberForUsesCallerInputsNotStalePrefsSemantics() {
        // ensureGrabberFor(inputs) writes desired via GrabDesiredIds.compute(open, inputs).
        // With no open windows, the caller's set alone must appear (P2-5).
        assertEquals(
            sortedSetOf(4, 9),
            GrabDesiredIds.compute(emptyList(), listOf(4, 9)),
        )
    }
}

class CamdManifestPermissionTest {
    @Test
    fun declaresSystemAlertWindow() {
        val candidates = listOf(
            java.io.File("src/main/AndroidManifest.xml"),
            java.io.File("camd/src/main/AndroidManifest.xml"),
        )
        val manifest = candidates.firstOrNull { it.isFile }
            ?: error("AndroidManifest.xml not found from ${java.io.File(".").absolutePath}")
        val text = manifest.readText()
        assertTrue(
            text.contains("""android:name="android.permission.SYSTEM_ALERT_WINDOW""""),
        )
    }
}

class CamdApiReplyTest {
    @Test
    fun statusDoesNotNeedTheHeadUnit() {
        val engine = CamdEngine(java.io.File("build/camd-test-files").apply { mkdirs() })
        val server = CamdApiServer(engine)
        assertEquals("ERR bad_command", server.reply("NOPE"))
        assertTrue(server.reply("STATUS").startsWith("OK "))
    }
}

class PropCacheTest {
    @Test
    fun cachesWithinTtlReloadsAfterTtlAndOnInvalidate() {
        var now = 0L
        var loads = 0
        val cache = PropCache(ttlMs = 1_000, clock = { now })
        assertEquals("running", cache.get("init.svc.AVMapp") { loads += 1; "running" })
        assertEquals(1, loads)
        now = 500
        assertEquals("running", cache.get("init.svc.AVMapp") { loads += 1; "stopped" })
        assertEquals(1, loads)
        now = 1_000
        assertEquals("stopped", cache.get("init.svc.AVMapp") { loads += 1; "stopped" })
        assertEquals(2, loads)
        cache.invalidate()
        assertEquals("fresh", cache.get("init.svc.AVMapp") { loads += 1; "fresh" })
        assertEquals(3, loads)
    }

    @Test
    fun differentNamesAreCachedSeparately() {
        var now = 0L
        val cache = PropCache(ttlMs = 1_000, clock = { now })
        assertEquals("a", cache.get("p1") { "a" })
        assertEquals("b", cache.get("p2") { "b" })
        now = 100
        assertEquals("a", cache.get("p1") { "x" })
        assertEquals("b", cache.get("p2") { "y" })
    }
}

class CamdApiServerClientTest {
    @Test
    fun answersOneLineAndClosesTheSocket() {
        val engine = CamdEngine(java.io.File("build/camd-test-files").apply { mkdirs() })
        val server = CamdApiServer(engine)
        java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { listener ->
            val client = java.net.Socket()
            client.soTimeout = 2_000
            client.connect(
                java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), listener.localPort),
                2_000,
            )
            client.getOutputStream().write("STATUS\n".toByteArray())
            client.getOutputStream().flush()
            listener.accept().use { accepted ->
                server.serveClient(accepted)
            }
            val reader = client.getInputStream().bufferedReader()
            assertTrue(reader.readLine().startsWith("OK "))
            assertEquals(-1, reader.read())
            client.close()
        }
    }

    @Test
    fun idleClientTimesOutAndClosesWithoutReply() {
        val engine = CamdEngine(java.io.File("build/camd-test-files").apply { mkdirs() })
        val server = CamdApiServer(engine, clientTimeoutMs = 150)
        java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { listener ->
            val client = java.net.Socket()
            client.soTimeout = 5_000
            client.connect(
                java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), listener.localPort),
                2_000,
            )
            listener.accept().use { accepted ->
                server.serveClient(accepted)
            }
            assertEquals(-1, client.getInputStream().read())
            client.close()
        }
    }
}
