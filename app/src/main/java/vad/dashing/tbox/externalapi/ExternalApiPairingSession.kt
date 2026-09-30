package vad.dashing.tbox.externalapi

import android.os.SystemClock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class ExternalApiPairRequestStatus {
    PENDING,
    APPROVED,
    DENIED,
}

data class ExternalApiPairRequest(
    val requestId: String,
    val clientId: String,
    val clientName: String,
    val clientKind: String?,
    val status: ExternalApiPairRequestStatus,
    val accessToken: String? = null,
    val createdAtElapsed: Long = SystemClock.elapsedRealtime(),
)

class ExternalApiPairingSession {
    @Volatile
    private var active: Boolean = false

    @Volatile
    private var expiresAtElapsed: Long = 0L

    private val pendingRequests = ConcurrentHashMap<String, ExternalApiPairRequest>()

    fun startPairing(timeoutMs: Long = ExternalApiConstants.PAIRING_TIMEOUT_MS) {
        active = true
        expiresAtElapsed = SystemClock.elapsedRealtime() + timeoutMs
        pendingRequests.clear()
    }

    fun stopPairing() {
        active = false
        expiresAtElapsed = 0L
        // Drop unresolved PENDING only. Keep APPROVED/DENIED so the client can still
        // poll /v1/pair/status and receive accessToken after the HU dialog closes
        // (approvePair → stopPairing used to wipe the map and caused not_found).
        pendingRequests.entries.removeIf { (_, req) ->
            req.status == ExternalApiPairRequestStatus.PENDING
        }
    }

    fun isPairingActive(): Boolean {
        if (!active) return false
        if (SystemClock.elapsedRealtime() >= expiresAtElapsed) {
            stopPairing()
            return false
        }
        return true
    }

    fun pairingExpiresAtElapsed(): Long? =
        if (isPairingActive()) expiresAtElapsed else null

    fun submitPairRequest(
        clientId: String,
        clientName: String,
        clientKind: String?,
    ): ExternalApiPairRequest {
        require(isPairingActive()) { "Pairing is not active" }
        val request = ExternalApiPairRequest(
            requestId = UUID.randomUUID().toString(),
            clientId = clientId.trim(),
            clientName = clientName.trim(),
            clientKind = clientKind?.trim()?.takeIf { it.isNotEmpty() },
            status = ExternalApiPairRequestStatus.PENDING,
        )
        pendingRequests[request.requestId] = request
        return request
    }

    fun pendingRequestsSnapshot(): List<ExternalApiPairRequest> =
        pendingRequests.values
            .filter { it.status == ExternalApiPairRequestStatus.PENDING }
            .sortedBy { it.createdAtElapsed }

    fun getRequest(requestId: String): ExternalApiPairRequest? =
        pendingRequests[requestId.trim()]

    fun approveRequest(requestId: String, accessToken: String): ExternalApiPairRequest? {
        val current = pendingRequests[requestId.trim()] ?: return null
        if (current.status != ExternalApiPairRequestStatus.PENDING) return current
        val approved = current.copy(
            status = ExternalApiPairRequestStatus.APPROVED,
            accessToken = accessToken,
        )
        pendingRequests[requestId.trim()] = approved
        return approved
    }

    fun denyRequest(requestId: String): ExternalApiPairRequest? {
        val current = pendingRequests[requestId.trim()] ?: return null
        if (current.status != ExternalApiPairRequestStatus.PENDING) return current
        val denied = current.copy(status = ExternalApiPairRequestStatus.DENIED)
        pendingRequests[requestId.trim()] = denied
        return denied
    }
}
