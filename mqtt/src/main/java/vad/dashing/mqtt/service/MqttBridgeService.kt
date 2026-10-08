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
import vad.dashing.mqtt.bridge.FastPublishGate
import vad.dashing.mqtt.bridge.GeoPoint
import vad.dashing.mqtt.bridge.MediaCommand
import vad.dashing.mqtt.bridge.MediaSample
import vad.dashing.mqtt.bridge.mediaInvoke
import vad.dashing.mqtt.bridge.mediaViewOf
import vad.dashing.mqtt.bridge.mergeMedia
import vad.dashing.mqtt.bridge.parseMediaCommand
import vad.dashing.mqtt.bridge.InvokeRequest
import vad.dashing.mqtt.bridge.LocationThrottle
import vad.dashing.mqtt.bridge.SignalRef
import vad.dashing.mqtt.bridge.StateFormat
import vad.dashing.mqtt.bridge.batchSignals
import vad.dashing.mqtt.bridge.commandToInvoke
import vad.dashing.mqtt.ha.AutomationRow
import vad.dashing.mqtt.ha.CatalogEntity
import vad.dashing.mqtt.ha.DiscoveryCleanup
import vad.dashing.mqtt.ha.DiscoveryPayload
import vad.dashing.mqtt.ha.HaComponent
import vad.dashing.mqtt.ha.Topics
import vad.dashing.mqtt.ha.buildEntities
import vad.dashing.mqtt.mqttclient.HiveMqttSession
import vad.dashing.mqtt.settings.MqttSettings
import vad.dashing.mqtt.settings.MqttSettingsStore
import vad.dashing.mqtt.wireguard.WgRouteException
import java.time.Instant

class MqttBridgeService : Service() {
    private val thread = HandlerThread("mqtt-bridge")
    private lateinit var handler: Handler
    private lateinit var store: MqttSettingsStore
    private val api = MonitorApi()
    private val session = HiveMqttSession()
    private val guard = CommandGuard()
    private val location = LocationThrottle()
    private val fastPublish = FastPublishGate()
    private val cleanup = DiscoveryCleanup()
    private val lastState = HashMap<String, String>()
    private var allEntities: List<CatalogEntity> = emptyList()
    private var entities: List<CatalogEntity> = emptyList()
    private var catalogJson: String = ""
    private var catalogAtMs: Long = 0L
    private var catalogVersion: Int = -1
    private var publishSignature: String = ""
    private var lastSeenAtMs: Long = 0L
    private var lastRepeatAtMs: Long = 0L
    private var monitorUp = false
    private var unauthorized = false
    private var lastError = ""
    private var running = false
    @Volatile
    private var lastStartId = 0
    @Volatile
    private var expedite = false
    @Volatile
    private var notifiedText: String? = null
    @Volatile
    private var channelReady = false
    private val loop = Runnable { cycle() }

