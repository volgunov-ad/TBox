package vad.dashing.mqtt.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import vad.dashing.mqtt.ha.EntityGroup
import vad.dashing.mqtt.settings.MqttSettings
import vad.dashing.mqtt.wireguard.parseWgConf
import vad.dashing.mqtt.ui.theme.tboxBody
import vad.dashing.mqtt.ui.theme.tboxButton
import vad.dashing.mqtt.ui.theme.tboxHeadline
import vad.dashing.mqtt.ui.theme.tboxTabLabel
import vad.dashing.mqtt.ui.theme.tboxTitle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Tabs = listOf("Подключение", "Состояние", "Сущности")

@Composable
fun MqttHomeScreen(
    onSettingsSaved: () -> Unit,
    viewModel: MqttHomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TabRow(selected = state.tab, onSelect = viewModel::selectTab)
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            when (state.tab) {
                0 -> ConnectionTab(state, viewModel, onSettingsSaved)
                1 -> StatusTab(state)
                else -> EntitiesTab(state, viewModel, onSettingsSaved)
            }
        }
    }
}

@Composable
private fun TabRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        Tabs.forEachIndexed { index, title ->
            val active = index == selected
            Text(
                text = title,
                style = MaterialTheme.typography.tboxTabLabel,
                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 28.dp, vertical = 16.dp),
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun ConnectionTab(
    state: MqttHomeState,
    viewModel: MqttHomeViewModel,
    onSettingsSaved: () -> Unit,
) {
    val settings = state.settings
    val connection = state.connectionDraft
    LinkSummary(state)
    SectionTitle("Monitor")
    NumberField("Порт API", settings.apiPort.toString()) {
        viewModel.update { current -> current.copy(apiPort = it.toIntOrNull() ?: current.apiPort) }
        onSettingsSaved()
    }
    Button(
        onClick = {
            onSettingsSaved()
            viewModel.sendPairRequest()
        },
        enabled = !state.pairBusy,
        modifier = Modifier.padding(top = 12.dp),
    ) {
        Text("Отправить запрос", style = MaterialTheme.typography.tboxButton)
    }
    if (state.pairMessage.isNotBlank()) {
        Text(
            state.pairMessage,
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
    SectionTitle("Брокер")
    SettingSwitch(
        checked = settings.brokerEnabled,
        title = "Подключаться к MQTT брокеру",
        description = if (settings.brokerEnabled) {
            "Мост работает, данные уходят в Home Assistant"
        } else {
            "Мост остановлен, Home Assistant видит машину offline. Настройки сохранены"
        },
        onChecked = { enabled ->
            viewModel.setBrokerEnabled(enabled)
            onSettingsSaved()
        },
    )
    TextField("Адрес", connection.brokerHost) {
        viewModel.editConnection { current -> current.copy(brokerHost = it) }
    }
    NumberField("Порт", connection.brokerPort.toString()) {
        viewModel.editConnection { current -> current.copy(brokerPort = it.toIntOrNull() ?: current.brokerPort) }
    }
    TextField("Пользователь", connection.username) {
        viewModel.editConnection { current -> current.copy(username = it) }
    }
    TextField("Пароль", connection.password, password = true) {
        viewModel.editConnection { current -> current.copy(password = it) }
    }
    SettingSwitch(
        checked = connection.tlsEnabled,
        title = "Шифрование TLS",
        description = if (connection.tlsEnabled) "Порт обычно 8883" else "По умолчанию выключено, порт 1883",
        onChecked = { enabled ->
            viewModel.editConnection { current -> current.copy(tlsEnabled = enabled) }
        },
    )
    WireguardBlock(connection, viewModel)
    viewModel.brokerWarning()?.let { warning ->
        Text(
            warning,
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    val dirty = viewModel.connectionDirty()
    Row(
        modifier = Modifier.padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = {
                viewModel.saveConnection()
                onSettingsSaved()
            },
        ) {
            Text("Сохранить", style = MaterialTheme.typography.tboxButton)
        }
        OutlinedButton(
            onClick = viewModel::checkBroker,
            enabled = !state.brokerBusy,
        ) {
            Text("Проверить", style = MaterialTheme.typography.tboxButton)
        }
    }
    if (dirty) {
        Text(
            "Есть несохранённые изменения",
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    listOf(state.connectionMessage, state.brokerMessage)
        .filter { it.isNotBlank() }
        .forEach { message ->
            Text(
                message,
                style = MaterialTheme.typography.tboxBody,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    SettingSwitch(
        checked = settings.autostart,
        title = "Автозапуск",
        description = "Поднимать мост после включения головного устройства",
        onChecked = { enabled ->
            viewModel.update { current -> current.copy(autostart = enabled) }
        },
    )
    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
    OutlinedButton(onClick = viewModel::toggleAdvanced) {
        Text(if (state.advancedOpen) "Скрыть дополнительно" else "Дополнительно", style = MaterialTheme.typography.tboxButton)
    }
    if (state.advancedOpen) {
        TextField("Идентификатор машины", connection.deviceId) {
            viewModel.editConnection { current -> current.copy(deviceId = it) }
        }
        TextField("Префикс топиков", connection.topicPrefix) {
            viewModel.editConnection { current -> current.copy(topicPrefix = it) }
        }
        TextField("Префикс discovery", connection.discoveryPrefix) {
            viewModel.editConnection { current -> current.copy(discoveryPrefix = it) }
        }
        NumberField("Интервал опроса, с", connection.pollSeconds.toString()) {
            viewModel.editConnection { current ->
                current.copy(pollSeconds = it.toIntOrNull() ?: current.pollSeconds)
            }
        }
        NumberField("Повтор состояний, мин", connection.repeatMinutes.toString()) {
            viewModel.editConnection { current ->
                current.copy(repeatMinutes = it.toIntOrNull() ?: current.repeatMinutes)
            }
        }
        NumberField("Быстрые данные, не чаще, с", connection.fastPublishSeconds.toString()) {
            viewModel.editConnection { current ->
                current.copy(fastPublishSeconds = it.toIntOrNull() ?: current.fastPublishSeconds)
            }
        }
        Text(
            "Скорость, обороты, другие числа и местоположение. 0 — при каждом изменении. Местоположение ещё ждёт сдвига на 50 м.",
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        TextField("PEM своего CA", connection.caPem) {
            viewModel.editConnection { current -> current.copy(caPem = it) }
        }
        TextField("Идентификатор MQTT-клиента", connection.mqttClientId) {
            viewModel.editConnection { current -> current.copy(mqttClientId = it) }
        }
        OutlinedButton(onClick = viewModel::toggleManualToken, modifier = Modifier.padding(top = 8.dp)) {
            Text("Токен вручную", style = MaterialTheme.typography.tboxButton)
        }
        if (state.manualTokenOpen) {
            TextField("Токен", state.manualToken) { viewModel.setManualToken(it) }
            Button(onClick = viewModel::saveManualToken, modifier = Modifier.padding(top = 8.dp)) {
                Text("Сохранить токен", style = MaterialTheme.typography.tboxButton)
            }
        }
    }
}

@Composable
private fun WireguardBlock(connection: MqttSettings, viewModel: MqttHomeViewModel) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        when (val text = readConf(context, uri)) {
            null -> viewModel.reportConnection("Не удалось прочитать файл")
            ConfTooLarge -> viewModel.reportConnection("Файл слишком большой")
            else -> viewModel.importWireguard(displayName(context, uri), text)
        }
    }
    val hasFile = connection.wireguardConf.isNotBlank()
    var open by rememberSaveable { mutableStateOf(false) }
    val fileName = connection.wireguardFileName.ifBlank { "wireguard.conf" }
    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
    SettingSwitch(
        checked = connection.wireguardEnabled,
        title = if (hasFile) "Через WireGuard: $fileName" else "Через WireGuard",
        description = if (hasFile) {
            "К брокеру ходит только эта программа. Остальные приложения головного устройства туннель не видят"
        } else {
            "Сначала выберите файл .conf"
        },
        onChecked = viewModel::setWireguardEnabled,
    )
    if (hasFile) {
        OutlinedButton(onClick = { open = !open }) {
            Text(if (open) "Скрыть файл WireGuard" else "Файл WireGuard", style = MaterialTheme.typography.tboxButton)
        }
    }
    if (hasFile && !open) return
    OutlinedButton(
        onClick = { picker.launch(arrayOf("*/*")) },
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Text(if (hasFile) "Выбрать другой файл .conf" else "Выбрать файл .conf", style = MaterialTheme.typography.tboxButton)
    }
    if (hasFile) {
        val parsed = parseWgConf(connection.wireguardConf)
        Text(
            parsed.fold(
                onSuccess = { it.summary() },
                onFailure = { it.message ?: "Файл не разобран" },
            ),
            style = MaterialTheme.typography.tboxBody,
            color = if (parsed.isSuccess) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        OutlinedButton(
            onClick = viewModel::clearWireguard,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text("Убрать файл", style = MaterialTheme.typography.tboxButton)
        }
    }
}

private const val ConfTooLarge = "\u0000too-large"

private fun readConf(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(64 * 1024 + 1)
            var offset = 0
            while (offset < buffer.size) {
                val count = input.read(buffer, offset, buffer.size - offset)
                if (count < 0) break
                offset += count
            }
            if (offset > 64 * 1024) return ConfTooLarge
            String(buffer, 0, offset, Charsets.UTF_8)
        }
    } catch (_: Exception) {
        null
    }
}

private fun displayName(context: Context, uri: Uri): String {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return ""
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index < 0) "" else cursor.getString(index).orEmpty()
            }
            .orEmpty()
    } catch (_: Exception) {
        ""
    }
}

@Composable
private fun StatusTab(state: MqttHomeState) {
    val bridge = state.bridge
    if (!state.settings.brokerEnabled) {
        StatusLine("Мост", "выключен: «Подключаться к MQTT брокеру» на вкладке «Подключение»", ok = false)
        return
    }
    StatusLine(
        "Monitor",
        linkText(bridge.monitorUp, bridge.monitorSinceMs),
        ok = bridge.monitorUp,
        detail = bridge.monitorError,
    )
    StatusLine(
        "Брокер",
        linkText(bridge.brokerUp, bridge.brokerSinceMs),
        ok = bridge.brokerUp,
        detail = bridge.brokerError,
    )
    if (bridge.wireguardEnabled) {
        StatusLine(
            "WireGuard",
            when {
                bridge.brokerUp -> "брокер доступен через туннель"
                bridge.tunnelError.isNotEmpty() -> "ошибка туннеля"
                else -> "брокер не отвечает через туннель"
            },
            ok = bridge.brokerUp,
            detail = bridge.tunnelError,
        )
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
    StatusLine("Home Assistant видит", bridge.availability)
    StatusLine("Опубликовано сущностей", bridge.publishedCount.toString())
    StatusLine(
        "Последняя отправка в брокер",
        if (bridge.lastPublishAtMs > 0L) clockText(bridge.lastPublishAtMs) else "ещё не было",
    )
    if (bridge.lastError.isNotBlank()) {
        Text(
            bridge.lastError,
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
    }
    if (bridge.events.isEmpty()) return
    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
    SectionTitle("Журнал связи")
    bridge.events.forEach { event ->
        StatusLine(
            "${clockText(event.atMs)}  ${event.link}",
            if (event.up) "на связи" else "нет связи",
            ok = event.up,
            detail = event.detail,
        )
    }
}

@Composable
private fun LinkSummary(state: MqttHomeState) {
    val bridge = state.bridge
    val links = buildList {
        if (!state.settings.brokerEnabled) {
            add("Мост выключен" to false)
        } else {
            add("Monitor" to bridge.monitorUp)
            add("Брокер" to bridge.brokerUp)
            if (bridge.wireguardEnabled) add("WireGuard" to bridge.brokerUp)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        links.forEach { (title, up) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(
                            if (up) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            CircleShape,
                        ),
                )
                Text(
                    title,
                    style = MaterialTheme.typography.tboxTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun EntitiesTab(
    state: MqttHomeState,
    viewModel: MqttHomeViewModel,
    onSettingsSaved: () -> Unit,
) {
    var filterText by rememberSaveable { mutableStateOf("") }
    var onlySelected by rememberSaveable { mutableStateOf(false) }
    if (state.settings.accessToken.isBlank()) {
        Text(
            "Сначала подключитесь к Monitor",
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurface,
        )
        return
    }
    val settings = state.settings
    TextField("Имя в Home Assistant", settings.deviceName) {
        viewModel.update { current -> current.copy(deviceName = it) }
        onSettingsSaved()
    }
    SettingSwitch(
        checked = settings.discoveryEnabled,
        title = "Показывать в Home Assistant",
        description = "Отправлять конфигурацию MQTT Discovery",
        onChecked = {
            viewModel.update { current -> current.copy(discoveryEnabled = it) }
            onSettingsSaved()
        },
    )
    SettingSwitch(
        checked = settings.acceptCommands,
        title = "Принимать команды",
        description = "Выключено — команды в Home Assistant недоступны, датчики продолжают обновляться",
        onChecked = {
            viewModel.update { current -> current.copy(acceptCommands = it) }
            onSettingsSaved()
        },
    )
    if (state.entitiesMessage.isNotBlank()) {
        Text(state.entitiesMessage, style = MaterialTheme.typography.tboxBody, modifier = Modifier.padding(top = 8.dp))
    }
    if (state.entities.isEmpty()) return
    EntityFilterField(filterText) { filterText = it }
    SettingSwitch(
        checked = onlySelected,
        title = "Только отмеченные",
        description = "Отмечено ${state.entities.count { it.objectId in settings.selectedObjectIds }} из ${state.entities.size}",
        onChecked = { onlySelected = it },
    )
    var any = false
    EntityGroup.entries.forEach { group ->
        val inGroup = state.entities.filter { it.group == group }
        val rows = inGroup.filter { entity ->
            (!onlySelected || entity.objectId in settings.selectedObjectIds) &&
                entityMatchesFilter(entity.label, entity.description, entity.objectId, group.title, filterText)
        }
        if (rows.isEmpty()) return@forEach
        any = true
        val picked = inGroup.count { it.objectId in settings.selectedObjectIds }
        val allRowsPicked = rows.all { it.objectId in settings.selectedObjectIds }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SectionTitle("${group.title} $picked/${inGroup.size}")
            }
            OutlinedButton(
                onClick = {
                    viewModel.setEntities(rows.map { it.objectId }, enabled = !allRowsPicked)
                    onSettingsSaved()
                },
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text(if (allRowsPicked) "Снять все" else "Отметить все", style = MaterialTheme.typography.tboxButton)
            }
        }
        rows.forEach { entity ->
            SettingSwitch(
                checked = entity.objectId in settings.selectedObjectIds,
                title = entity.label,
                description = entity.description,
                onChecked = { enabled ->
                    viewModel.toggleEntity(entity.objectId, enabled)
                    onSettingsSaved()
                },
            )
        }
    }
    if (!any) {
        Text(
            "Ничего не найдено",
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Same match as the widget picker: title, description and the stored key. */
internal fun entityMatchesFilter(
    label: String,
    description: String,
    objectId: String,
    groupTitle: String,
    query: String,
): Boolean {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return true
    return label.lowercase().contains(needle) ||
        description.lowercase().contains(needle) ||
        objectId.lowercase().contains(needle) ||
        groupTitle.lowercase().contains(needle)
}

@Composable
private fun EntityFilterField(value: String, onValue: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        textStyle = MaterialTheme.typography.tboxTitle,
        label = { Text("Поиск", style = MaterialTheme.typography.tboxBody) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValue("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Очистить")
                }
            }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.tboxHeadline,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun StatusLine(title: String, value: String, ok: Boolean? = null, detail: String = "") {
    Text(title, style = MaterialTheme.typography.tboxTitle, color = MaterialTheme.colorScheme.onSurface)
    Text(
        value,
        style = MaterialTheme.typography.tboxBody,
        color = when (ok) {
            true -> MaterialTheme.colorScheme.primary
            false -> MaterialTheme.colorScheme.error
            null -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.padding(bottom = if (detail.isBlank()) 8.dp else 0.dp),
    )
    if (detail.isNotBlank()) {
        Text(
            detail,
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

private val ClockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

internal fun clockText(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(ms).atZone(zone).format(ClockFormat)

internal fun linkText(up: Boolean, sinceMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val state = if (up) "на связи" else "нет связи"
    if (sinceMs <= 0L) return state
    return "$state с ${clockText(sinceMs, zone)}"
}

@Composable
private fun SettingSwitch(
    checked: Boolean,
    title: String,
    description: String,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Switch(checked = checked, onCheckedChange = onChecked)
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.tboxTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (description.isNotBlank()) {
                Text(
                    description,
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TextField(
    label: String,
    value: String,
    password: Boolean = false,
    onValue: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label, style = MaterialTheme.typography.tboxBody) },
        textStyle = MaterialTheme.typography.tboxBody,
        singleLine = !label.contains("PEM"),
        visualTransformation = if (password) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
}

@Composable
private fun NumberField(label: String, value: String, onValue: (String) -> Unit) {
    // Local text lets the field be cleared before typing; the model only takes valid numbers.
    var text by remember(value) { mutableStateOf(value) }
    TextField(label, text) { typed ->
        val digits = typed.filter { it.isDigit() }.take(6)
        text = digits
        if (digits.isNotEmpty()) onValue(digits)
    }
}
