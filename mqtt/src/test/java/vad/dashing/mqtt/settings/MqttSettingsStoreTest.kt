package vad.dashing.mqtt.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.mqtt.ha.Topics

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MqttSettingsStoreTest {
    @Test
    fun saveDoesNotRewriteTokenOrPort() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("mqtt-save", Context.MODE_PRIVATE)
        prefs.edit().putString("access_token", "live").putInt("api_port", 9000).commit()
        val store = MqttSettingsStore.forTest(context, prefs)
        val loaded = store.load()
        store.save(loaded.copy(brokerHost = "broker.local", accessToken = "stale", apiPort = 1))
        assertEquals("live", prefs.getString("access_token", null))
        assertEquals(9000, prefs.getInt("api_port", 0))
        assertEquals("broker.local", prefs.getString("broker_host", null))
        store.saveMonitorAccess(accessToken = "renewed", apiPort = 9001)
        assertEquals("renewed", store.load().accessToken)
        assertEquals(9001, store.load().apiPort)
    }

    @Test
    fun plaintextMovesIntoAnEmptyStoreAndIsRemoved() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val plain = context.getSharedPreferences(MqttSettingsStore.PLAIN_FILE, Context.MODE_PRIVATE)
        plain.edit().putString("broker_host", "from-plain").commit()
        val encrypted = context.getSharedPreferences("mqtt-enc", Context.MODE_PRIVATE)
        assertTrue(MqttSettingsStore.migratePlaintextInto(context, encrypted))
        assertEquals("from-plain", encrypted.getString("broker_host", null))
        assertTrue(context.getSharedPreferences(MqttSettingsStore.PLAIN_FILE, Context.MODE_PRIVATE).all.isEmpty())
    }

    @Test
    fun filledEncryptedStoreDropsPlaintextWithoutCopying() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val plain = context.getSharedPreferences(MqttSettingsStore.PLAIN_FILE, Context.MODE_PRIVATE)
        plain.edit().putString("broker_host", "from-plain").commit()
        val encrypted = context.getSharedPreferences("mqtt-enc-full", Context.MODE_PRIVATE)
        encrypted.edit().putString("broker_host", "from-enc").commit()
        assertFalse(MqttSettingsStore.migratePlaintextInto(context, encrypted))
        assertEquals("from-enc", encrypted.getString("broker_host", null))
        assertTrue(context.getSharedPreferences(MqttSettingsStore.PLAIN_FILE, Context.MODE_PRIVATE).all.isEmpty())
    }

    @Test
    fun newInstallGetsAStableDeviceIdAndClientSuffix() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("mqtt-new", Context.MODE_PRIVATE)
        val store = MqttSettingsStore.forTest(context, prefs)
        val first = store.load()
        assertTrue(first.deviceId.startsWith("dashing_"))
        assertTrue(first.clientSuffix.length >= 8)
        val again = store.load()
        assertEquals(first.deviceId, again.deviceId)
        assertEquals(first.clientSuffix, again.clientSuffix)
        val client = Topics.mqttClientId("", again.deviceId, again.clientSuffix)
        assertTrue(client.endsWith(again.clientSuffix))
        val kept = context.getSharedPreferences("mqtt-kept", Context.MODE_PRIVATE)
        kept.edit().putString("device_id", "dashing").commit()
        assertEquals("dashing", MqttSettingsStore.forTest(context, kept).load().deviceId)
    }
}