    private val shutdownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SHUTDOWN) {
                post { publishOffline() }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = MqttSettingsStore.get(this)
        cleanup.commit(store.loadPublishedTopics())
        startAsForeground(notificationText(monitorUp = false, brokerUp = false))
        thread.start()
        handler = Handler(thread.looper)
        val shutdown = IntentFilter(Intent.ACTION_SHUTDOWN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(shutdownReceiver, shutdown, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(shutdownReceiver, shutdown)
        }
        running = true
        handler.post(loop)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        expedite = true
        // Every startForegroundService() call must be answered, not only the first one.
        startAsForeground(notifiedText ?: notificationText(monitorUp = false, brokerUp = false))
        if (::handler.isInitialized) {
            handler.removeCallbacks(loop)
            handler.postDelayed(loop, START_SETTLE_MS)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        runCatching { unregisterReceiver(shutdownReceiver) }
        if (::handler.isInitialized) {
            handler.removeCallbacksAndMessages(null)
            post {
                publishOffline()
                session.disconnect()
            }
        }
        thread.quitSafely()
        BridgeStatusStore.state.value = BridgeStatus()
        super.onDestroy()
    }

    /** Anything thrown on the bridge looper would kill the whole process. */
    private fun post(block: () -> Unit) {
        handler.post {
            try {
                block()
            } catch (error: Exception) {
                lastError = error.message ?: error.javaClass.simpleName
            }
        }
    }

    private fun cycle() {
        if (!running) return
        val settings = try {
            store.load().normalized()
        } catch (error: Exception) {
            lastError = error.message ?: "Настройки не читаются"
            reschedule(MqttSettings().pollSeconds)
            return
        }
        if (!settings.ready) {
            monitorUp = false
            lastError = ""
            runCatching { publishOffline() }
            session.disconnect()
            // By start id: a start that raced in with fresh settings keeps the service alive.
            stopSelf(lastStartId)
        } else {
            var tunnelError: String? = null
            try {
                connectBroker(settings)
            } catch (error: WgRouteException) {
                tunnelError = error.message ?: "Туннель WireGuard не поднялся"
                lastError = tunnelError
            } catch (error: Exception) {
                lastError = error.message ?: "Нет связи с брокером"
            }
            try {
                refreshMonitor(settings)
            } catch (error: Exception) {
                lastError = error.message ?: "Ошибка моста"
            }
            if (tunnelError != null) lastError = tunnelError
        }
        runCatching { publishStatus(settings) }
        reschedule(settings.pollSeconds)
    }

    private fun reschedule(pollSeconds: Int) {
        if (!running) return
        val delay = if (expedite) START_SETTLE_MS else pollSeconds.coerceIn(1, 60) * 1000L
        expedite = false
        handler.removeCallbacks(loop)
        handler.postDelayed(loop, delay)
    }

    private fun connectBroker(settings: MqttSettings) {
        val base = Topics.base(settings.topicPrefix, settings.deviceId)
        session.ensureConnected(
            settings = settings,
            statusTopic = Topics.status(base),
            onConnected = { post { onBrokerConnected(store.load().normalized()) } },
            onDisconnected = { post { publishStatus(store.load().normalized()) } },
            onMessage = { topic, payload, retained ->
                post { onMqttMessage(store.load().normalized(), topic, payload, retained) }
            },
        )
    }

    private fun onBrokerConnected(settings: MqttSettings) {
        session.forgetSubscriptions()
        republishStatic(settings)
        syncSubscriptions(settings)
        publishStatus(settings)
    }

    private fun refreshMonitor(settings: MqttSettings) {
        val wasUp = monitorUp
        val health = try {
            api.health(settings.apiPort)
        } catch (error: ApiCallException) {
            markMonitorDown(settings, wasUp, "Нет связи с Monitor")
            return
        }
        val catalogChanged = health.catalogVersion != catalogVersion
        // /v1/health is open; only token calls prove the pairing is still valid.
        try {
            reloadCatalogIfNeeded(settings, force = unauthorized || catalogChanged)
            pollSignals(settings)
        } catch (error: ApiCallException) {
            if (error.httpStatus == 401 || error.code == "unauthorized") {
                unauthorized = true
                markMonitorDown(settings, wasUp, "Monitor не принимает токен")
                return
            }
            throw error
        }
        catalogVersion = health.catalogVersion
        unauthorized = false
        monitorUp = true
        if (!wasUp) publishAvailability(settings, "online")
        maybeRepeat(settings)
        syncSubscriptions(settings)
        lastError = ""
    }

    private fun markMonitorDown(settings: MqttSettings, wasUp: Boolean, reason: String) {
        monitorUp = false
        lastError = reason
        if (wasUp) {
            publishOffline()
            syncSubscriptions(settings)
        }
    }

    private fun reloadCatalogIfNeeded(settings: MqttSettings, force: Boolean) {
        val now = System.currentTimeMillis()
        if (force || catalogJson.isEmpty() || now - catalogAtMs >= CATALOG_PERIOD_MS) {
            catalogJson = api.catalog(settings.apiPort, settings.accessToken)
            val automations = api.automations(settings.apiPort, settings.accessToken)
                .map { AutomationRow(it.id, it.name) }
            catalogAtMs = now
            allEntities = buildEntities(catalogJson, automations)
        }
        val selected = settings.selectedObjectIds
        entities = allEntities.filter { it.objectId in selected }
        val liveIds = entities.map { it.objectId }.toSet()
        lastState.keys.retainAll(liveIds)
        fastPublish.retain(liveIds)
        val signature = entities.joinToString(",") { it.objectId } +
            "|${settings.discoveryEnabled}|${settings.deviceName}|${settings.deviceId}|" +
            "${settings.topicPrefix}|${settings.discoveryPrefix}|${settings.acceptCommands}"
        if (signature != publishSignature) {
            publishSignature = signature
            republishStatic(settings)
        }
    }

    private fun pollSignals(settings: MqttSettings) {
        val refs = entities.flatMap { entity ->
            val media = entity.media
            if (media != null) {
                media.signalIds.map { SignalRef(it, media.source) }
            } else {
                val id = entity.signalId ?: return@flatMap emptyList()
                val source = entity.source ?: return@flatMap emptyList()
                listOf(SignalRef(id, source))
            }
        }
        val samples = mutableListOf<SignalSample>()
        batchSignals(refs).forEach { batch ->
            samples += api.signals(settings.apiPort, settings.accessToken, batch.ids, batch.source)
        }
        val byId = samples.associateBy { it.id }
        val base = Topics.base(settings.topicPrefix, settings.deviceId)
        val now = System.currentTimeMillis()
        val fastIntervalMs = settings.fastPublishSeconds * 1000L
        var sawFresh = false
        entities.forEach { entity ->
            if (entity.media != null) {
                if (publishMedia(entity, byId, base, now, fastIntervalMs)) sawFresh = true
                return@forEach
            }
            val sample = byId[entity.signalId] ?: return@forEach
            if (!sample.available) return@forEach
            if (entity.component == HaComponent.DEVICE_TRACKER) {
                val point = geoOf(sample) ?: return@forEach
                if (location.shouldSend(point, now, force = false, minIntervalMs = fastIntervalMs)) {
                    publishGeo(base, entity, point)
                    location.markSent(point, now)
                    sawFresh = true
                }
                return@forEach
            }
            val number = sample.number
            val text = when {
                number != null -> StateFormat.numberText(number, entity.unit)
                sample.text != null -> StateFormat.text(sample.text, entity.unit, entity.component)
                else -> null
            } ?: return@forEach
            if (entity.component == HaComponent.SELECT && text !in entity.options) return@forEach
            if (lastState[entity.objectId] == text) return@forEach
            if (number != null && !fastPublish.allow(entity.objectId, now, fastIntervalMs)) return@forEach
            publishState(base, entity.objectId, text)
            sawFresh = true
        }
        if (sawFresh) publishLastSeen(settings, force = false)
    }

    private fun publishMedia(
        entity: CatalogEntity,
        byId: Map<String, SignalSample>,
        base: String,
        now: Long,
        fastIntervalMs: Long,
    ): Boolean {
        val samples = byId.mapValues { (_, sample) ->
            MediaSample(sample.available, sample.text, sample.number)
        }
        val previous = mediaViewOf(lastState[entity.objectId])
        val next = mergeMedia(previous, samples)
        if (next == previous) return false
        val positionOnly = previous != null && next.sameExceptPosition(previous)
        if (positionOnly) {
            if (!fastPublish.allow(entity.objectId, now, fastIntervalMs)) return false
        } else {
            fastPublish.mark(entity.objectId, now)
        }
        publishState(base, entity.objectId, next.json())
        return true
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
        cleanup.commit(next)
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
            val bundle = entity.media
            val command = if (bundle != null) {
                val parsed = parseMediaCommand(payload)
                if (parsed == null) {
                    lastError = "Команда не из списка"
                    restoreState(base, entity)
                    return
                }
                val playing = mediaViewOf(lastState[entity.objectId])?.state == "playing"
                val invoke = mediaInvoke(bundle, parsed, playing)
                if (invoke == null) {
                    if (parsed is MediaCommand.Volume) {
                        lastError = "Громкость вне диапазона"
                        restoreState(base, entity)
                    }
                    return
                }
                invoke
            } else {
                commandToInvoke(entity, payload)
            }
            if (command == null) {
                lastError = "Команда не из списка"
                restoreState(base, entity)
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
        val text = notificationText(monitorUp && !unauthorized, brokerUp)
        if (text == notifiedText) return
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
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
        notifiedText = text
        if (!channelReady && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
            channel.setSound(null, null)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            channelReady = true
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
        private const val START_SETTLE_MS = 700L

        fun start(context: Context) {
            val intent = Intent(context, MqttBridgeService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
    }
}
