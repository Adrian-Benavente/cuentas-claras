package com.cuentasclaras.app.util

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import kotlin.math.min

/**
 * Center-crops a bitmap to a square, optionally scales down, and encodes as JPEG.
 * Pure bitmap ops are unit-testable without Android Context.
 */
object GroupAvatarProcessor {
    const val MAX_SIZE_PX = 512
    const val JPEG_QUALITY = 85

    data class CropRect(val x: Int, val y: Int, val size: Int)

    fun centerCropRect(width: Int, height: Int): CropRect {
        require(width > 0 && height > 0) { "Bitmap dimensions must be positive" }
        val size = min(width, height)
        val x = (width - size) / 2
        val y = (height - size) / 2
        return CropRect(x = x, y = y, size = size)
    }

    fun centerCropToSquare(source: Bitmap): Bitmap {
        val rect = centerCropRect(source.width, source.height)
        return Bitmap.createBitmap(source, rect.x, rect.y, rect.size, rect.size)
    }

    fun scaleDownIfNeeded(source: Bitmap, maxSize: Int = MAX_SIZE_PX): Bitmap {
        if (source.width <= maxSize && source.height <= maxSize) return source
        return Bitmap.createScaledBitmap(source, maxSize, maxSize, true)
    }

    fun toJpegBytes(source: Bitmap, quality: Int = JPEG_QUALITY): ByteArray {
        val stream = ByteArrayOutputStream()
        check(source.compress(Bitmap.CompressFormat.JPEG, quality, stream)) {
            "Failed to compress avatar JPEG"
        }
        return stream.toByteArray()
    }

    fun processToJpegBytes(source: Bitmap, maxSize: Int = MAX_SIZE_PX): ByteArray {
        val cropped = centerCropToSquare(source)
        val scaled = scaleDownIfNeeded(cropped, maxSize)
        return toJpegBytes(scaled)
    }
}
