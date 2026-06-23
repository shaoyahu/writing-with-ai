package com.yy.writingwithai.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * media-attachment-infrastructure · 图片压缩(长边 1920px + JPEG 85%)。
 */
@Singleton
class ImageCompressor @Inject constructor() {
    companion object {
        private const val MAX_DIMENSION = 1920
        private const val JPEG_QUALITY = 85
    }

    fun compress(sourceFile: File, destFile: File) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourceFile.absolutePath, options)

        var width = options.outWidth
        var height = options.outHeight
        if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
            val scale = MAX_DIMENSION.toFloat() / maxOf(width, height)
            width = (width * scale).toInt()
            height = (height * scale).toInt()
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(options, width, height)
        }
        val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOptions) ?: return

        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        destFile.parentFile?.mkdirs()
        FileOutputStream(destFile).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        if (bitmap !== scaled) bitmap.recycle()
        scaled.recycle()
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
