package vad.dashing.mqtt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import vad.dashing.mqtt.ha.EntityGroup
import vad.dashing.mqtt.ui.theme.tboxBody
import vad.dashing.mqtt.ui.theme.tboxButton
import vad.dashing.mqtt.ui.theme.tboxHeadline
import vad.dashing.mqtt.ui.theme.tboxTabLabel
import vad.dashing.mqtt.ui.theme.tboxTitle

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
    TextField("Адрес", settings.brokerHost) {
        viewModel.update { current -> current.copy(brokerHost = it) }
        onSettingsSaved()
    }
    NumberField("Порт", settings.brokerPort.toString()) {
        viewModel.update { current -> current.copy(brokerPort = it.toIntOrNull() ?: current.brokerPort) }
        onSettingsSaved()
    }
    TextField("Пользователь", settings.username) {
        viewModel.update { current -> current.copy(username = it) }
        onSettingsSaved()
    }
    TextField("Пароль", settings.password, password = true) {
        viewModel.update { current -> current.copy(password = it) }
        onSettingsSaved()
    }
    SettingSwitch(
        checked = settings.tlsEnabled,
        title = "Шифрование TLS",
        description = if (settings.tlsEnabled) "Порт обычно 8883" else "По умолчанию выключено, порт 1883",
        onChecked = {
            viewModel.update { current -> current.copy(tlsEnabled = it) }
            onSettingsSaved()
        },
    )
    viewModel.brokerWarning()?.let { warning ->
        Text(
            warning,
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    OutlinedButton(
        onClick = viewModel::checkBroker,
        enabled = !state.brokerBusy,
        modifier = Modifier.padding(top = 12.dp),
    ) {
        Text("Проверить", style = MaterialTheme.typography.tboxButton)
    }
    if (state.brokerMessage.isNotBlank()) {
        Text(
            state.brokerMessage,
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    SettingSwitch(
        checked = settings.autostart,
        title = "Автозапуск",
        description = "Поднимать мост после включения головного устройства",
        onChecked = {
            viewModel.update { current -> current.copy(autostart = it) }
            onSettingsSaved()
        },
    )
    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
    OutlinedButton(onClick = viewModel::toggleAdvanced) {
        Text(if (state.advancedOpen) "Скрыть дополнительно" else "Дополнительно", style = MaterialTheme.typography.tboxButton)
    }
    if (state.advancedOpen) {
        TextField("Идентификатор машины", settings.deviceId) {
            viewModel.update { current -> current.copy(deviceId = it) }
            onSettingsSaved()
        }
        TextField("Префикс топиков", settings.topicPrefix) {
            viewModel.update { current -> current.copy(topicPrefix = it) }
            onSettingsSaved()
        }
        TextField("Префикс discovery", settings.discoveryPrefix) {
            viewModel.update { current -> current.copy(discoveryPrefix = it) }
            onSettingsSaved()
        }
        NumberField("Интервал опроса, с", settings.pollSeconds.toString()) {
            viewModel.update { current -> current.copy(pollSeconds = it.toIntOrNull() ?: current.pollSeconds) }
            onSettingsSaved()
        }
        NumberField("Повтор состояний, мин", settings.repeatMinutes.toString()) {
            viewModel.update { current -> current.copy(repeatMinutes = it.toIntOrNull() ?: current.repeatMinutes) }
            onSettingsSaved()
        }
        TextField("PEM своего CA", settings.caPem) {
            viewModel.update { current -> current.copy(caPem = it) }
            onSettingsSaved()
        }
        TextField("Идентификатор MQTT-клиента", settings.mqttClientId) {
            viewModel.update { current -> current.copy(mqttClientId = it) }
            onSettingsSaved()
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
private fun StatusTab(state: MqttHomeState) {
    val bridge = state.bridge
    StatusLine("Monitor", if (bridge.monitorUp) "на связи" else "нет связи")
    StatusLine("Брокер", if (bridge.brokerUp) "на связи" else "нет связи")
    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
    StatusLine("Home Assistant видит", bridge.availability)
    StatusLine("Опубликовано сущностей", bridge.publishedCount.toString())
    if (bridge.lastError.isNotBlank()) {
        Text(
            bridge.lastError,
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun EntitiesTab(
    state: MqttHomeState,
    viewModel: MqttHomeViewModel,
    onSettingsSaved: () -> Unit,
) {
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
    EntityGroup.entries.forEach { group ->
        val rows = state.entities.filter { it.group == group }
        if (rows.isEmpty()) return@forEach
        SectionTitle(group.title)
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
private fun StatusLine(title: String, value: String) {
    Text(title, style = MaterialTheme.typography.tboxTitle, color = MaterialTheme.colorScheme.onSurface)
    Text(
        value,
        style = MaterialTheme.typography.tboxBody,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
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
    TextField(label, value, onValue = onValue)
}
