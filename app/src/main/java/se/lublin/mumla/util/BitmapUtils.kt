package se.lublin.mumla.util

import android.graphics.Bitmap
import kotlin.math.min
import kotlin.math.roundToInt

object BitmapUtils {
    @JvmStatic
    fun resizeKeepingAspect(image: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = image.width
        val height = image.height

        if (width < maxWidth && height < maxHeight) {
            return image
        }

        val ratioBitmap = width.toFloat() / height.toFloat()
        val ratioMax = maxWidth.toFloat() / maxHeight.toFloat()

        var finalWidth = maxWidth
        var finalHeight = maxHeight
        if (ratioMax > ratioBitmap) {
            finalWidth = (maxHeight * ratioBitmap).toInt()
        } else {
            finalHeight = (maxWidth / ratioBitmap).toInt()
        }

        return Bitmap.createScaledBitmap(image, finalWidth, finalHeight, true)
    }

    fun blurForBackground(image: Bitmap, radius: Int = 12, maxSize: Int = 180): Bitmap {
        val scale = min(1f, maxSize.toFloat() / image.width.coerceAtLeast(image.height))
        val scaledWidth = (image.width * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (image.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = if (scaledWidth == image.width && scaledHeight == image.height) {
            image
        } else {
            Bitmap.createScaledBitmap(image, scaledWidth, scaledHeight, true)
        }
        val bitmap = scaled.copy(Bitmap.Config.ARGB_8888, true)
        val safeRadius = radius.coerceIn(1, (bitmap.width.coerceAtMost(bitmap.height) - 1).coerceAtLeast(1))
        repeat(3) {
            boxBlur(bitmap, safeRadius)
        }
        return bitmap
    }

    private fun boxBlur(bitmap: Bitmap, radius: Int) {
        val width = bitmap.width
        val height = bitmap.height
        val source = IntArray(width * height)
        val horizontal = IntArray(width * height)
        val output = IntArray(width * height)
        bitmap.getPixels(source, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                var alpha = 0
                var red = 0
                var green = 0
                var blue = 0
                val count = radius * 2 + 1
                for (offset in -radius..radius) {
                    val pixel = source[y * width + (x + offset).coerceIn(0, width - 1)]
                    alpha += pixel ushr 24
                    red += pixel shr 16 and 0xff
                    green += pixel shr 8 and 0xff
                    blue += pixel and 0xff
                }
                horizontal[y * width + x] =
                    (alpha / count shl 24) or
                    (red / count shl 16) or
                    (green / count shl 8) or
                    (blue / count)
            }
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                var alpha = 0
                var red = 0
                var green = 0
                var blue = 0
                val count = radius * 2 + 1
                for (offset in -radius..radius) {
                    val pixel = horizontal[(y + offset).coerceIn(0, height - 1) * width + x]
                    alpha += pixel ushr 24
                    red += pixel shr 16 and 0xff
                    green += pixel shr 8 and 0xff
                    blue += pixel and 0xff
                }
                output[y * width + x] =
                    (alpha / count shl 24) or
                    (red / count shl 16) or
                    (green / count shl 8) or
                    (blue / count)
            }
        }

        bitmap.setPixels(output, 0, width, 0, 0, width, height)
    }
}
