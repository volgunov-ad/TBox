package vad.dashing.tbox.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vad.dashing.tbox.ui.theme.TboxAppTheme
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28])
class HorizontalSectionTabRowScreenshotTest {
    @Test
    fun exportGeopositionPreviewPng() {
        savePng(
            renderPreview(widthPx = 1280, heightPx = 120) {
                TboxAppTheme(theme = 1) {
                    HorizontalSectionTabRowGeopositionPreviewContent(selectedIndex = 0)
                }
            },
            "horizontal_section_tab_row_geoposition_preview.png",
        )
    }

    @Test
    fun exportCarSettingsPreviewPng() {
        savePng(
            renderPreview(widthPx = 1280, heightPx = 120) {
                TboxAppTheme(theme = 1) {
                    HorizontalSectionTabRowCarSettingsPreviewContent(selectedIndex = 2)
                }
            },
            "horizontal_section_tab_row_car_settings_preview.png",
        )
    }

    private fun renderPreview(
        widthPx: Int,
        heightPx: Int,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ): Bitmap {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val composeView = ComposeView(activity).apply {
            setContent(content)
        }
        activity.setContentView(composeView)
        val widthSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        composeView.measure(widthSpec, heightSpec)
        composeView.layout(0, 0, widthPx, heightPx)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        composeView.draw(Canvas(bitmap))
        return bitmap
    }

    private fun savePng(bitmap: Bitmap, fileName: String) {
        val dir = File("/opt/cursor/artifacts")
        dir.mkdirs()
        FileOutputStream(File(dir, fileName)).use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                "Failed to compress preview bitmap"
            }
        }
    }
}
