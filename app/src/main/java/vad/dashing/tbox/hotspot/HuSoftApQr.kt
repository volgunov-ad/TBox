package vad.dashing.tbox.hotspot

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun huSoftApQrImage(payload: String, sizePx: Int): ImageBitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1)
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val pixels = IntArray(sizePx * sizePx)
    for (y in 0 until sizePx) {
        val row = y * sizePx
        for (x in 0 until sizePx) {
            pixels[row + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
        }
    }
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
    return bitmap.asImageBitmap()
}

@Composable
internal fun rememberHuSoftApQrImage(payload: String, sizePx: Int): State<ImageBitmap?> =
    produceState<ImageBitmap?>(initialValue = null, payload, sizePx) {
        value = withContext(Dispatchers.Default) {
            runCatching { huSoftApQrImage(payload, sizePx) }.getOrNull()
        }
    }
