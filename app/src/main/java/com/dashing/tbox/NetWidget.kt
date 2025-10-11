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
        // Кэш для PendingIntent чтобы не создавать его каждый раз
        private var cachedPendingIntent: android.app.PendingIntent? = null
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateWidget(context, manager, ids)

        //context.startService(Intent(context, BackgroundService::class.java).apply {
        //    action = BackgroundService.ACTION_NET_UPD_START
        //})
        val intent = Intent(context, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_START
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        // Запускаем проверку таймаута
        startTimeoutCheck(context)
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
            R.drawable.ic_signal_nosig_sharp_outlined_dark
        if (tboxStatus) {
            val signalLevel = csq.getSignalLevel()
            if (apnStatus == "подключен") {
                when (netType) {
                    "2G" -> {
                        when (signalLevel) {
                            1 -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_e_cellular_1_sharp_outlined else
                                    R.drawable.ic_signal_e_cellular_1_sharp_outlined_dark
                            }
                            2 -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_e_cellular_2_sharp_outlined else
                                    R.drawable.ic_signal_e_cellular_2_sharp_outlined_dark
                            }
                            3 -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_e_cellular_3_sharp_outlined else
                                    R.drawable.ic_signal_e_cellular_3_sharp_outlined_dark
                            }
                            4 -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_e_cellular_4_sharp_outlined else
                                    R.drawable.ic_signal_e_cellular_4_sharp_outlined_dark
                            }
                            else -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_e_cellular_0_sharp_outlined else
                                    R.drawable.ic_signal_e_cellular_0_sharp_outlined_dark
                            }
                        }
                    }
                    "3G" -> {
                        when (signalLevel) {
                            1 -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_3g_cellular_1_sharp_outlined else
                                    R.drawable.ic_signal_3g_cellular_1_sharp_outlined_dark
                            }
                            2 -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_3g_cellular_2_sharp_outlined else
                                    R.drawable.ic_signal_3g_cellular_2_sharp_outlined_dark
                            }
                            3 -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_3g_cellular_3_sharp_outlined else
                                    R.drawable.ic_signal_3g_cellular_3_sharp_outlined_dark
                            }
                            4 -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_3g_cellular_4_sharp_outlined else
                                    R.drawable.ic_signal_3g_cellular_4_sharp_outlined_dark
                            }
                            else -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_3g_cellular_0_sharp_outlined else
                                    R.drawable.ic_signal_3g_cellular_0_sharp_outlined_dark
                            }
                        }
                    }
                    "4G" -> {
                        when (signalLevel) {
                            1 -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_4g_cellular_1_sharp_outlined else
                                    R.drawable.ic_signal_4g_cellular_1_sharp_outlined_dark
                            }
                            2 -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_4g_cellular_2_sharp_outlined else
                                    R.drawable.ic_signal_4g_cellular_2_sharp_outlined_dark
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
                                    R.drawable.ic_signal_4g_cellular_0_sharp_outlined_dark
                            }
                        }
                    }
                    else -> {
                        when (signalLevel) {
                            1 -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_cellular_1_sharp_outlined else
                                    R.drawable.ic_signal_cellular_1_sharp_outlined_dark
                            }
                            2 -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_cellular_2_sharp_outlined else
                                    R.drawable.ic_signal_cellular_2_sharp_outlined_dark
                            }
                            3 -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_cellular_3_sharp_outlined else
                                    R.drawable.ic_signal_cellular_3_sharp_outlined_dark
                            }
                            4 -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_cellular_4_sharp_outlined else
                                    R.drawable.ic_signal_cellular_4_sharp_outlined_dark
                            }
                            else -> {
                                imageR = if (theme == 2) R.drawable.ic_signal_cellular_0_sharp_outlined else
                                    R.drawable.ic_signal_cellular_0_sharp_outlined_dark
                            }
                        }
                    }
                }
            }
            else {
                when (signalLevel) {
                    1 -> {
                        imageR = if (theme == 2) R.drawable.ic_signal_cellular_1_sharp_outlined else
                            R.drawable.ic_signal_cellular_1_sharp_outlined_dark
                    }
                    2 -> {
                        imageR = if (theme == 2) R.drawable.ic_signal_cellular_2_sharp_outlined else
                            R.drawable.ic_signal_cellular_2_sharp_outlined_dark
                    }
                    3 -> {
                        imageR = if (theme == 2) R.drawable.ic_signal_cellular_3_sharp_outlined else
                            R.drawable.ic_signal_cellular_3_sharp_outlined_dark
                    }
                    4 -> {
                        imageR = if (theme == 2) R.drawable.ic_signal_cellular_4_sharp_outlined else
                            R.drawable.ic_signal_cellular_4_sharp_outlined_dark
                    }
                    else -> {
                        imageR = if (theme == 2) R.drawable.ic_signal_cellular_0_sharp_outlined else
                            R.drawable.ic_signal_cellular_0_sharp_outlined_dark
                    }
                }
            }
        }
        val pendingIntent = getPendingIntent(context)
        appWidgetIds.forEach { appWidgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_net).apply {
                setImageViewResource(R.id.widget_image, imageR)
            }
            // Создаем PendingIntent для открытия MainActivity
            views.setOnClickPendingIntent(R.id.widget_image, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun getPendingIntent(context: Context): android.app.PendingIntent {
        if (cachedPendingIntent == null) {
            cachedPendingIntent = Intent(context, MainActivity::class.java).let { intent ->
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                android.app.PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
            }
        }
        return cachedPendingIntent!!
    }

    private fun Int.getSignalLevel(): Int {
        return when (this) {
            in 1..10 -> 1
            in 11..16 -> 2
            in 17..24 -> 3
            in 25..32 -> 4
            else -> 0
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        /*if (!WidgetUtils.isWidgetActive(context)) {
            context.stopService(Intent(context, BackgroundService::class.java).apply {
                action = BackgroundService.ACTION_STOP
            })
        }*/
        // Останавливаем проверку таймаута
        timeoutRunnable?.let { handler.removeCallbacks(it) }
    }
}
