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

    // Direct VHAL telemetry property ids used by Android10VhalRepository.
    const val VHAL_ENGINE_RPM_PROPERTY_ID = 289_414_951 // R_0900_EMS_1_EngineSpd
    const val VHAL_ENGINE_TEMPERATURE_PROPERTY_ID = 289_414_949 // R_0900_EMS1G_EngineCoolantTemperture
    const val VHAL_CAR_SPEED_PROPERTY_ID = 289_414_964 // R_0900_ICM_1_DisplayVehicleSpeed

    private data class Tables(
        val sendIds: Set<Int>,
        val receiveIds: Set<Int>,
    )

    @Volatile
    private var cached: Tables? = null

    /**
     * Explicit verified translations between legacy mbCAN ids and firmware ids.
     *
     * Evidence source:
     * - D:\Dashing\AirConditioning\sources\android\car\VehiclePropertyIds.java
     * - D:\Dashing\CarSettings\sources\android\car\VehiclePropertyIds.java
     * - usage in AirConditioning / CarSettings app sources (setIntProperty/getIntProperty).
     */
    private val explicitWriteIdMap: Map<Int, Int> = mapOf(
        // MBVehicleProperty.eVEHICLE_SET_MFS_HEAT_SWITCH
        MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH to 289412679, // T_0401_SET_MFS_Heat
        // MBVehicleProperty.eVEHICLE_SET_WIPER_MAINTENANCE_SWITCH
        MbCanKnownVehiclePropertyId.WIPER_MAINTENANCE_SWITCH to 289412682, // T_0401_SET_Wiper_Maintenance
        // MBVehicleProperty.eVEHICLE_SET_PAS_SWITCH
        MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH to 289415942, // T_0B01_SET_PAS_Switch
        // MBVehicleProperty.eVHEICEL_FRONTWINDSCREEN_HEAT
        MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH to 289415309, // T_0201_SET_FrontWindscreenHeatiReq
        // MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_FAN_DIRECTION
        MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION to 289415298, // T_0201_IHU_5_ModeAdjust_Req
        // MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_DEFROSTER (rear window + mirrors)
        MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH to 289415299, // T_0201_IHU_5_RearDefrostSwitch_Req
        // MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_AIR_RECIRCULATION
        MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION to 289415302, // T_0201_IHU_5_CirculationMode_Req
        // MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_POWER
        MbCanKnownVehiclePropertyId.HVAC_POWER to 289415300, // T_0201_IHU_5_ACRequestCommand
        // MBVehicleProperty.eHVAC_AUTO_STATE
        MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE to 289415311, // T_0201_IHU_5_AutoState
        MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_LEFT to 289415313, // T_0201_IHU_5_L_Set_Temperature
        MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RIGHT to 289415314, // T_0201_IHU_5_R_Set_Temperature
        MbCanKnownVehiclePropertyId.HVAC_FAN_SPEED to 289415296, // T_0201_IHU_5_BlowSpeedLevel_Req
        MbCanKnownVehiclePropertyId.HVAC_FRONT_OFF to 289415301, // T_0201_IHU_5_FrontOFF_Req
        MbCanKnownVehiclePropertyId.HVAC_SYNC_SWITCH to 289415308, // T_0201_IHU_5_SyncSwtich_Req
        MbCanKnownVehiclePropertyId.TRUNK_PLG_CONTROL to 289412638, // T_0403_SET_PLG_Control
        MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH to 289415316, // T_0201_SET_FLSeatHeatVentSwReq
        MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH to 289415315, // T_0201_SET_FRSeatHeatVentSwReq
        MbCanKnownVehiclePropertyId.REAR_LEFT_SEAT_HEAT_SWITCH to 289415345, // T_0203_SET_LRSeatHeatVentSwReq
        MbCanKnownVehiclePropertyId.REAR_RIGHT_SEAT_HEAT_SWITCH to 289415344, // T_0203_SET_RRSeatHeatVentSwReq
        // Car settings (drive/eps)
        MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_EPS_MODE to 289412662, // T_0401_IHU_1_DVD_SET_EPSmode
        MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE to 289412695, // T_0401_IHU_9_DriveMode
        MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET to 289412692, // T_0401_IHU_9_DriveMode_6DCT_Wet
        // Audio
        MbCanKnownAudioPropertyId.VOLUME to 557849090, // AUDIO_CURRENT_MAIN_VOLUME
        MbCanKnownAudioPropertyId.VOLUME_SPEED to 557849227, // AUDIO_VOL_VSC_MOD_REQ
    )

    private val explicitReadIdMap: Map<Int, Int> = mapOf(
        // MBVehicleProperty.eVEHICLE_SET_MFS_HEAT_SWITCH
        MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH to 289412111, // R_0400_RBCM_MFS_HeatSts
        // MBVehicleProperty.eVEHICLE_SET_WIPER_MAINTENANCE_SWITCH
        MbCanKnownVehiclePropertyId.WIPER_MAINTENANCE_SWITCH to 289412194, // R_0400_CEM_Wiper_MaintenanceSts
        // MBVehicleProperty.eVEHICLE_SET_PAS_SWITCH
        MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH to 289412233, // R_0400_CEM_RAD1_Pas_Switch
        // MBVehicleProperty.eVHEICEL_FRONTWINDSCREEN_HEAT
        MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH to 289412114, // R_0400_RBCM_FGHeat_Request_CommandFeedb
        // MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_FAN_DIRECTION
        MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION to 289415174, // R_0200_CEM_IPM_FrontBlowModeSts
        // MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_DEFROSTER (rear window + mirrors)
        MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH to 289415177, // R_0200_CEM_IPM_RearDefrosts
        // MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_AIR_RECIRCULATION
        MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION to 289415172, // R_0200_CEM_IPM_RecyMode
        // MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_POWER
        MbCanKnownVehiclePropertyId.HVAC_POWER to 289415180, // R_0200_CEM_IPM_AC_DisplaySts
        // MBVehicleProperty.eHVAC_AUTO_STATE
        MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE to 289415182, // R_0200_CEM_IPM_FrontAutoACSts
        MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_LEFT to 289415169, // R_0200_CEM_IPM_FLTempsts
        MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_RIGHT to 289415168, // R_0200_CEM_IPM_FRTempsts
        MbCanKnownVehiclePropertyId.HVAC_FAN_SPEED to 289415171, // R_0200_CEM_IPM_FrontBlowSpdCtrlsts
        MbCanKnownVehiclePropertyId.HVAC_FRONT_OFF to 289415175, // R_0200_CEM_IPM_FrontOFFSts
        MbCanKnownVehiclePropertyId.HVAC_SYNC_SWITCH to 289415181, // R_0200_CEM_IPM_SyncSts
        MbCanKnownVehiclePropertyId.TRUNK_REAR_DOOR_MOVE_DIR to 289412272, // R_0402_PLG_1_RearDoorMoveDir
        MbCanKnownVehiclePropertyId.TRUNK_REAR_DOOR_STATUS to 289412273, // R_0402_PLG_1_RearDoorStatus
        // Seat states
        MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH to 289415193, // R_0200_CEM_IPM_FLSeatHeatVentSwSts
        MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH to 289415192, // R_0200_CEM_IPM_FRSeatHeatVentSwSts
        MbCanKnownVehiclePropertyId.REAR_LEFT_SEAT_HEAT_SWITCH to 289415203, // R_0202_RBCM_2_LRSeatHeatVentSwSts
        MbCanKnownVehiclePropertyId.REAR_RIGHT_SEAT_HEAT_SWITCH to 289415202, // R_0202_RBCM_2__RRSeatHeatVentSwSts
        // Car settings (drive/eps)
        MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_EPS_MODE to 289412124, // R_0400_EPS_1_EPSModeSts
        MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE to 289412123, // R_0400_TCU_G_DriverMode_7
        MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET to 289412692, // T_0401_IHU_9_DriveMode_6DCT_Wet
        // Audio
        MbCanKnownAudioPropertyId.VOLUME to 557849090, // AUDIO_CURRENT_MAIN_VOLUME
        MbCanKnownAudioPropertyId.VOLUME_SPEED to 557849227, // AUDIO_VOL_VSC_MOD_REQ
    )

    fun resolveWritePropertyId(requestedPropertyId: Int): Int? {
        explicitWriteIdMap[requestedPropertyId]?.let { return it }
        val tables = loadTables() ?: return null
        return requestedPropertyId.takeIf { tables.sendIds.contains(it) }
    }

    fun resolveReadPropertyId(requestedPropertyId: Int): Int? {
        explicitReadIdMap[requestedPropertyId]?.let { return it }
        val tables = loadTables() ?: return null
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
