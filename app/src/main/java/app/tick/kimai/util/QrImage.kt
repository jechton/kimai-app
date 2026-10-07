package app.tick.kimai.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

/** Decodes a QR code from a picked image, e.g. a screenshot of Kimai's login QR. */
object QrImage {
    fun decode(
        context: Context,
        uri: Uri,
    ): String? {
        val bitmap =
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                ?: return null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        val binary = BinaryBitmap(HybridBinarizer(source))
        return runCatching { MultiFormatReader().decode(binary).text }.getOrNull()
    }
}
