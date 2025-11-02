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
import java.util.Date

class ConResWidget : AppWidgetProvider() {

    companion object {
        private const val TIMEOUT_MS = 30000L // 30 секунд
        private var lastUpdateTime: Long = 0
        private val handler = Handler(Looper.getMainLooper())
        private var timeoutRunnable: Runnable? = null
        private var restartBlock = Date()
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
                        isNoData = false
                    )
                }

                // Перезапускаем проверку таймаута
                restartTimeoutCheck(context)
            }

            BackgroundService.ACTION_TBOX_REBOOT -> {
                restartBlock = Date()
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
        isNoData: Boolean = false,
    ) {
        // Определяем цвет текста
        val color = when {
            isNoData -> ContextCompat.getColor(context, R.color.status_err)
            !tboxStatus -> ContextCompat.getColor(context, R.color.status_warn)
            else -> ContextCompat.getColor(context, R.color.status_ok)
        }

        val resColor = when {
            theme == 2 -> ContextCompat.getColor(context, R.color.on_dark_background)
            else -> ContextCompat.getColor(context, R.color.on_light_background)
        }

        val views = RemoteViews(context.packageName, R.layout.widget_con_res).apply {
            // Устанавливаем цвет текста TextView
            setTextColor(R.id.tbox_text, color)
            setTextColor(R.id.res_text, resColor)

            // Устанавливаем клики
            setOnClickPendingIntent(R.id.tbox_text, getMainPendingIntent(context))

            // Условие для клика на RES (перезагрузка)
            if (tboxStatus && !isNoData && Date().time - restartBlock.time > 15000) {
                setOnClickPendingIntent(R.id.res_text, getRebootPendingIntent(context))
            } else {
                // Если перезагрузка заблокирована, убираем клик или ставим основной
                setOnClickPendingIntent(R.id.res_text, null)
            }
        }

        // Настраиваем размер текста
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val textSize = calculateTextSize(minWidth)

        // Устанавливаем размер текста для обоих TextView
        views.setTextViewTextSize(R.id.res_text, TypedValue.COMPLEX_UNIT_SP, textSize)
        views.setTextViewTextSize(R.id.tbox_text, TypedValue.COMPLEX_UNIT_SP, textSize)

        // Обновляем виджет
        appWidgetManager.updateAppWidget(appWidgetId, views)
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
            minWidth < 120 -> 14f
            minWidth < 160 -> 20f
            minWidth < 200 -> 30f
            minWidth < 250 -> 40f
            else -> 50f
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Останавливаем проверку таймаута
        timeoutRunnable?.let { handler.removeCallbacks(it) }
    }
}