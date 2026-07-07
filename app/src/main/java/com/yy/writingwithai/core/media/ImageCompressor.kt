package com.yy.writingwithai.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * media-attachment-infrastructure · 图片压缩(长边 1920px + JPEG 85%)。
 *
 * fix-2026-06-24-review-r1-high H19:加 EXIF orientation 处理(`Matrix.postRotate` 防旋转错)
 * + `inSampleSize` 按目标维度直接 decode(跳过中间全尺寸 bitmap 防 OOM)。
 */
@Singleton
class ImageCompressor @Inject constructor() {
    companion object {
        private const val MAX_DIMENSION = 1920
        private const val JPEG_QUALITY = 85
    }

    fun compress(sourceFile: File, destFile: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)

        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        if (srcW <= 0 || srcH <= 0) return

        var targetW = srcW
        var targetH = srcH
        if (targetW > MAX_DIMENSION || targetH > MAX_DIMENSION) {
            val scale = MAX_DIMENSION.toFloat() / maxOf(targetW, targetH)
            targetW = (targetW * scale).toInt()
            targetH = (targetH * scale).toInt()
        }

        // fix H19:用目标尺寸算 inSampleSize,decode 出 bitmap 直接是缩放后的尺寸
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds, targetW, targetH)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOptions) ?: return

        // fix H19:EXIF orientation 处理(防旋转错)
        val orient = try {
            ExifInterface(sourceFile.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (e: Throwable) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val rotation = when (orient) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val rotated = if (rotation != 0f) {
            val matrix = Matrix().apply { postRotate(rotation) }
            try {
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
                    if (it !== bitmap) bitmap.recycle()
                }
            } catch (_: OutOfMemoryError) {
                bitmap.recycle()
                return
            }
        } else {
            bitmap
        }

        destFile.parentFile?.mkdirs()
        FileOutputStream(destFile).use { out ->
            rotated.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        rotated.recycle()
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
