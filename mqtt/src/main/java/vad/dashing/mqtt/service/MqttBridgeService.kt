package vad.dashing.mqtt.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import vad.dashing.mqtt.BuildConfig
import vad.dashing.mqtt.MainActivity
import vad.dashing.mqtt.R
import vad.dashing.mqtt.api.ApiCallException
import vad.dashing.mqtt.api.MonitorApi
import vad.dashing.mqtt.api.SignalSample
import vad.dashing.mqtt.bridge.CommandGuard
import vad.dashing.mqtt.bridge.GeoPoint
import vad.dashing.mqtt.bridge.InvokeRequest
import vad.dashing.mqtt.bridge.LocationThrottle
import vad.dashing.mqtt.bridge.SignalRef
import vad.dashing.mqtt.bridge.StateFormat
import vad.dashing.mqtt.bridge.batchSignals
import vad.dashing.mqtt.bridge.commandToInvoke
import vad.dashing.mqtt.ha.CatalogEntity
import vad.dashing.mqtt.ha.DiscoveryCleanup
import vad.dashing.mqtt.ha.DiscoveryPayload
import vad.dashing.mqtt.ha.HaComponent
import vad.dashing.mqtt.ha.Topics
import vad.dashing.mqtt.ha.buildEntities
import vad.dashing.mqtt.mqttclient.HiveMqttSession
import vad.dashing.mqtt.settings.MqttSettings
import vad.dashing.mqtt.settings.MqttSettingsStore
import java.time.Instant

class MqttBridgeService : Service() {
    private val thread = HandlerThread("mqtt-bridge")
    private lateinit var handler: Handler
    private lateinit var store: MqttSettingsStore
    private val api = MonitorApi()
    private val session = HiveMqttSession()
    private val guard = CommandGuard()
    private val location = LocationThrottle()
    private val cleanup = DiscoveryCleanup()
    private val lastState = HashMap<String, String>()
    private var allEntities: List<CatalogEntity> = emptyList()
    private var entities: List<CatalogEntity> = emptyList()
    private var catalogJson: String = ""
    private var catalogAtMs: Long = 0L
    private var publishSignature: String = ""
    private var lastSeenAtMs: Long = 0L
    private var lastRepeatAtMs: Long = 0L
    private var monitorUp = false
    private var unauthorized = false
    private var lastError = ""
    private var running = false
    private val loop = Runnable { cycle() }

