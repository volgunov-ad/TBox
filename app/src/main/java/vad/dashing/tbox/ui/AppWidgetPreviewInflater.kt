package vad.dashing.tbox.ui

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.annotation.StringRes

/**
 * Builds a small preview [View] for catalog rows using provider metadata.
 * Falls back to the app icon when no preview is declared.
 */
object AppWidgetPreviewInflater {

    fun inflatePreview(rootContext: Context, info: AppWidgetProviderInfo, @StringRes placeholderRes: Int): View {
        tryPreview(rootContext, info)?.let { return it }
        tryIcon(rootContext, info)?.let { return it }
        return TextView(rootContext).apply {
            text = rootContext.getString(placeholderRes)
            textSize = 12f
        }
    }

    private fun tryPreview(rootContext: Context, info: AppWidgetProviderInfo): View? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.previewLayout != 0) {
            try {
                val rv = RemoteViews(info.provider.packageName, info.previewLayout)
                val container = FrameLayout(rootContext)
                val applied = rv.apply(rootContext, container)
                return applied ?: container
            } catch (_: Exception) {
            }
        }
        if (info.previewImage != 0) {
            try {
                val pkgCtx = rootContext.createPackageContext(
                    info.provider.packageName,
                    Context.CONTEXT_INCLUDE_CODE
                )
                val d = pkgCtx.getDrawable(info.previewImage) ?: return null
                return ImageView(rootContext).apply {
                    setImageDrawable(d)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    adjustViewBounds = true
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun tryIcon(rootContext: Context, info: AppWidgetProviderInfo): View? {
        return try {
            val density = rootContext.resources.configuration.densityDpi
            val drawable = info.loadIcon(rootContext, density) ?: return null
            ImageView(rootContext).apply {
                setImageDrawable(drawable)
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
            }
        } catch (_: Exception) {
            null
        }
    }
}
