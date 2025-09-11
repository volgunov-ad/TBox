package com.dashing.tbox

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

object WidgetUtils {
    fun isWidgetActive(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, NetWidget::class.java))
        return ids.isNotEmpty()
    }
}
