package com.dashing.tbox

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews

class NetWidget : AppWidgetProvider() {
    companion object {
        private const val TIMEOUT_MS = 30000L // 30 секунд
        private var lastUpdateTime: Long = 0
        private val handler = Handler(Looper.getMainLooper())
        private var timeoutRunnable: Runnable? = null
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateWidget(context, manager, ids)

        //context.startService(Intent(context, BackgroundService::class.java).apply {
        //    action = BackgroundService.ACTION_NET_UPD_START
        //})
        val serviceIntent = Intent(context, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_NET_UPD_START
        }

        // Для Android 8+ используйте startForegroundService
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == BackgroundService.ACTION_UPDATE_WIDGET) {
            val theme = intent.getIntExtra(BackgroundService.EXTRA_THEME, 1)
            val csq = intent.getIntExtra(BackgroundService.EXTRA_CSQ, 99)
            val netType = intent.getStringExtra(BackgroundService.EXTRA_NET_TYPE) ?: ""
            val apnStatus = intent.getStringExtra(BackgroundService.EXTRA_APN_STATUS) ?: ""
            val tboxStatus = intent.getBooleanExtra(BackgroundService.EXTRA_TBOX_STATUS, false)

            // Обновляем все экземпляры виджета
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, NetWidget::class.java)
            )

            updateWidget(context, appWidgetManager, appWidgetIds, theme, csq, netType, apnStatus, tboxStatus)

            // Перезапускаем проверку таймаута
            restartTimeoutCheck(context)
        }
    }

    private fun startTimeoutCheck(context: Context) {
        lastUpdateTime = System.currentTimeMillis()
        restartTimeoutCheck(context)
    }

    private fun restartTimeoutCheck(context: Context) {
        // Удаляем предыдущую проверку
        timeoutRunnable?.let { handler.removeCallbacks(it) }

        timeoutRunnable = Runnable {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUpdateTime >= TIMEOUT_MS) {
                // Прошло более 30 секунд без обновлений - показываем отсутствие сигнала
                showNoSignal(context)
            } else {
                // Планируем следующую проверку
                val timeSinceLastUpdate = currentTime - lastUpdateTime
                val delay = TIMEOUT_MS - timeSinceLastUpdate
                if (delay > 0) {
                    handler.postDelayed(timeoutRunnable!!, delay)
                }
            }
        }

        handler.postDelayed(timeoutRunnable!!, TIMEOUT_MS)
    }

    private fun showNoSignal(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, NetWidget::class.java)
        )

        // Используем стандартную тему (1) для отображения отсутствия сигнала
        updateWidget(context, appWidgetManager, appWidgetIds, theme = 1, csq = 99,
            netType = "", apnStatus = "", tboxStatus = false)
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        theme: Int = 1,
        csq: Int = 99,
        netType: String = "",
        apnStatus: String = "",
        tboxStatus: Boolean = false
    ) {
        var imageR = if (theme == 1) R.drawable.ic_signal_nosig_sharp_outlined else
            R.drawable.ic_signal_nosig_sharp_outlined
        if (tboxStatus) {
            val signalLevel = csq.getSignalLevel()
            if (apnStatus == "подключен") {
                when (netType) {
                    "2G" -> {
                        when (signalLevel) {
                            1 -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_e_cellular_1_sharp_outlined else
                                    R.drawable.ic_signal_e_cellular_1_sharp_outlined
                            }
                            2 -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_e_cellular_2_sharp_outlined else
                                    R.drawable.ic_signal_e_cellular_2_sharp_outlined
                            }
                            3 -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_e_cellular_3_sharp_outlined else
                                    R.drawable.ic_signal_e_cellular_3_sharp_outlined
                            }
                            4 -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_e_cellular_4_sharp_outlined else
                                    R.drawable.ic_signal_e_cellular_4_sharp_outlined
                            }
                            else -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_e_cellular_0_sharp_outlined else
                                    R.drawable.ic_signal_e_cellular_0_sharp_outlined
                            }
                        }
                    }
                    "3G" -> {
                        when (signalLevel) {
                            1 -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_3g_cellular_1_sharp_outlined else
                                    R.drawable.ic_signal_3g_cellular_1_sharp_outlined
                            }
                            2 -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_3g_cellular_2_sharp_outlined else
                                    R.drawable.ic_signal_3g_cellular_2_sharp_outlined
                            }
                            3 -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_3g_cellular_3_sharp_outlined else
                                    R.drawable.ic_signal_3g_cellular_3_sharp_outlined
                            }
                            4 -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_3g_cellular_4_sharp_outlined else
                                    R.drawable.ic_signal_3g_cellular_4_sharp_outlined
                            }
                            else -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_3g_cellular_0_sharp_outlined else
                                    R.drawable.ic_signal_3g_cellular_0_sharp_outlined
                            }
                        }
                    }
                    "4G" -> {
                        when (signalLevel) {
                            1 -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_4g_cellular_1_sharp_outlined else
                                    R.drawable.ic_signal_4g_cellular_1_sharp_outlined
                            }
                            2 -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_4g_cellular_2_sharp_outlined else
                                    R.drawable.ic_signal_4g_cellular_2_sharp_outlined
                            }
                            3 -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_4g_cellular_3_sharp_outlined else
                                    R.drawable.ic_signal_4g_cellular_3_sharp_outlined_dark
                            }
                            4 -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_4g_cellular_4_sharp_outlined else
                                    R.drawable.ic_signal_4g_cellular_4_sharp_outlined_dark
                            }
                            else -> {
                                imageR = if (theme == 1) R.drawable.ic_signal_4g_cellular_0_sharp_outlined else
                                    R.drawable.ic_signal_4g_cellular_0_sharp_outlined
                            }
                        }
                    }
                    else -> {
                        when (signalLevel) {
                            1 -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_cellular_1_sharp_outlined else
                                    R.drawable.ic_signal_cellular_1_sharp_outlined
                            }
                            2 -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_cellular_2_sharp_outlined else
                                    R.drawable.ic_signal_cellular_2_sharp_outlined
                            }
                            3 -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_cellular_3_sharp_outlined else
                                    R.drawable.ic_signal_cellular_3_sharp_outlined
                            }
                            4 -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_cellular_4_sharp_outlined else
                                    R.drawable.ic_signal_cellular_4_sharp_outlined
                            }
                            else -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_cellular_0_sharp_outlined else
                                    R.drawable.ic_signal_cellular_0_sharp_outlined
                            }
                        }
                    }
                }
            }
            else {
                when (signalLevel) {
                    1 -> {
                        imageR = if (theme == 2) R.drawable.ic_signal_cellular_1_sharp_outlined else
                            R.drawable.ic_signal_cellular_1_sharp_outlined
                    }
                    2 -> {
                        imageR = if (theme == 2) R.drawable.ic_signal_cellular_2_sharp_outlined else
                            R.drawable.ic_signal_cellular_2_sharp_outlined
                    }
                    3 -> {
                        imageR = if (theme == 2) R.drawable.ic_signal_cellular_3_sharp_outlined else
                            R.drawable.ic_signal_cellular_3_sharp_outlined
                    }
                    4 -> {
                        imageR = if (theme == 2) R.drawable.ic_signal_cellular_4_sharp_outlined else
                            R.drawable.ic_signal_cellular_4_sharp_outlined
                    }
                    else -> {
                        imageR = if (theme == 2) R.drawable.ic_signal_cellular_0_sharp_outlined else
                            R.drawable.ic_signal_cellular_0_sharp_outlined
                    }
                }
            }
        }

        appWidgetIds.forEach { appWidgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_net).apply {
                setImageViewResource(R.id.widget_image, imageR)
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun Int.getSignalLevel(): Int {
        return when (this) {
            in 1..6 -> 1
            in 7..13 -> 2
            in 14..20 -> 3
            in 21..32 -> 4
            else -> 0
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        if (!WidgetUtils.isWidgetActive(context)) {
            context.stopService(Intent(context, BackgroundService::class.java).apply {
                action = BackgroundService.ACTION_NET_UPD_STOP
            })
        }
        // Останавливаем проверку таймаута
        timeoutRunnable?.let { handler.removeCallbacks(it) }
    }
}
