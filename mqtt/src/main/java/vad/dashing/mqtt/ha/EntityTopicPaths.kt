package vad.dashing.mqtt.ha

/** State and optional command topics for one catalog row — same paths as discovery / WQTT. */
data class EntityTopicPaths(
    val stateTopic: String,
    val commandTopic: String?,
)

fun entityTopicPaths(
    topicPrefix: String,
    deviceId: String,
    objectId: String,
    writable: Boolean,
    component: HaComponent,
): EntityTopicPaths {
    val base = Topics.base(topicPrefix, deviceId)
    val stateTopic = when (component) {
        HaComponent.DEVICE_TRACKER -> Topics.attributes(base, objectId)
        HaComponent.SENSOR,
        HaComponent.BINARY_SENSOR,
        HaComponent.SWITCH,
        HaComponent.NUMBER,
        HaComponent.SELECT,
        HaComponent.BUTTON,
        -> Topics.state(base, objectId)
    }
    return EntityTopicPaths(
        stateTopic = stateTopic,
        commandTopic = if (writable) Topics.set(base, objectId) else null,
    )
}

/** Two-line caption under an entity label in the Entities tab. */
fun formatEntityTopicCaption(
    paths: EntityTopicPaths,
    noneLabel: String = "нет",
): String {
    val command = paths.commandTopic ?: noneLabel
    return "состояние: ${paths.stateTopic}\nкоманда: $command"
}
