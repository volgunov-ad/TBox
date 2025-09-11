package com.dashing.tbox

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.widget.RemoteViews

class NetWidget : AppWidgetProvider() {
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
            val csq = intent.getIntExtra(BackgroundService.EXTRA_CSQ, 99)
            val netType = intent.getStringExtra(BackgroundService.EXTRA_NET_TYPE) ?: ""
            val apnStatus = intent.getStringExtra(BackgroundService.EXTRA_APN_STATUS) ?: ""
            val tboxStatus = intent.getBooleanExtra(BackgroundService.EXTRA_TBOX_STATUS, false)

            // Обновляем все экземпляры виджета
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, NetWidget::class.java)
            )

            updateWidget(context, appWidgetManager, appWidgetIds, csq, netType, apnStatus, tboxStatus)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        csq: Int = 99,
        netType: String = "",
        apnStatus: String = "",
        tboxStatus: Boolean = false
    ) {
        var imageR = R.drawable.ic_signal_nosig_sharp_outlined
        if (tboxStatus) {
            if (apnStatus == "подключен") {
                when (netType) {
                    "2G" -> {
                        when (csq) {
                            in 1..6 -> {
                                imageR = R.drawable.ic_signal_e_cellular_1_sharp_outlined
                            }
                            in 7..13 -> {
                                imageR = R.drawable.ic_signal_e_cellular_2_sharp_outlined
                            }
                            in 14..20 -> {
                                imageR = R.drawable.ic_signal_e_cellular_3_sharp_outlined
                            }
                            in 21..32 -> {
                                imageR = R.drawable.ic_signal_e_cellular_4_sharp_outlined
                            }
                            else -> {
                                imageR = R.drawable.ic_signal_e_cellular_0_sharp_outlined
                            }
                        }
                    }
                    "3G" -> {
                        when (csq) {
                            in 1..6 -> {
                                imageR = R.drawable.ic_signal_3g_cellular_1_sharp_outlined
                            }
                            in 7..13 -> {
                                imageR = R.drawable.ic_signal_3g_cellular_2_sharp_outlined
                            }
                            in 14..20 -> {
                                imageR = R.drawable.ic_signal_3g_cellular_3_sharp_outlined
                            }
                            in 21..32 -> {
                                imageR = R.drawable.ic_signal_3g_cellular_4_sharp_outlined
                            }
                            else -> {
                                imageR = R.drawable.ic_signal_3g_cellular_0_sharp_outlined
                            }
                        }
                    }
                    "4G" -> {
                        when (csq) {
                            in 1..6 -> {
                                imageR = R.drawable.ic_signal_4g_cellular_1_sharp_outlined
                            }
                            in 7..13 -> {
                                imageR = R.drawable.ic_signal_4g_cellular_2_sharp_outlined
                            }
                            in 14..20 -> {
                                imageR = R.drawable.ic_signal_4g_cellular_3_sharp_outlined
                            }
                            in 21..32 -> {
                                imageR = R.drawable.ic_signal_4g_cellular_4_sharp_outlined
                            }
                            else -> {
                                imageR = R.drawable.ic_signal_4g_cellular_0_sharp_outlined
                            }
                        }
                    }
                    else -> {
                        when (csq) {
                            in 1..6 -> {
                                imageR = R.drawable.ic_signal_cellular_1_sharp_outlined
                            }
                            in 7..13 -> {
                                imageR = R.drawable.ic_signal_cellular_2_sharp_outlined
                            }
                            in 14..20 -> {
                                imageR = R.drawable.ic_signal_cellular_3_sharp_outlined
                            }
                            in 21..32 -> {
                                imageR = R.drawable.ic_signal_cellular_4_sharp_outlined
                            }
                            else -> {
                                imageR = R.drawable.ic_signal_cellular_0_sharp_outlined
                            }
                        }
                    }
                }
            }
            else {
                when (csq) {
                    in 1..6 -> {
                        imageR = R.drawable.ic_signal_cellular_1_sharp_outlined
                    }
                    in 7..13 -> {
                        imageR = R.drawable.ic_signal_cellular_2_sharp_outlined
                    }
                    in 14..20 -> {
                        imageR = R.drawable.ic_signal_cellular_3_sharp_outlined
                    }
                    in 21..32 -> {
                        imageR = R.drawable.ic_signal_cellular_4_sharp_outlined
                    }
                    else -> {
                        imageR = R.drawable.ic_signal_cellular_0_sharp_outlined
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
        /*val textTboxStatus = if (tboxStatus) {
            "TBox подключен"
        } else {
            "TBox отключен"
        }
        val colorTbox = if (tboxStatus) Color.GREEN else Color.RED
        val colorAPN = if (apnStatus == "подключен") Color.GREEN else Color.RED
        val color = when (csq) {
            in 0..10 -> Color.RED
            in 11..20 -> Color.rgb(255,110,0)
            99 -> Color.RED
            else -> Color.GREEN
        }
        val csqText = if (csq == 99) {
            "-"
        }
        else {
            csq.toString()
        }
        appWidgetIds.forEach { appWidgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_net).apply {
                setTextViewText(R.id.widget_csq_value, csqText)
                setTextColor(R.id.widget_csq_value, color)
                setTextViewText(R.id.widget_net_type, netType)
                setTextViewText(R.id.widget_apn_status, apnStatus)
                setTextColor(R.id.widget_apn_status, colorAPN)
                setTextViewText(R.id.widget_connection_status, textTboxStatus)
                setTextColor(R.id.widget_connection_status, colorTbox)
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }*/
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        if (!WidgetUtils.isWidgetActive(context)) {
            context.stopService(Intent(context, BackgroundService::class.java).apply {
                action = BackgroundService.ACTION_NET_UPD_STOP
            })
        }
    }
}
