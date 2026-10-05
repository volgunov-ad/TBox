package vad.dashing.tbox

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BitmapComposeSupportTest {

    @Test
    fun computeUiImageInSampleSize_tallPhonePhoto_fitsMaxEdge() {
        // Luna1 theme had 2160×3840 as a tile background for a ~549×977 overlay.
        val sample = computeUiImageInSampleSize(
            srcWidth = 2160,
            srcHeight = 3840,
            maxEdgePx = UI_IMAGE_DECODE_MAX_EDGE_PX,
        )
        assertTrue(sample >= 2)
        assertTrue(2160 / sample <= UI_IMAGE_DECODE_MAX_EDGE_PX)
        assertTrue(3840 / sample <= UI_IMAGE_DECODE_MAX_EDGE_PX)
    }

    @Test
    fun computeUiImageInSampleSize_alreadySmall_isOne() {
        assertEquals(1, computeUiImageInSampleSize(800, 600, UI_IMAGE_DECODE_MAX_EDGE_PX))
    }

    @Test
    fun decodeFileToOwnedImageBitmap_downsamplesHugeJpeg() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val file = File(ctx.cacheDir, "huge_tile.jpg")
        writeSolidJpeg(file, width = 2160, height = 3840)
        val decoded = decodeFileToOwnedImageBitmap(file)
        assertNotNull(decoded)
        assertTrue(decoded!!.width <= UI_IMAGE_DECODE_MAX_EDGE_PX)
        assertTrue(decoded.height <= UI_IMAGE_DECODE_MAX_EDGE_PX)
        assertTrue(decoded.width.toLong() * decoded.height.toLong() <= UI_IMAGE_DECODE_MAX_PIXELS)
    }

    @Test
    fun shrinkImageFileIfOversized_rewritesTallJpeg() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val file = File(ctx.cacheDir, "shrink_me.jpg")
        writeSolidJpeg(file, width = 2160, height = 3840)
        val before = file.length()
        assertTrue(shrinkImageFileIfOversized(file))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        assertTrue(bounds.outWidth <= UI_IMAGE_DECODE_MAX_EDGE_PX)
        assertTrue(bounds.outHeight <= UI_IMAGE_DECODE_MAX_EDGE_PX)
        assertTrue(file.length() > 0L)
        // Shrunk file should be much smaller than a full-res encode of the same solid bitmap.
        assertTrue(file.length() < before || before < 50_000L)
    }

    private fun writeSolidJpeg(file: File, width: Int, height: Int) {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.BLUE)
        FileOutputStream(file).use { out ->
            assertTrue(bmp.compress(Bitmap.CompressFormat.JPEG, 90, out))
        }
        bmp.recycle()
    }
}
