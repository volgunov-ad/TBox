package vad.dashing.tbox.location.roadmatch

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoadMapOfflineImportTest {

    @Test
    fun sanitizeRejectsTraversalAndSchemes() {
        assertNull(RoadMapOfflineCatalogParser.sanitizeRelativePackPath("../x.zip"))
        assertNull(RoadMapOfflineCatalogParser.sanitizeRelativePackPath("/abs/x.zip"))
        assertNull(RoadMapOfflineCatalogParser.sanitizeRelativePackPath("https://evil/x.zip"))
        assertNull(RoadMapOfflineCatalogParser.sanitizeRelativePackPath("C:/x.zip"))
        assertNull(RoadMapOfflineCatalogParser.sanitizeRelativePackPath("a/../b.zip"))
        assertEquals(
            "ru-moscow-v4.tboxroads.zip",
            RoadMapOfflineCatalogParser.sanitizeRelativePackPath("./ru-moscow-v4.tboxroads.zip"),
        )
        assertEquals(
            "packs/ru-moscow-v4.tboxroads.zip",
            RoadMapOfflineCatalogParser.sanitizeRelativePackPath("packs/ru-moscow-v4.tboxroads.zip"),
        )
    }

    @Test
    fun parseOfflineCatalogRequiresSafeFileAndDedupes() {
        val json = """
            {
              "version": 2,
              "title": "USB maps",
              "regions": [
                {
                  "id": "ru-test",
                  "country": "RU",
                  "title_ru": "Тест",
                  "title_en": "Test",
                  "bbox": [37.0, 55.0, 38.0, 56.0],
                  "file": "ru-test-v4.tboxroads.zip",
                  "bytes": 10,
                  "sha256": "${"a".repeat(64)}",
                  "graphVersion": 3
                },
                {
                  "id": "ru-test",
                  "country": "RU",
                  "title_ru": "Тест",
                  "title_en": "Test",
                  "bbox": [37.0, 55.0, 38.0, 56.0],
                  "file": "ru-test-v4.tboxroads.zip",
                  "bytes": 10,
                  "sha256": "${"a".repeat(64)}",
                  "graphVersion": 4
                }
              ]
            }
        """.trimIndent()
        val cat = RoadMapOfflineCatalogParser.parse(json)
        assertEquals(2, cat.version)
        assertEquals("USB maps", cat.title)
        assertEquals(1, cat.regions.size)
        assertEquals(4, cat.regions[0].region.graphVersion)
        assertEquals("ru-test-v4.tboxroads.zip", cat.regions[0].relativeFile)
    }

    @Test
    fun parseRejectsConflictingSameVersionHash() {
        val json = """
            {
              "version": 1,
              "regions": [
                {
                  "id": "ru-test",
                  "file": "a.zip",
                  "bytes": 1,
                  "sha256": "${"a".repeat(64)}",
                  "graphVersion": 4,
                  "bbox": [0,0,1,1],
                  "title_ru": "a",
                  "title_en": "a"
                },
                {
                  "id": "ru-test",
                  "file": "b.zip",
                  "bytes": 1,
                  "sha256": "${"b".repeat(64)}",
                  "graphVersion": 4,
                  "bbox": [0,0,1,1],
                  "title_ru": "a",
                  "title_en": "a"
                }
              ]
            }
        """.trimIndent()
        val result = runCatching { RoadMapOfflineCatalogParser.parse(json) }
        assertTrue(result.isFailure)
    }

    @Test
    fun basenameFromUrlWhenFileMissing() {
        val o = JSONObject(
            """{"id":"x","url":"yandex-disk:/maps/ru-x-v4.tboxroads.zip","bytes":1}""",
        )
        assertEquals(
            "ru-x-v4.tboxroads.zip",
            RoadMapOfflineCatalogParser.resolveRelativeFile(o),
        )
    }

    @Test
    fun parseSkipsUnpublishedRowsWithoutFileOrUrl() {
        val json = """
            {
              "version": 4,
              "regions": [
                {
                  "id": "ru-unpublished",
                  "country": "RU",
                  "title_ru": "Нет пакета",
                  "title_en": "No pack",
                  "bbox": [0,0,1,1],
                  "url": "",
                  "bytes": 0,
                  "graphVersion": 4
                },
                {
                  "id": "ru-moscow",
                  "country": "RU",
                  "title_ru": "Москва",
                  "title_en": "Moscow",
                  "bbox": [36.8, 55.1, 38.3, 56.1],
                  "url": "yandex-disk:/maps/ru-moscow-v4.tboxroads.zip",
                  "bytes": 12345678,
                  "graphVersion": 4
                }
              ]
            }
        """.trimIndent()
        val cat = RoadMapOfflineCatalogParser.parse(json)
        assertEquals(1, cat.regions.size)
        assertEquals("ru-moscow", cat.regions[0].region.id)
        assertEquals("ru-moscow-v4.tboxroads.zip", cat.regions[0].relativeFile)
        assertEquals(12345678L, cat.regions[0].region.bytes)
        assertEquals("", cat.regions[0].sha256)
    }

    @Test
    fun installBundleFromLocalZipIsAtomicAndReported() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var savedManifest = """{"version":1,"installed":[]}"""
        val manager = RoadMapDownloadManager(
            appContext = context,
            scope = CoroutineScope(Dispatchers.Unconfined),
            loadManifestJson = { savedManifest },
            saveManifestJson = { savedManifest = it },
        )
        manager.ensureLoaded()

        val root = createTempDir(prefix = "usb-import-")
        val zip = File(root, "ru-test.tboxroads.zip")
        writeMinimalBundle(zip, regionId = "ru-test", graphVersion = 4)

        val entry = manager.installBundleFromLocalZip(zip, "ru-test")
        assertEquals("ru-test", entry.id)
        assertEquals(4, entry.graphVersion)
        assertTrue(manager.bundleDirFor("ru-test").isDirectory)
        assertTrue(File(manager.bundleDirFor("ru-test"), RoadMapBundle.INDEX_FILE).isFile)
        assertNotNull(manager.installedEntry("ru-test"))
        assertTrue(savedManifest.contains("ru-test"))
    }

    @Test
    fun importSelectedVerifiesHashAndInstallsFromFileUri() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var savedManifest = """{"version":1,"installed":[]}"""
        val manager = RoadMapDownloadManager(
            appContext = context,
            scope = CoroutineScope(Dispatchers.Unconfined),
            loadManifestJson = { savedManifest },
            saveManifestJson = { savedManifest = it },
        )
        manager.ensureLoaded()

        val folder = createTempDir(prefix = "usb-folder-")
        val zip = File(folder, "ru-test-v4.tboxroads.zip")
        writeMinimalBundle(zip, regionId = "ru-test", graphVersion = 4)
        val sha = sha256Hex(zip.readBytes())
        val catalogFile = File(folder, "catalog.json")
        catalogFile.writeText(
            """
            {
              "version": 1,
              "title": "Test USB",
              "regions": [{
                "id": "ru-test",
                "country": "RU",
                "title_ru": "Тест",
                "title_en": "Test",
                "bbox": [37.0, 55.0, 38.0, 56.0],
                "file": "ru-test-v4.tboxroads.zip",
                "bytes": ${zip.length()},
                "sha256": "$sha",
                "graphVersion": 4
              }]
            }
            """.trimIndent(),
        )

        val importer = RoadMapOfflineImportManager(context, manager)
        val catalogUri = Uri.fromFile(catalogFile)
        val catalog = importer.readCatalog(catalogUri).getOrThrow()
        val states = importer.buildRegionStates(catalog, catalogUri, null)
        assertEquals(1, states.size)
        assertEquals(OfflineRegionReadiness.NOT_INSTALLED, states[0].readiness)
        assertTrue(states[0].selectable)

        val summary = importer.importSelected(
            catalogUri = catalogUri,
            folderUri = null,
            catalog = catalog,
            regionIds = setOf("ru-test"),
            onProgress = {},
        )
        assertEquals(listOf("ru-test"), summary.succeeded)
        assertTrue(summary.failed.isEmpty())
        assertTrue(manager.bundleDirFor("ru-test").isDirectory)
        assertEquals(4, manager.installedEntry("ru-test")!!.graphVersion)
    }

    @Test
    fun importFromPublishedCatalogWithoutSha256UsesSizeCheck() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var savedManifest = """{"version":1,"installed":[]}"""
        val manager = RoadMapDownloadManager(
            appContext = context,
            scope = CoroutineScope(Dispatchers.Unconfined),
            loadManifestJson = { savedManifest },
            saveManifestJson = { savedManifest = it },
        )
        manager.ensureLoaded()

        val folder = createTempDir(prefix = "usb-yadisk-")
        val zip = File(folder, "ru-test-v4.tboxroads.zip")
        writeMinimalBundle(zip, regionId = "ru-test", graphVersion = 4)
        val catalogFile = File(folder, "catalog.json")
        catalogFile.writeText(
            """
            {
              "version": 4,
              "title": "maps",
              "regions": [{
                "id": "ru-test",
                "country": "RU",
                "title_ru": "Тест",
                "title_en": "Test",
                "bbox": [37.0, 55.0, 38.0, 56.0],
                "url": "yandex-disk:/maps/ru-test-v4.tboxroads.zip",
                "bytes": ${zip.length()},
                "graphVersion": 4
              }]
            }
            """.trimIndent(),
        )

        val importer = RoadMapOfflineImportManager(context, manager)
        val catalogUri = Uri.fromFile(catalogFile)
        val catalog = importer.readCatalog(catalogUri).getOrThrow()
        val states = importer.buildRegionStates(catalog, catalogUri, null)
        assertEquals(1, states.size)
        assertEquals(OfflineRegionReadiness.NOT_INSTALLED, states[0].readiness)
        assertTrue(states[0].selectable)
        assertEquals("", catalog.regions[0].sha256)

        val summary = importer.importSelected(
            catalogUri = catalogUri,
            folderUri = null,
            catalog = catalog,
            regionIds = setOf("ru-test"),
            onProgress = {},
        )
        assertEquals(listOf("ru-test"), summary.succeeded)
        assertTrue(summary.failed.isEmpty())
        assertTrue(manager.bundleDirFor("ru-test").isDirectory)
        assertEquals(4, manager.installedEntry("ru-test")!!.graphVersion)
    }

    @Test
    fun importRejectsSizeMismatchWithoutSha256() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var savedManifest = """{"version":1,"installed":[]}"""
        val manager = RoadMapDownloadManager(
            appContext = context,
            scope = CoroutineScope(Dispatchers.Unconfined),
            loadManifestJson = { savedManifest },
            saveManifestJson = { savedManifest = it },
        )
        manager.ensureLoaded()

        val folder = createTempDir(prefix = "usb-badsize-")
        val zip = File(folder, "ru-test-v4.tboxroads.zip")
        writeMinimalBundle(zip, regionId = "ru-test", graphVersion = 4)
        val catalogFile = File(folder, "catalog.json")
        catalogFile.writeText(
            """
            {
              "version": 1,
              "regions": [{
                "id": "ru-test",
                "country": "RU",
                "title_ru": "Тест",
                "title_en": "Test",
                "bbox": [37.0, 55.0, 38.0, 56.0],
                "file": "ru-test-v4.tboxroads.zip",
                "bytes": ${zip.length() + 99},
                "graphVersion": 4
              }]
            }
            """.trimIndent(),
        )
        val importer = RoadMapOfflineImportManager(context, manager)
        val catalogUri = Uri.fromFile(catalogFile)
        val catalog = importer.readCatalog(catalogUri).getOrThrow()
        val summary = importer.importSelected(
            catalogUri = catalogUri,
            folderUri = null,
            catalog = catalog,
            regionIds = setOf("ru-test"),
            onProgress = {},
        )
        assertTrue(summary.succeeded.isEmpty())
        assertEquals(1, summary.failed.size)
        assertTrue(summary.failed[0].second.contains("size", ignoreCase = true))
        assertFalse(manager.bundleDirFor("ru-test").exists())
    }

    @Test
    fun importRejectsHashMismatchWithoutTouchingInstall() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var savedManifest = """{"version":1,"installed":[]}"""
        val manager = RoadMapDownloadManager(
            appContext = context,
            scope = CoroutineScope(Dispatchers.Unconfined),
            loadManifestJson = { savedManifest },
            saveManifestJson = { savedManifest = it },
        )
        manager.ensureLoaded()

        val folder = createTempDir(prefix = "usb-badhash-")
        val zip = File(folder, "ru-test-v4.tboxroads.zip")
        writeMinimalBundle(zip, regionId = "ru-test", graphVersion = 4)
        val catalogFile = File(folder, "catalog.json")
        catalogFile.writeText(
            """
            {
              "version": 1,
              "regions": [{
                "id": "ru-test",
                "country": "RU",
                "title_ru": "Тест",
                "title_en": "Test",
                "bbox": [37.0, 55.0, 38.0, 56.0],
                "file": "ru-test-v4.tboxroads.zip",
                "bytes": ${zip.length()},
                "sha256": "${"0".repeat(64)}",
                "graphVersion": 4
              }]
            }
            """.trimIndent(),
        )
        val importer = RoadMapOfflineImportManager(context, manager)
        val catalogUri = Uri.fromFile(catalogFile)
        val catalog = importer.readCatalog(catalogUri).getOrThrow()
        val summary = importer.importSelected(
            catalogUri = catalogUri,
            folderUri = null,
            catalog = catalog,
            regionIds = setOf("ru-test"),
            onProgress = {},
        )
        assertTrue(summary.succeeded.isEmpty())
        assertEquals(1, summary.failed.size)
        assertTrue(summary.failed[0].second.contains("hash", ignoreCase = true))
        assertFalse(manager.bundleDirFor("ru-test").exists())
    }

    @Test
    fun fileFromDocumentIdParsesPrimaryRawAndUsb() {
        val root = File("/sdcard")
        assertEquals(
            File(root, "Download/tbox-maps/catalog.json"),
            LocalSafPath.fileFromDocumentId("primary:Download/tbox-maps/catalog.json", root),
        )
        assertEquals(
            File(root, "Download/catalog.json"),
            LocalSafPath.fileFromDocumentId("home:Download/catalog.json", root),
        )
        assertEquals(
            File("/storage/emulated/0/Download/tbox-maps/catalog.json"),
            LocalSafPath.fileFromDocumentId(
                "raw:/storage/emulated/0/Download/tbox-maps/catalog.json",
                root,
            ),
        )
        assertEquals(
            File("/storage/1A2B-3C4D/maps/ru-adygea-v4.tboxroads.zip"),
            LocalSafPath.fileFromDocumentId(
                "1A2B-3C4D:maps/ru-adygea-v4.tboxroads.zip",
                root,
            ),
        )
        assertNull(LocalSafPath.fileFromDocumentId("primary:Download/../catalog.json", root))
        assertNull(LocalSafPath.fileFromDocumentId("", root))
        assertNull(LocalSafPath.fileFromDocumentId("raw:relative-not-absolute", root))
    }

    @Test
    fun resolvePackUriFindsSiblingsForExternalStorageDocumentUri() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var savedManifest = """{"version":1,"installed":[]}"""
        val manager = RoadMapDownloadManager(
            appContext = context,
            scope = CoroutineScope(Dispatchers.Unconfined),
            loadManifestJson = { savedManifest },
            saveManifestJson = { savedManifest = it },
        )
        manager.ensureLoaded()

        val primary = android.os.Environment.getExternalStorageDirectory()
        val folder = File(primary, "Download/tbox-maps").apply { mkdirs() }
        val zip = File(folder, "ru-test-v4.tboxroads.zip")
        writeMinimalBundle(zip, regionId = "ru-test", graphVersion = 4)
        val catalogFile = File(folder, "catalog.json")
        catalogFile.writeText(
            """
            {
              "version": 1,
              "title": "USB maps",
              "regions": [{
                "id": "ru-test",
                "country": "RU",
                "title_ru": "Тест",
                "title_en": "Test",
                "bbox": [37.0, 55.0, 38.0, 56.0],
                "file": "ru-test-v4.tboxroads.zip",
                "bytes": ${zip.length()},
                "graphVersion": 4
              }]
            }
            """.trimIndent(),
        )

        val catalogUri = Uri.parse(
            "content://com.android.externalstorage.documents/document/" +
                Uri.encode("primary:Download/tbox-maps/catalog.json"),
        )
        val importer = RoadMapOfflineImportManager(context, manager)
        val catalog = RoadMapOfflineCatalogParser.parse(catalogFile.readText())
        val resolved = importer.resolvePackUri(catalogUri, null, "ru-test-v4.tboxroads.zip")
        assertNotNull(resolved)
        val states = importer.buildRegionStates(catalog, catalogUri, null)
        assertEquals(OfflineRegionReadiness.NOT_INSTALLED, states[0].readiness)
        assertTrue(states[0].selectable)
    }

    @Test
    fun buildRegionStatesPutsFoundPacksBeforeMissing() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var savedManifest = """{"version":1,"installed":[]}"""
        val manager = RoadMapDownloadManager(
            appContext = context,
            scope = CoroutineScope(Dispatchers.Unconfined),
            loadManifestJson = { savedManifest },
            saveManifestJson = { savedManifest = it },
        )
        manager.ensureLoaded()
        val folder = createTempDir(prefix = "usb-partial-")
        val zip = File(folder, "ru-found-v4.tboxroads.zip")
        writeMinimalBundle(zip, regionId = "ru-found", graphVersion = 4)
        val catalogFile = File(folder, "catalog.json")
        catalogFile.writeText(
            """
            {
              "version": 1,
              "regions": [
                {
                  "id": "ru-missing",
                  "country": "RU",
                  "title_ru": "Нет",
                  "title_en": "Missing",
                  "bbox": [0,0,1,1],
                  "file": "ru-missing-v4.tboxroads.zip",
                  "bytes": 10,
                  "graphVersion": 4
                },
                {
                  "id": "ru-found",
                  "country": "RU",
                  "title_ru": "Есть",
                  "title_en": "Found",
                  "bbox": [0,0,1,1],
                  "file": "ru-found-v4.tboxroads.zip",
                  "bytes": ${zip.length()},
                  "graphVersion": 4
                }
              ]
            }
            """.trimIndent(),
        )
        val importer = RoadMapOfflineImportManager(context, manager)
        val catalogUri = Uri.fromFile(catalogFile)
        val catalog = importer.readCatalog(catalogUri).getOrThrow()
        val states = importer.buildRegionStates(catalog, catalogUri, null)
        assertEquals("ru-found", states[0].offline.region.id)
        assertEquals(OfflineRegionReadiness.NOT_INSTALLED, states[0].readiness)
        assertEquals("ru-missing", states[1].offline.region.id)
        assertEquals(OfflineRegionReadiness.MISSING_FILE, states[1].readiness)
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val d = MessageDigest.getInstance("SHA-256").digest(bytes)
        return d.joinToString("") { "%02x".format(it) }
    }

    private fun writeMinimalBundle(zip: File, regionId: String, graphVersion: Int) {
        val index = """
            {
              "format": 1,
              "regionId": "$regionId",
              "graphVersion": $graphVersion,
              "bbox": [37.0, 55.0, 38.0, 56.0],
              "tiles": [
                {"id":"a","file":"tiles/a.tboxroads","bbox":[37.0,55.0,38.0,56.0],"bytes":100}
              ]
            }
        """.trimIndent()
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry(RoadMapBundle.INDEX_FILE))
            out.write(index.toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("tiles/a.tboxroads"))
            out.write(pack(regionId, graphVersion))
            out.closeEntry()
        }
    }

    private fun pack(regionId: String, version: Int): ByteArray {
        val json =
            """{"format":1,"regionId":"$regionId","graphVersion":$version,"bbox":[37.0,55.0,38.0,56.0],"edges":[{"id":1,"class":"primary","lengthM":100.0,"from":0,"to":1,"coords":[[37.5,55.4],[37.5,55.6]]}]}"""
        val gz = ByteArrayOutputStream()
        GZIPOutputStream(gz).use { it.write(json.toByteArray()) }
        return RoadGraph.MAGIC.toByteArray(Charsets.US_ASCII) + gz.toByteArray()
    }
}
