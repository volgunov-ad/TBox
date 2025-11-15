package com.dashing.tbox

import android.os.Build
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class TBoxBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TBoxBroadcastReceiver"
        const val MAIN_ACTION = "com.dashing.tbox.main_action"
        const val GET_STATE = "com.dashing.tbox.get_state"
        const val SUBSCRIBE = "com.dashing.tbox.subscribe"
        const val SENDER = "sender"
        const val EXTRA_NAME_1 = "extraname_1"
        const val EXTRA_NAME_2 = "extraname_2"
        const val EXTRA_VALUE_1 = "extravalue_1"
        const val EXTRA_VALUE_2 = "extravalue_2"
    }

    private lateinit var broadcastSender: TboxBroadcastSender

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action?.lowercase()) {
            MAIN_ACTION -> {
                if (intent.getStringExtra(EXTRA_NAME_1)?.lowercase() != SENDER) return
                val sender = intent.getStringArrayExtra(EXTRA_VALUE_1)?.getOrNull(0) ?: return
                val extraName2 = intent.getStringExtra(EXTRA_NAME_2)?.lowercase() ?: ""
                val extraValue2 = intent.getStringExtra(EXTRA_VALUE_2)?.lowercase() ?: ""
                mainAction(context, sender, extraName2, extraValue2)
            }
            GET_STATE -> {
                if (intent.getStringExtra(EXTRA_NAME_1)?.lowercase() != SENDER) return
                val sender = intent.getStringArrayExtra(EXTRA_VALUE_1)?.getOrNull(0) ?: return
                val extraName2 = intent.getStringExtra(EXTRA_NAME_2)?.lowercase() ?: ""
                val extraValue2 = intent.getStringExtra(EXTRA_VALUE_2)?.lowercase() ?: ""
                getState(context, sender, extraName2, extraValue2)
            }
            SUBSCRIBE -> {
                if (intent.getStringExtra(EXTRA_NAME_1)?.lowercase() != SENDER) return
                val sender = intent.getStringArrayExtra(EXTRA_VALUE_1)?.getOrNull(0) ?: return
                val extraName2 = intent.getStringExtra(EXTRA_NAME_2)?.lowercase() ?: ""
                val extraValue2 = intent.getStringExtra(EXTRA_VALUE_2)?.lowercase() ?: ""
                broadcastSender.addSubscriber(sender, extraName2, extraValue2)
            }
        }
    }

    private fun mainAction(context: Context, sender: String, extraName2: String, extraValue2: String) {
        when (extraName2) {
            //"active" -> handleActiveCommand(context, sender)
            "tbox" -> handleTBoxAction(context, sender, extraValue2)
            else -> {
                Log.w(TAG, "Unknown main action command: $extraName2")
            }
        }
    }

    private fun handleTBoxAction(context: Context, sender: String, extraValue2: String) {
        when (extraValue2) {
            "reboot" -> rebootTBox(context, sender)
            else -> {
                Log.w(TAG, "Unknown main action value: $extraValue2")
            }
        }
    }

    private fun getState(context: Context, sender: String, extraName2: String, extraValue2: String) {
        when (extraName2) {
            "netstate" -> handleNetStateCommand(context, sender, extraValue2)
            "status" -> handleStatusCommand(context, sender, extraValue2)
            "apnstatus" ->
                TboxBroadcastSender.sendResponse(context, sender, GET_STATE,
                    "apnStatus",
                    TboxRepository.apnStatus.value)
            "tboxconnected" ->
                TboxBroadcastSender.sendResponse(context, sender, GET_STATE,
                    "tboxConnected",
                    TboxRepository.tboxConnected.value)
            "preventrestartsend" ->
                TboxBroadcastSender.sendResponse(context, sender, GET_STATE,
                    "preventRestartSend",
                    TboxRepository.preventRestartSend.value)
            "suspendtboxappsend" ->
                TboxBroadcastSender.sendResponse(context, sender, GET_STATE,
                    "suspendTboxAppSend",
                    TboxRepository.suspendTboxAppSend.value)
            "starttime" -> handleStartTime(context, sender, extraValue2)
            else -> {
                Log.w(TAG, "Unknown main action command: $extraName2")
            }
        }
    }

    private fun handleNetStateCommand(context: Context, sender: String, extraValue2: String) {
        when (extraValue2) {
            "level" ->
                TboxBroadcastSender.sendResponse(context, sender, GET_STATE,
                    "net_level",
                    TboxRepository.netState.value.signalLevel)
            "status" ->
                TboxBroadcastSender.sendResponse(context, sender, GET_STATE,
                    "net_status",
                    TboxRepository.netState.value.netStatus)
            else -> {
                Log.w(TAG, "Unknown get Net state action value: $extraValue2")
            }
        }
    }

    private fun handleStatusCommand(context: Context, sender: String, extraValue2: String) {
        when (extraValue2) {
            "region" ->
                TboxBroadcastSender.sendResponse(context, sender, GET_STATE,
                    "net_region",
                    TboxRepository.netState.value.regStatus)
            "sim" ->
                TboxBroadcastSender.sendResponse(context, sender, GET_STATE,
                    "net_sim",
                    TboxRepository.netState.value.simStatus)
            else -> {
                Log.w(TAG, "Unknown get Status action value: $extraValue2")
            }
        }
    }

    private fun handleStartTime(context: Context, sender: String, extraValue2: String) {
        when (extraValue2) {
            "tbox" ->
                TboxBroadcastSender.sendResponse(context, sender, GET_STATE,
                    "tbox_startTime",
                    TboxRepository.tboxConnectionTime.value)
            "service" ->
                TboxBroadcastSender.sendResponse(context, sender, GET_STATE,
                    "service_startTime",
                    TboxRepository.serviceStartTime.value)
            else -> {
                Log.w(TAG, "Unknown get Status action value: $extraValue2")
            }
        }
    }

    private fun startService(context: Context) {
        val intent = Intent(context, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_START
        }
        startServiceSafely(context, intent)
    }

    private fun stopService(context: Context) {
        val intent = Intent(context, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_STOP
        }
        context.stopService(intent)
    }

    private fun rebootTBox(context: Context, sender: String) {
        val intent = Intent(context, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_TBOX_REBOOT
        }
        if (startServiceSafely(context, intent)) {
            TboxBroadcastSender.sendResponse(context, sender, MAIN_ACTION, "tbox_reboot", true)
        } else {
            TboxBroadcastSender.sendResponse(context, sender, MAIN_ACTION, "tbox_reboot", false)
        }
    }

    private fun startServiceSafely(context: Context, intent: Intent): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }
}