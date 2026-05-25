package vad.dashing.tbox.mbcan

import android.util.Log
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Runtime loader for Android 10 vehicle JSON tables from firmware.
 *
 * Important: `send.json` / `receive.json` contain numeric IDs only.
 * We deliberately avoid heuristic semantic inference (no guessing).
 * Mapping uses:
 * 1) explicit overrides (empty by default),
 * 2) direct identity when requested id exists in firmware tables.
 */
object FirmwareVehicleJsonMapper {
    private const val TAG = "FirmwareVehicleMapper"
    private const val SEND_JSON_PATH = "/system/etc/adayo/vehicle/send.json"
    private const val RECEIVE_JSON_PATH = "/system/etc/adayo/vehicle/receive.json"

    private data class Tables(
        val sendIds: Set<Int>,
        val receiveIds: Set<Int>,
    )

    @Volatile
    private var cached: Tables? = null

    /**
     * Explicit verified translations between legacy mbCAN ids and firmware ids.
     * Keep empty until a mapping is confirmed from evidence (logs/dumps/docs).
     */
    private val explicitWriteIdMap: Map<Int, Int> = emptyMap()
    private val explicitReadIdMap: Map<Int, Int> = emptyMap()

    fun resolveWritePropertyId(requestedPropertyId: Int): Int? {
        val tables = loadTables() ?: return null
        explicitWriteIdMap[requestedPropertyId]?.let { return it }
        return requestedPropertyId.takeIf { tables.sendIds.contains(it) }
    }

    fun resolveReadPropertyId(requestedPropertyId: Int): Int? {
        val tables = loadTables() ?: return null
        explicitReadIdMap[requestedPropertyId]?.let { return it }
        return requestedPropertyId.takeIf { tables.receiveIds.contains(it) }
    }

    private fun loadTables(): Tables? {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val sendFile = File(SEND_JSON_PATH)
            val receiveFile = File(RECEIVE_JSON_PATH)
            if (!sendFile.isFile || !receiveFile.isFile) {
                Log.w(TAG, "Vehicle json files not found: $SEND_JSON_PATH / $RECEIVE_JSON_PATH")
                return null
            }
            val sendIds = runCatching { parseSendIds(sendFile.readText()) }.getOrElse {
                Log.e(TAG, "Failed to parse send.json: ${it.message}")
                emptySet()
            }
            val receiveIds = runCatching { parseReceiveIds(receiveFile.readText()) }.getOrElse {
                Log.e(TAG, "Failed to parse receive.json: ${it.message}")
                emptySet()
            }
            val tables = Tables(sendIds = sendIds, receiveIds = receiveIds)
            cached = tables
            return tables
        }
    }

    private fun parseSendIds(raw: String): Set<Int> {
        val root = JSONObject(raw)
        val send = root.optJSONArray("SendJSON") ?: JSONArray()
        val ids = linkedSetOf<Int>()
        for (i in 0 until send.length()) {
            val funcObj = send.optJSONObject(i) ?: continue
            val cmdMap = funcObj.optJSONArray("CmdMap") ?: continue
            for (j in 0 until cmdMap.length()) {
                val cmdObj = cmdMap.optJSONObject(j) ?: continue
                val cmdData = cmdObj.optJSONArray("cmdData") ?: continue
                for (k in 0 until cmdData.length()) {
                    val cd = cmdData.optJSONObject(k) ?: continue
                    val sendData = cd.optJSONArray("sendData") ?: continue
                    for (z in 0 until sendData.length()) {
                        val item = sendData.optJSONObject(z) ?: continue
                        item.takeIf { it.has("ID") }?.optInt("ID")?.let { ids.add(it) }
                    }
                }
            }
        }
        return ids
    }

    private fun parseReceiveIds(raw: String): Set<Int> {
        val root = JSONObject(raw)
        val receive = root.optJSONArray("ReceiveJSON") ?: JSONArray()
        val ids = linkedSetOf<Int>()
        for (i in 0 until receive.length()) {
            val funcObj = receive.optJSONObject(i) ?: continue
            val cmdMap = funcObj.optJSONArray("CmdMap") ?: continue
            for (j in 0 until cmdMap.length()) {
                val cmdObj = cmdMap.optJSONObject(j) ?: continue
                val rcvMap = cmdObj.optJSONArray("RcvMap") ?: continue
                for (k in 0 until rcvMap.length()) {
                    val item = rcvMap.optJSONObject(k) ?: continue
                    item.takeIf { it.has("ID") }?.optInt("ID")?.let { ids.add(it) }
                }
            }
        }
        return ids
    }
}
