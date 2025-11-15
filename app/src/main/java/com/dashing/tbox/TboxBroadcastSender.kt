package com.dashing.tbox

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.Date

class TboxBroadcastSender(
    private val context: Context,
    private val scope: CoroutineScope
) {

    private val subscribers = mutableSetOf<Array<String>>()

    companion object {
        private const val TAG = "TboxBroadcastSender"
        private const val GET_STATE = "com.dashing.tbox.get_state"
        private const val RETURN_EXTRA_NAME_1 = "returnextraname_1"
        private const val RETURN_EXTRA_VALUE_1 = "returnextravalue_1"

        fun sendResponse(
            context: Context,
            sender: String,
            action: String,
            returnExtraName1: String,
            returnExtraValue1: Any,
        ) {
            try {
                val responseIntent = Intent(action).apply {
                    setPackage(sender)
                    putExtra(RETURN_EXTRA_NAME_1, returnExtraName1)
                    when (returnExtraValue1) {
                        is Int -> putExtra(RETURN_EXTRA_VALUE_1, returnExtraValue1)
                        is String -> putExtra(RETURN_EXTRA_VALUE_1, returnExtraValue1)
                        is Boolean -> putExtra(RETURN_EXTRA_VALUE_1, returnExtraValue1)
                        is Date -> putExtra(RETURN_EXTRA_VALUE_1, returnExtraValue1.time)
                    }
                }

                context.sendBroadcast(responseIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send response", e)
            }
        }
    }

    private var generalStateBroadcastJob: Job? = null

    // Добавить подписчика
    fun addSubscriber(packageName: String, extraName: String, extraValue: String) {
        if (isSubscriber(packageName, extraName, extraValue)) return
        subscribers.add(arrayOf(packageName, extraName, extraValue))
        Log.d(TAG, "Subscriber added: $packageName. Total subscribers: ${subscribers.size}")

        // Автоматически запускаем слушатель, если это первый подписчик
        if (subscribers.size == 1 && generalStateBroadcastJob?.isActive != true) {
            startStateBroadcastListener()
        }
    }

    // Удалить подписчика
    fun deleteSubscriber(packageName: String, extraName: String, extraValue: String) {
        if (!isSubscriber(packageName, extraName, extraValue)) return
        subscribers.remove(arrayOf(packageName, extraName, extraValue))
        Log.d(TAG, "Subscriber removed: $packageName. Total subscribers: ${subscribers.size}")

        // Останавливаем слушатель, если не осталось подписчиков
        if (subscribers.isEmpty()) {
            stopStateBroadcastListener()
        }
    }

    // Очистить всех подписчиков
    fun clearSubscribers() {
        subscribers.clear()
        stopStateBroadcastListener()
        Log.d(TAG, "All subscribers cleared")
    }

    @OptIn(FlowPreview::class)
    private fun startStateBroadcastListener() {
        if (generalStateBroadcastJob?.isActive == true) {
            Log.d(TAG, "State broadcast listener is already running")
            return
        }

        generalStateBroadcastJob = scope.launch {
            TboxRepository.netState
                .debounce(100) // Задержка 100ms чтобы избежать спама
                .collect { netState ->
                    if (subscribers.isNotEmpty()) {
                        subscribers.forEach { subscriber ->
                            sendNetState(netState, subscriber)
                        }
                    }
                }
        }
        Log.d(TAG, "State broadcast listener started")
    }

    private fun sendNetState(netState: NetState, subscriber: Array<String>) {
        try {
            if (subscriber[1] == "netstate") {
                if (subscriber[2] == "net_level") {
                    sendResponse(
                        context,
                        subscriber[0],
                        GET_STATE,
                        subscriber[2],
                        netState.signalLevel
                    )
                } else if (subscriber[2] == "net_status") {
                    sendResponse(
                        context,
                        subscriber[0],
                        GET_STATE,
                        subscriber[2],
                        netState.netStatus
                    )
                }
            } else if (subscriber[1] == "status") {
                if (subscriber[2] == "net_reg") {
                    sendResponse(
                        context,
                        subscriber[0],
                        GET_STATE,
                        subscriber[2],
                        netState.regStatus
                    )
                } else if (subscriber[2] == "net_sim") {
                    sendResponse(
                        context,
                        subscriber[0],
                        GET_STATE,
                        subscriber[2],
                        netState.simStatus
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send response", e)
        }
    }

    fun stopStateBroadcastListener() {
        generalStateBroadcastJob?.cancel()
        generalStateBroadcastJob = null
        Log.d(TAG, "State broadcast listener stopped")
    }

    // Проверить, является ли пакет подписчиком
    fun isSubscriber(packageName: String, extraName: String, extraValue: String): Boolean {
        return subscribers.contains(arrayOf(packageName, extraName, extraValue))
    }
}