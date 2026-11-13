package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.media.ExifInterface
import android.net.Uri
import androidx.annotation.DrawableRes
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ImageFilterProcessor {

    private const val MAX_WORKING_EDGE_PX = 2048

    fun decodeBitmapFromUri(context: Context, uri: Uri, maxEdgePx: Int = MAX_WORKING_EDGE_PX): Bitmap? {
        val resolver = context.contentResolver
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        }
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

        val sampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, maxEdgePx)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return null

        val orientation = try {
            resolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val oriented = applyExifOrientation(raw, orientation)
        return scaleDownIfNeeded(oriented, maxEdgePx)
    }

    fun decodeBitmapFromResource(
        context: Context,
        @DrawableRes resId: Int,
        maxEdgePx: Int = MAX_WORKING_EDGE_PX
    ): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, resId, boundsOpts)
        val sampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, maxEdgePx)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeResource(context.resources, resId, decodeOpts) ?: return null
        return scaleDownIfNeeded(decoded, maxEdgePx)
    }

    fun decodeBitmapFromFile(path: String, maxEdgePx: Int = MAX_WORKING_EDGE_PX): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null
        val sampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, maxEdgePx)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(path, decodeOpts) ?: return null
        return scaleDownIfNeeded(decoded, maxEdgePx)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxEdgePx: Int): Int {
        var inSampleSize = 1
        val longest = max(width, height)
        if (longest > maxEdgePx) {
            while (longest / (inSampleSize * 2) >= maxEdgePx) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxEdgePx: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxEdgePx) {
            return if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap
            else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
        val ratio = maxEdgePx.toFloat() / longest.toFloat()
        val targetW = max(1, (bitmap.width * ratio).roundToInt())
        val targetH = max(1, (bitmap.height * ratio).roundToInt())
        val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    fun buildColorMatrix(adjustments: ImageAdjustments): ColorMatrix {
        val combined = ColorMatrix()

        // 1. Filter preset base matrix
        val presetMatrix = when (adjustments.filterPreset) {
            FilterPreset.ORIGINAL -> ColorMatrix()
            FilterPreset.WARM -> ColorMatrix(
                floatArrayOf(
                    1.12f, 0f, 0f, 0f, 8f,
                    0f, 1.03f, 0f, 0f, 2f,
                    0f, 0f, 0.88f, 0f, -6f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            FilterPreset.COOL -> ColorMatrix(
                floatArrayOf(
                    0.90f, 0f, 0f, 0f, -4f,
                    0f, 1.02f, 0f, 0f, 2f,
                    0f, 0f, 1.15f, 0f, 10f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            FilterPreset.MONOCHROME -> ColorMatrix().apply { setSaturation(0f) }
            FilterPreset.VINTAGE -> ColorMatrix(
                floatArrayOf(
                    0.92f, 0.28f, 0.08f, 0f, 12f,
                    0.12f, 0.86f, 0.08f, 0f, 8f,
                    0.06f, 0.14f, 0.68f, 0f, 4f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            FilterPreset.VIVID -> ColorMatrix().apply {
                setSaturation(1.38f)
                val boost = ColorMatrix(
                    floatArrayOf(
                        1.08f, 0f, 0f, 0f, -6f,
                        0f, 1.08f, 0f, 0f, -6f,
                        0f, 0f, 1.08f, 0f, -6f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                postConcat(boost)
            }
            FilterPreset.CINEMA -> ColorMatrix(
                floatArrayOf(
                    1.08f, -0.04f, -0.02f, 0f, -6f,
                    -0.02f, 1.04f, 0.02f, 0f, -4f,
                    -0.02f, 0.05f, 1.12f, 0f, 6f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }
        combined.postConcat(presetMatrix)

        // 2. Saturation slider
        if (adjustments.saturation != 1f) {
            val satMatrix = ColorMatrix().apply { setSaturation(adjustments.saturation) }
            combined.postConcat(satMatrix)
        }

        // 3. Contrast & Brightness
        val c = adjustments.contrast
        val t = (1f - c) * 128f + adjustments.brightness * 255f
        val bcMatrix = ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )
        )
        combined.postConcat(bcMatrix)

        // 4. Warmth
        if (adjustments.warmth != 0f) {
            val w = adjustments.warmth * 36f
            val warmMatrix = ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, w,
                    0f, 1f, 0f, 0f, w * 0.25f,
                    0f, 0f, 1f, 0f, -w,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            combined.postConcat(warmMatrix)
        }

        return combined
    }

    fun applyAdjustmentsToBitmap(source: Bitmap, adjustments: ImageAdjustments): Bitmap {
        if (adjustments.isDefault) {
            return source.copy(Bitmap.Config.ARGB_8888, false)
        }
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(buildColorMatrix(adjustments))
        }
        canvas.drawBitmap(source, 0f, 0f, paint)

        if (adjustments.vignette > 0.01f) {
            val cx = source.width / 2f
            val cy = source.height / 2f
            val radius = max(cx, cy) * 1.15f
            val edgeAlpha = (adjustments.vignette.coerceIn(0f, 0.9f) * 210).roundToInt()
            val edgeColor = android.graphics.Color.argb(edgeAlpha, 5, 8, 15)
            val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cx,
                    cy,
                    radius,
                    intArrayOf(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                        edgeColor
                    ),
                    floatArrayOf(0f, 0.52f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, source.width.toFloat(), source.height.toFloat(), vignettePaint)
        }

        return output
    }

    fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun flipBitmap(source: Bitmap, horizontal: Boolean, vertical: Boolean): Bitmap {
        val sx = if (horizontal) -1f else 1f
        val sy = if (vertical) -1f else 1f
        val matrix = Matrix().apply { postScale(sx, sy, source.width / 2f, source.height / 2f) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun cropBitmapNormalized(
        source: Bitmap,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ): Bitmap {
        val l = (left.coerceIn(0f, 0.95f) * source.width).roundToInt()
        val t = (top.coerceIn(0f, 0.95f) * source.height).roundToInt()
        val r = (right.coerceIn(left + 0.05f, 1f) * source.width).roundToInt()
        val b = (bottom.coerceIn(top + 0.05f, 1f) * source.height).roundToInt()
        val w = max(16, min(source.width - l, r - l))
        val h = max(16, min(source.height - t, b - t))
        return Bitmap.createBitmap(source, l, t, w, h)
    }
}
