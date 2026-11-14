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
        assertNull(CamdCommands.parse("OPEN 4 0.0200 0.5500 0.4000 0.4000 45"))
        assertEquals(CamdCommand.CloseAll, CamdCommands.parse("close"))
        assertEquals(CamdCommand.Close(4), CamdCommands.parse("CLOSE 4"))
        assertEquals(CamdCommand.Status, CamdCommands.parse("STATUS"))
        assertEquals(CamdCommand.SetKeepAvm(true), CamdCommands.parse("AVM ON"))
        assertEquals(CamdCommand.SetKeepAvm(false), CamdCommands.parse("avm off"))
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
        // Flip without rotation slot is not a valid 7-token line (7th must be rot).
        assertNull(CamdCommands.parse("OPEN 8 0.0200 0.5500 0.4000 0.4000 1"))
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
        assertNull(CamdCommands.parse("MOVE 0"))
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
        assertEquals(15, all.size)
        assertTrue(all.all { it.builtin })
        assertEquals(CamdCrop(0f, 0f, 0.5f, 1f), CamdPresetStore.byName(prefs, "front_left")!!.crop)
        assertTrue(
            CamdPresetStore.saveBuiltinOverride(prefs, "front_left", 180, true, false, CamdCrop(0f, 0f, 0.5f, 1f))
        )
        val edited = CamdPresetStore.byName(prefs, "front_left")!!
        assertEquals(180, edited.rotation)
        assertTrue(edited.flipH)
        assertFalse(edited.flipV)
        assertEquals(0, CamdPresetStore.byName(prefs, "front_right")!!.rotation)
        assertEquals(false, CamdPresetStore.saveBuiltinOverride(prefs, "nope", 0, false, false, CamdCrop.IDENTITY))
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
        assertNull(CamdPresetStore.saveUser(prefs, CamdPreset("my_corner", 5)))
        assertEquals(5, CamdPresetStore.byName(prefs, "my_corner")!!.cameraId)
        assertTrue(CamdPresetStore.deleteUser(prefs, "my_corner"))
        assertEquals(null, CamdPresetStore.byName(prefs, "my_corner"))
        assertFalse(CamdPresetStore.deleteUser(prefs, "my_corner"))
    }
}

class CamdBackupTest {
    @Test
    fun exportImportRoundTrip() {
        val prefs = FakePrefs()
        CamdLensCorrectionStore.save(prefs, 8, CamdLensCorrection(k1 = -0.2f, tiltV = 0.3f))
        CamdPresetStore.saveUser(prefs, CamdPreset("my_corner", 4, rotation = 90))
        CamdPresetStore.saveBuiltinOverride(prefs, "front_left", 180, true, false, CamdCrop(0f, 0f, 0.5f, 1f))
        prefs.edit().putBoolean(CameraWindows.KEEP_AVM_STOPPED_KEY, true).apply()

        val json = CamdBackup.exportJson(prefs)

        val fresh = FakePrefs()
        val summary = CamdBackup.importJson(fresh, json)!!
        assertEquals(7, summary.corrections)
        assertEquals(1, summary.userPresets)
        assertEquals(15, summary.builtinPresets)
        assertTrue(summary.keepAvm)
        assertEquals(CamdLensCorrection(k1 = -0.2f, tiltV = 0.3f), CamdLensCorrectionStore.load(fresh, 8))
        assertEquals(90, CamdPresetStore.byName(fresh, "my_corner")!!.rotation)
        assertEquals(180, CamdPresetStore.byName(fresh, "front_left")!!.rotation)
        assertTrue(fresh.getBoolean(CameraWindows.KEEP_AVM_STOPPED_KEY, false))
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
