package com.dashing.tbox

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NetState(
    val csq: Int = 99,
    val netStatus: String = "",
    val regStatus: String = "",
    val simStatus: String = ""
)

data class NetValues(
    val imei: String = "",
    val iccid: String = "",
    val imsi: String = "",
    val operator: String = ""
)

data class APNState(
    val apnStatus: String = "",
    val apnType: String = "",
    val apnIP: String = "",
    val apnGate: String = "",
    val apnDNS1: String = "",
    val apnDNS2: String = ""
)

object TboxRepository {
    private val _netState = MutableStateFlow(NetState())
    val netState: StateFlow<NetState> = _netState.asStateFlow()

    private val _netValues = MutableStateFlow(NetValues())
    val netValues: StateFlow<NetValues> = _netValues.asStateFlow()

    private val _apnState = MutableStateFlow(APNState())
    val apnState: StateFlow<APNState> = _apnState.asStateFlow()

    private val _apn2State = MutableStateFlow(APNState())
    val apn2State: StateFlow<APNState> = _apn2State.asStateFlow()

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    private val _tboxConnected = MutableStateFlow(false)
    val tboxConnected: StateFlow<Boolean> = _tboxConnected.asStateFlow()

    fun updateMessage(value: String) {
        _message.value = value
    }

    fun updateTboxConnected(value: Boolean) {
        _tboxConnected.value = value
    }

    fun updateNetState(newState: NetState) {
        _netState.value = newState
    }

    fun updateNetValues(newValues: NetValues) {
        _netValues.value = newValues
    }

    fun updateAPNState(newState: APNState) {
        _apnState.value = newState
    }

    fun updateAPN2State(newState: APNState) {
        _apn2State.value = newState
    }
}