    private val shutdownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SHUTDOWN) {
                handler.post { publishOffline() }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = MqttSettingsStore(this)
        cleanup.plan(store.loadPublishedTopics())
        startAsForeground(notificationText(monitorUp = false, brokerUp = false))
        thread.start()
        handler = Handler(thread.looper)
        registerReceiver(shutdownReceiver, IntentFilter(Intent.ACTION_SHUTDOWN))
        running = true
        handler.post(loop)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        if (::handler.isInitialized) handler.removeCallbacks(loop)
        runCatching { unregisterReceiver(shutdownReceiver) }
        if (::handler.isInitialized) {
            handler.post {
                publishOffline()
                session.disconnect()
            }
        }
        thread.quitSafely()
        super.onDestroy()
    }

    private fun cycle() {
        if (!running) return
        val settings = store.load().normalized()
        try {
            if (!settings.ready) {
                monitorUp = false
                publishOffline()
                session.disconnect()
                lastError = ""
            } else {
                connectBroker(settings)
                refreshMonitor(settings)
            }
        } catch (error: Exception) {
            lastError = error.message ?: "Ошибка моста"
        }
        publishStatus(settings)
        if (running) {
            handler.removeCallbacks(loop)
            handler.postDelayed(loop, settings.pollSeconds.coerceIn(1, 60) * 1000L)
        }
    }

    private fun connectBroker(settings: MqttSettings) {
        val base = Topics.base(settings.topicPrefix, settings.deviceId)
        session.ensureConnected(
            settings = settings,
            statusTopic = Topics.status(base),
            onConnected = { handler.post { onBrokerConnected(store.load().normalized()) } },
            onDisconnected = { handler.post { publishStatus(store.load().normalized()) } },
            onMessage = { topic, payload, retained ->
                handler.post { onMqttMessage(store.load().normalized(), topic, payload, retained) }
            },
        )
    }

    private fun onBrokerConnected(settings: MqttSettings) {
        republishStatic(settings)
        syncSubscriptions(settings)
        publishStatus(settings)
    }

    private fun refreshMonitor(settings: MqttSettings) {
        val wasUp = monitorUp
        try {
            api.health(settings.apiPort)
            unauthorized = false
            monitorUp = true
        } catch (error: ApiCallException) {
            monitorUp = false
            unauthorized = error.httpStatus == 401 || error.code == "unauthorized"
            lastError = if (unauthorized) {
                "Monitor не принимает токен"
            } else {
                "Нет связи с Monitor"
            }
            if (wasUp) publishOffline()
            return
        }
        if (!wasUp) {
            publishAvailability(settings, "online")
        }
        reloadCatalogIfNeeded(settings)
        pollSignals(settings)
        maybeRepeat(settings)
        syncSubscriptions(settings)
        lastError = ""
    }

    private fun reloadCatalogIfNeeded(settings: MqttSettings) {
        val now = System.currentTimeMillis()
        if (catalogJson.isEmpty() || now - catalogAtMs >= CATALOG_PERIOD_MS) {
            catalogJson = api.catalog(settings.apiPort, settings.accessToken)
            val automations = api.automations(settings.apiPort, settings.accessToken)
                .map { vad.dashing.mqtt.ha.AutomationRow(it.id, it.name) }
            catalogAtMs = now
            allEntities = buildEntities(catalogJson, automations)
        }
        val selected = settings.selectedObjectIds
        entities = allEntities.filter { it.objectId in selected }
        lastState.keys.retainAll(entities.map { it.objectId }.toSet())
        val signature = entities.joinToString(",") { it.objectId } +
            "|${settings.discoveryEnabled}|${settings.deviceName}|${settings.deviceId}|" +
            "${settings.topicPrefix}|${settings.discoveryPrefix}|${settings.acceptCommands}"
        if (signature != publishSignature) {
            publishSignature = signature
            republishStatic(settings)
        }
    }

    private fun pollSignals(settings: MqttSettings) {
        val refs = entities.mapNotNull { entity ->
            val id = entity.signalId ?: return@mapNotNull null
            val source = entity.source ?: return@mapNotNull null
            SignalRef(id, source)
        }
        val samples = mutableListOf<SignalSample>()
        batchSignals(refs).forEach { batch ->
            samples += api.signals(settings.apiPort, settings.accessToken, batch.ids, batch.source)
        }
        val byId = samples.associateBy { it.id }
        val base = Topics.base(settings.topicPrefix, settings.deviceId)
        var sawFresh = false
        entities.forEach { entity ->
            val sample = byId[entity.signalId] ?: return@forEach
            if (!sample.available) return@forEach
            if (entity.component == HaComponent.DEVICE_TRACKER) {
                val point = geoOf(sample) ?: return@forEach
                val now = System.currentTimeMillis()
                if (location.shouldSend(point, now, force = false)) {
                    publishGeo(base, entity, point)
                    location.markSent(point, now)
                    sawFresh = true
                }
                return@forEach
            }
            val text = when {
                sample.number != null -> StateFormat.numberText(sample.number, entity.unit)
                sample.text != null -> StateFormat.text(sample.text, entity.unit)
                else -> null
            } ?: return@forEach
            if (lastState[entity.objectId] == text) return@forEach
            publishState(base, entity.objectId, text)
            sawFresh = true
        }
        if (sawFresh) publishLastSeen(settings, force = false)
    }

    private fun maybeRepeat(settings: MqttSettings) {
        if (settings.repeatMinutes <= 0) return
        val now = System.currentTimeMillis()
        if (now - lastRepeatAtMs < settings.repeatMinutes * 60_000L) return
        lastRepeatAtMs = now
        val base = Topics.base(settings.topicPrefix, settings.deviceId)
        lastState.forEach { (objectId, payload) ->
            session.publish(Topics.state(base, objectId), payload, retain = true)
        }
        location.lastSent()?.let { point ->
            val tracker = entities.firstOrNull { it.component == HaComponent.DEVICE_TRACKER }
            if (tracker != null) publishGeo(base, tracker, point)
        }
        publishLastSeen(settings, force = true)
    }

    private fun republishStatic(settings: MqttSettings) {
        if (!session.connected) return
        val base = Topics.base(settings.topicPrefix, settings.deviceId)
        publishAvailability(settings, if (monitorUp) "online" else "offline")
        session.publish(
            Topics.commands(base),
            if (settings.acceptCommands) "on" else "off",
            retain = true,
        )
        publishDiscovery(settings)
        lastState.forEach { (objectId, payload) ->
            session.publish(Topics.state(base, objectId), payload, retain = true)
        }
    }

    private fun publishDiscovery(settings: MqttSettings) {
        val sw = BuildConfig.VERSION_NAME
        val next = linkedSetOf<String>()
        if (settings.discoveryEnabled) {
            entities.forEach { entity ->
                val topic = DiscoveryPayload.configTopic(settings.discoveryPrefix, settings.deviceId, entity)
                val body = DiscoveryPayload.entityConfig(
                    entity,
                    settings.topicPrefix,
                    settings.discoveryPrefix,
                    settings.deviceId,
                    settings.deviceName,
                    sw,
                )
                session.publish(topic, body, retain = true)
                next += topic
            }
            DiscoveryPayload.serviceTopics(settings.discoveryPrefix, settings.deviceId).forEach { topic ->
                next += topic
            }
            session.publish(
                DiscoveryPayload.serviceTopics(settings.discoveryPrefix, settings.deviceId)[0],
                DiscoveryPayload.connectivityConfig(
                    settings.topicPrefix,
                    settings.deviceId,
                    settings.deviceName,
                    sw,
                ),
                retain = true,
            )
            session.publish(
                DiscoveryPayload.serviceTopics(settings.discoveryPrefix, settings.deviceId)[1],
                DiscoveryPayload.lastSeenConfig(
                    settings.topicPrefix,
                    settings.deviceId,
                    settings.deviceName,
                    sw,
                ),
                retain = true,
            )
        }
        val stale = cleanup.plan(next)
        stale.forEach { topic -> session.publish(topic, "", retain = true) }
        store.savePublishedTopics(cleanup.publishedTopics())
    }

    private fun syncSubscriptions(settings: MqttSettings) {
        if (!session.connected) return
        val base = Topics.base(settings.topicPrefix, settings.deviceId)
        val topics = mutableSetOf<String>()
        if (settings.discoveryEnabled) {
            topics += Topics.haStatus(settings.discoveryPrefix)
        }
        if (settings.acceptCommands && monitorUp) {
            entities.filter { it.writable }.forEach { entity ->
                topics += Topics.set(base, entity.objectId)
            }
        }
        session.replaceSubscriptions(topics)
    }

    private fun onMqttMessage(
        settings: MqttSettings,
        topic: String,
        payload: String,
        retained: Boolean,
    ) {
        if (topic == Topics.haStatus(settings.discoveryPrefix) &&
            payload.equals("online", ignoreCase = true)
        ) {
            republishStatic(settings)
            return
        }
        if (!settings.acceptCommands || !monitorUp) return
        val base = Topics.base(settings.topicPrefix, settings.deviceId)
        val entity = entities.firstOrNull { Topics.set(base, it.objectId) == topic } ?: return
        val decision = guard.decide(
            objectId = entity.objectId,
            payload = payload.trim(),
            retained = retained,
            lastPublished = lastState[entity.objectId],
            isButton = entity.isButton,
            nowMs = System.currentTimeMillis(),
        )
        if (decision != null) return
        try {
            val command = commandToInvoke(entity, payload)
            if (command == null) {
                lastError = "Команда не из списка"
                return
            }
            val ok = when (val request = command.request) {
                is InvokeRequest.Actions ->
                    api.invoke(settings.apiPort, settings.accessToken, request.json)
                is InvokeRequest.RunAutomation ->
                    api.runAutomation(settings.apiPort, settings.accessToken, request.id)
            }
            if (!ok) {
                lastError = "Машина не выполнила команду"
                restoreState(base, entity)
            } else {
                lastError = ""
                val published = command.publishedState
                if (published != null) publishState(base, entity.objectId, published)
            }
        } catch (error: Exception) {
            lastError = error.message ?: "Команда не выполнена"
            restoreState(base, entity)
        } finally {
            guard.finish(entity.objectId)
        }
    }

    private fun restoreState(base: String, entity: CatalogEntity) {
        val previous = lastState[entity.objectId] ?: return
        session.publish(Topics.state(base, entity.objectId), previous, retain = true)
    }

    private fun publishState(base: String, objectId: String, payload: String) {
        lastState[objectId] = payload
        session.publish(Topics.state(base, objectId), payload, retain = true)
    }

    private fun publishGeo(base: String, entity: CatalogEntity, point: GeoPoint) {
        val json = """{"latitude":${point.latitude},"longitude":${point.longitude}}"""
        session.publish(Topics.attributes(base, entity.objectId), json, retain = true)
    }

    private fun publishLastSeen(settings: MqttSettings, force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSeenAtMs < 60_000L) return
        lastSeenAtMs = now
        val base = Topics.base(settings.topicPrefix, settings.deviceId)
        session.publish(Topics.lastSeen(base), Instant.ofEpochMilli(now).toString(), retain = true)
    }

    private fun publishAvailability(settings: MqttSettings, value: String) {
        if (!session.connected) return
        val base = Topics.base(settings.topicPrefix, settings.deviceId)
        session.publish(Topics.status(base), value, retain = true)
    }

    private fun publishOffline() {
        val settings = store.load().normalized()
        if (!session.connected) return
        publishLastSeen(settings, force = true)
        publishAvailability(settings, "offline")
        monitorUp = false
    }

    private fun publishStatus(settings: MqttSettings) {
        val brokerUp = session.connected
        val availability = if (monitorUp && brokerUp) "online" else "offline"
        BridgeStatusStore.state.value = BridgeStatus(
            monitorUp = monitorUp && !unauthorized,
            brokerUp = brokerUp,
            availability = availability,
            publishedCount = entities.size,
            lastError = lastError,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(notificationText(monitorUp, brokerUp)))
    }

    private fun geoOf(sample: SignalSample): GeoPoint? {
        val lat = sample.latitude ?: return null
        val lon = sample.longitude ?: return null
        if (!lat.isFinite() || !lon.isFinite()) return null
        return GeoPoint(lat, lon)
    }

    private fun startAsForeground(text: String) {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
            channel.setSound(null, null)
            manager.createNotificationChannel(channel)
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun notificationText(monitorUp: Boolean, brokerUp: Boolean): String = when {
        monitorUp && brokerUp -> "Monitor и брокер на связи"
        !monitorUp -> "Нет связи с Monitor"
        else -> "Нет связи с брокером"
    }

    companion object {
        private const val CHANNEL_ID = "mqtt_bridge"
        private const val NOTIFICATION_ID = 42
        private const val CATALOG_PERIOD_MS = 10L * 60L * 1000L

        fun start(context: Context) {
            val intent = Intent(context, MqttBridgeService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
    }
}
