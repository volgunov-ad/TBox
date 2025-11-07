package com.dashing.tbox

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.content.ContextCompat

class ConResWidget : AppWidgetProvider() {

    companion object {
        private const val TIMEOUT_MS = 30000L // 30 секунд
        private var lastUpdateTime: Long = 0
        private val handler = Handler(Looper.getMainLooper())
        private var timeoutRunnable: Runnable? = null
        private var restartBlock = System.currentTimeMillis()
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Обновляем все виджеты
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }

        // Запускаем сервис
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

        when (intent.action) {
            BackgroundService.ACTION_UPDATE_WIDGET -> {
                val theme = intent.getIntExtra(BackgroundService.EXTRA_THEME, 1)
                val tboxStatus = intent.getBooleanExtra(BackgroundService.EXTRA_TBOX_STATUS, false)
                val showLocIndicator = intent.getBooleanExtra(BackgroundService.EXTRA_WIDGET_SHOW_LOC_INDICATOR, false)
                val locSetPosition = intent.getBooleanExtra(BackgroundService.EXTRA_LOC_SET_POSITION, false)
                val isLocTruePosition = intent.getBooleanExtra(BackgroundService.EXTRA_LOC_TRUE_POSITION, false)


                // Обновляем все экземпляры виджета
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, ConResWidget::class.java)
                )
                appWidgetIds.forEach { appWidgetId ->
                    updateWidget(
                        context, appWidgetManager, appWidgetId,
                        theme,
                        tboxStatus,
                        showLocIndicator,
                        locSetPosition,
                        isLocTruePosition,
                        isNoData = false
                    )
                }

                // Перезапускаем проверку таймаута
                restartTimeoutCheck(context)
            }

            BackgroundService.ACTION_TBOX_REBOOT -> {
                restartBlock = System.currentTimeMillis()
            }
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
            ComponentName(context, ConResWidget::class.java)
        )

        appWidgetIds.forEach { appWidgetId ->
            updateWidget(
                context, appWidgetManager, appWidgetId,
                theme = 1,
                tboxStatus = false,
                isNoData = true
            )
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        theme: Int = 1,
        tboxStatus: Boolean = false,
        showLocIndicator: Boolean = false,
        locSetPosition: Boolean = false,
        isLocTruePosition: Boolean = false,
        isNoData: Boolean = false,
    ) {
        // Определяем цвет текста
        val color = when {
            isNoData -> ContextCompat.getColor(context, R.color.status_err)
            !tboxStatus -> ContextCompat.getColor(context, R.color.status_warn)
            else -> ContextCompat.getColor(context, R.color.status_ok)
        }

        val locIndicatorDrawable = when {
            !showLocIndicator -> R.drawable.loc_none
            !locSetPosition -> R.drawable.loc_err
            !isLocTruePosition -> R.drawable.loc_warn
            else -> R.drawable.loc_ok
        }

        val resColor = when {
            theme == 2 -> ContextCompat.getColor(context, R.color.on_dark_background)
            else -> ContextCompat.getColor(context, R.color.on_light_background)
        }

        // Настраиваем размер текста
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val textSize = calculateTextSize(minWidth)
        //val indicatorSize = calculateIndicatorSize(minWidth)

        val views = RemoteViews(context.packageName, R.layout.widget_con_res).apply {
            // Устанавливаем цвет текста TextView
            setTextColor(R.id.tbox_text, color)
            setTextColor(R.id.res_text, resColor)
            setImageViewResource(R.id.location_indicator, locIndicatorDrawable)

            setTextViewTextSize(R.id.res_text, TypedValue.COMPLEX_UNIT_SP, textSize)
            setTextViewTextSize(R.id.tbox_text, TypedValue.COMPLEX_UNIT_SP, textSize)

            // Устанавливаем клики
            setOnClickPendingIntent(R.id.tbox_text, getMainPendingIntent(context))

            // Условие для клика на RES (перезагрузка)
            if (tboxStatus && !isNoData && System.currentTimeMillis() - restartBlock > 15000) {
                setOnClickPendingIntent(R.id.res_text, getRebootPendingIntent(context))
            } else {
                // Если перезагрузка заблокирована, убираем клик
                setOnClickPendingIntent(R.id.res_text, null)
            }
        }

        //setImageSize(views, context, R.id.location_indicator, indicatorSize)

        // Обновляем виджет
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun setImageSize(views: RemoteViews, context: Context, viewId: Int, sizeDp: Int) {
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt()

        views.setInt(viewId, "setMaxWidth", sizePx)
        views.setInt(viewId, "setMaxHeight", sizePx)
        views.setInt(viewId, "setMinimumWidth", sizePx)
        views.setInt(viewId, "setMinimumHeight", sizePx)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    private fun getMainPendingIntent(context: Context): android.app.PendingIntent {
        return Intent(context, MainActivity::class.java).let { intent ->
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            android.app.PendingIntent.getActivity(
                context,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    private fun getRebootPendingIntent(context: Context): android.app.PendingIntent {
        return Intent(context, BackgroundService::class.java).let { intent ->
            intent.action = BackgroundService.ACTION_TBOX_REBOOT
            android.app.PendingIntent.getService(
                context,
                1,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    private fun calculateTextSize(minWidth: Int): Float {
        // Адаптивный расчет размера текста
        return when {
            minWidth < 100 -> 10f
            minWidth < 150 -> 15f
            minWidth < 200 -> 20f
            minWidth < 250 -> 25f
            minWidth < 300 -> 30f
            minWidth < 350 -> 35f
            minWidth < 400 -> 40f
            minWidth < 450 -> 45f
            minWidth < 500 -> 50f
            else -> 60f
        }
    }

    private fun calculateIndicatorSize(minWidth: Int): Int {
        // Адаптивный расчет размера индикатора
        return when {
            minWidth < 100 -> 10
            minWidth < 150 -> 15
            minWidth < 200 -> 20
            minWidth < 250 -> 25
            minWidth < 300 -> 30
            minWidth < 350 -> 35
            minWidth < 400 -> 40
            minWidth < 450 -> 45
            minWidth < 500 -> 50
            else -> 60
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Останавливаем проверку таймаута
        timeoutRunnable?.let { handler.removeCallbacks(it) }
    }
}