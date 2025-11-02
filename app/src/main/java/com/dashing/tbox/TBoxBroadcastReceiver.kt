package com.dashing.tbox

import android.os.Build
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Date

class TBoxBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TBoxBroadcastReceiver"
        const val MAIN_ACTION = "com.dashing.tbox.MAIN_ACTION"
        const val GET_STATE = "com.dashing.tbox.GET_STATE"
        const val ACTION_ERROR_RESPONSE = "com.dashing.tbox.ERROR_RESPONSE"
        const val SENDER ="sender"
        const val EXTRA_NAME_2 ="extraName_2"
        const val EXTRA_VALUE_2 ="extraValue_2"
        const val RETURN_EXTRA_NAME_1 = "returnExtraName_1"
        const val RETURN_EXTRA_VALUE_1 = "returnExtraValue_1"
        const val EXTRA_NAME_2_ERROR = "error"
        const val UNKNOWN_EXTRA_NAME_2 = "unknown_extraName_2"
        const val UNKNOWN_EXTRA_VALUE_2 = "unknown_extraValue_2"
        const val INTERNAL_ERROR = "internal_error"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            MAIN_ACTION -> {
                val sender = intent.getStringArrayExtra(SENDER) ?: return
                val extraName2 = intent.getStringExtra(EXTRA_NAME_2) ?: ""
                val extraValue2 = intent.getStringExtra(EXTRA_VALUE_2) ?: ""
                mainAction(context, sender, extraName2, extraValue2)
            }
            GET_STATE -> {
                val sender = intent.getStringArrayExtra(SENDER) ?: return
                val extraName2 = intent.getStringExtra(EXTRA_NAME_2) ?: ""
                val extraValue2 = intent.getStringExtra(EXTRA_VALUE_2) ?: ""
                getState(context, sender, extraName2, extraValue2)
            }
        }
    }

    private fun mainAction(context: Context, sender: Array<String>, extraName2: String, extraValue2: String) {
        when (extraName2) {
            //"active" -> handleActiveCommand(context, sender)
            "tbox" -> handleTBoxAction(context, sender, extraValue2)
            else -> {
                Log.w(TAG, "Unknown main action command: $extraName2")
                sendResponse(context, sender, EXTRA_NAME_2_ERROR,
                    UNKNOWN_EXTRA_NAME_2)
            }
        }
    }

    private fun handleTBoxAction(context: Context, sender: Array<String>, extraValue2: String) {
        when (extraValue2) {
            "reboot" -> rebootTBox(context, sender)
            else -> {
                Log.w(TAG, "Unknown main action value: $extraValue2")
                sendResponse(context, sender, EXTRA_NAME_2_ERROR,
                    UNKNOWN_EXTRA_VALUE_2)
            }
        }
    }

    private fun getState(context: Context, sender: Array<String>, extraName2: String, extraValue2: String) {
        when (extraName2) {
            "netState" -> handleNetStateCommand(context, sender, extraValue2)
            "status" -> handleStatusCommand(context, sender, extraValue2)
            "apnStatus" -> sendResponse(context, sender, "<default>",
                TboxRepository.apnStatus.value)
            "tboxConnected" -> sendResponse(context, sender, "<default>",
                TboxRepository.tboxConnected.value)
            "preventRestartSend" -> sendResponse(context, sender, "<default>",
                TboxRepository.preventRestartSend.value)
            "suspendTboxAppSend" -> sendResponse(context, sender, "<default>",
                TboxRepository.suspendTboxAppSend.value)
            "startTime" -> handleStartTime(context, sender, extraValue2)
            else -> {
                Log.w(TAG, "Unknown main action command: $extraName2")
                sendResponse(context, sender, EXTRA_NAME_2_ERROR,
                    UNKNOWN_EXTRA_NAME_2)
            }
        }
    }

    private fun handleNetStateCommand(context: Context, sender: Array<String>, extraValue2: String) {
        when (extraValue2) {
            "level" -> sendResponse(context, sender, extraValue2,
                TboxRepository.netState.value.signalLevel.toString())
            "status" -> sendResponse(context, sender, extraValue2,
                TboxRepository.netState.value.netStatus)
            else -> {
                Log.w(TAG, "Unknown get Net state action value: $extraValue2")
                sendResponse(context, sender, EXTRA_NAME_2_ERROR,
                    UNKNOWN_EXTRA_VALUE_2)
            }
        }
    }

    private fun handleStatusCommand(context: Context, sender: Array<String>, extraValue2: String) {
        when (extraValue2) {
            "region" -> sendResponse(context, sender, extraValue2,
                TboxRepository.netState.value.regStatus)
            "sim" -> sendResponse(context, sender, extraValue2,
                TboxRepository.netState.value.simStatus)
            else -> {
                Log.w(TAG, "Unknown get Status action value: $extraValue2")
                sendResponse(context, sender, EXTRA_NAME_2_ERROR,
                    UNKNOWN_EXTRA_VALUE_2)
            }
        }
    }

    private fun handleStartTime(context: Context, sender: Array<String>, extraValue2: String) {
        when (extraValue2) {
            "tbox" -> sendResponse(context, sender, extraValue2,
                TboxRepository.tboxConnectionTime.value)
            "service" -> sendResponse(context, sender, extraValue2,
                TboxRepository.serviceStartTime.value)
            else -> {
                Log.w(TAG, "Unknown get Status action value: $extraValue2")
                sendResponse(context, sender, EXTRA_NAME_2_ERROR,
                    UNKNOWN_EXTRA_VALUE_2)
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

    private fun rebootTBox(context: Context, sender: Array<String>) {
        val intent = Intent(context, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_TBOX_REBOOT
        }
        if (startServiceSafely(context, intent)) {
            sendResponse(context, sender, "tbox", true)
        } else {
            sendResponse(context, sender, "tbox", false)
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

    private fun sendResponse(
        context: Context,
        sender: Array<String>,
        returnExtraName1: String,
        returnExtraValue1: Any,
        )
    {
        try {
            val errorIntent = Intent(ACTION_ERROR_RESPONSE).apply {
                setPackage(sender.getOrNull(0) ?: return)
                putExtra("sender", arrayOf(
                    "com.dashing.tbox",
                    sender.getOrNull(1) ?: return
                ))
                putExtra(RETURN_EXTRA_NAME_1, returnExtraName1)
                when (returnExtraValue1) {
                    is String -> putExtra(RETURN_EXTRA_VALUE_1, returnExtraValue1)
                    is Boolean -> putExtra(RETURN_EXTRA_VALUE_1, returnExtraValue1)
                    is Date -> putExtra(RETURN_EXTRA_VALUE_1, returnExtraValue1.time)
                    else -> putExtra(EXTRA_NAME_2_ERROR, INTERNAL_ERROR)
                }
            }

            context.sendBroadcast(errorIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send response", e)
        }
    }
}