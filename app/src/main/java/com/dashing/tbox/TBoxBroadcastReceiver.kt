package com.dashing.tbox

import android.os.Build
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class TboxBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TBoxBroadcastReceiver"
        const val MAIN_ACTION = "com.dashing.tbox.main_action"
        const val GET_STATE = "com.dashing.tbox.get_state"
        const val SUBSCRIBE = "com.dashing.tbox.subscribe"
        const val UNSUBSCRIBE = "com.dashing.tbox.unsubscribe"
        const val SENDER = "sender"
        const val EXTRA_NAME_1 = "extra_name_1"
        const val EXTRA_NAME_2 = "extra_name_2"
        const val EXTRA_VALUE_1 = "extra_value_1"
        const val EXTRA_VALUE_2 = "extra_value_2"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        try {
            val backgroundService = context as? BackgroundService
            val broadcastSender = backgroundService?.broadcastSender ?: run {
                Log.e(TAG, "BackgroundService or broadcastSender not available")
                return
            }

            if (intent == null) {
                Log.e(TAG, "Intent is null")
                return
            }
            Log.d(TAG, "Get Intent: ${intent.action} Package: ${intent.`package`}")

            if (intent.getStringExtra(EXTRA_NAME_1) != SENDER) return
            val sender = intent.getStringArrayExtra(EXTRA_VALUE_1)?.getOrNull(0) ?: return
            val extraName2 = intent.getStringExtra(EXTRA_NAME_2) ?: ""
            val extraValue2 = intent.getStringExtra(EXTRA_VALUE_2) ?: ""

            Log.d(TAG, "Get Intent: ${intent.action} from $sender: $extraName2 = $extraValue2")

            when (intent.action) {
                MAIN_ACTION -> {
                    mainAction(context, sender, extraName2, extraValue2)
                }

                GET_STATE -> {
                    broadcastSender.sendCurrentValue(sender, extraName2, extraValue2)
                }

                SUBSCRIBE -> {
                    broadcastSender.addSubscriber(sender, extraName2, extraValue2)
                }

                UNSUBSCRIBE -> {
                    broadcastSender.deleteSubscriber(sender, extraName2, extraValue2)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun mainAction(context: Context, sender: String, extraName2: String, extraValue2: String) {
        when (extraName2) {
            "activity" -> handleActiveCommand(context, sender, extraValue2)
            "tbox" -> handleTBoxAction(context, sender, extraValue2)
            else -> {
                Log.w(TAG, "Unknown main action command: $extraName2")
            }
        }
    }

    private fun handleActiveCommand(context: Context, sender: String, extraValue2: String) {
        when (extraValue2) {
            "show" -> showMainActivity(context)
            else -> {
                Log.w(TAG, "Unknown main action MainActive value: $extraValue2")
            }
        }
    }

    private fun handleTBoxAction(context: Context, sender: String, extraValue2: String) {
        when (extraValue2) {
            "reboot" -> rebootTBox(context)
            else -> {
                Log.w(TAG, "Unknown main action Service value: $extraValue2")
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

    private fun showMainActivity(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // Добавляем любые дополнительные данные, если нужно
            // putExtra("key", "value")

            // Можно также установить действие, если MainActivity обрабатывает разные действия
            // action = "CUSTOM_ACTION"
        }

        context.startActivity(intent)
    }

    private fun rebootTBox(context: Context) {
        val intent = Intent(context, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_TBOX_REBOOT
        }
        startServiceSafely(context, intent)
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