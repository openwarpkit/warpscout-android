package io.github.openwarpkit.warpscout.core

data class OperationState(
    val running: Boolean = false,
    val operation: String = "",
    val phase: String = "",
    val completed: Int = 0,
    val total: Int = 0,
    val working: Int = 0,
    val tornDown: Int = 0,
    val regions: Set<String> = emptySet(),
    val nodes: Set<String> = emptySet(),
    val bestEndpoint: String? = null,
    val startedAt: Long = 0,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val latestResultJson: String? = null,
    val historyId: Long? = null,
    val localPort: Int? = null
) {
    val progress: Float
        get() = if (total > 0) completed.toFloat() / total else 0f
}

data class OperationRequest(
    val operation: String,
    val payloadJson: String,
    val preset: String = "",
    val protocol: String = "",
    val localPort: Int? = null
)
