package vad.dashing.voice.llm

data class CloudChatMessage(
    val role: String,
    val content: String? = null,
    val toolCalls: List<CloudToolCall> = emptyList(),
)

data class CloudToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

data class CloudChatResult(
    val message: CloudChatMessage,
    val finishReason: String,
)
