package com.yy.writingwithai.core.media

import android.graphics.Bitmap
import androidx.collection.LruCache

/**
 * M7 fix:应用级 Bitmap LRU 缓存，避免 LazyColumn 滚出再滚入时重复 decodeFile。
 *
 * 容量 = 可用内存 / 8（典型 Android 设备 128MB 堆 → 16MB 缓存）。
 * 通过 [instance] 懒加载单例访问，Composable 中无需 Hilt 注入。
 */
class LruBitmapCache(maxSizeBytes: Int = defaultSize()) {

    private val cache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(maxSizeBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    /** 获取缓存 Bitmap，未命中返回 null。 */
    fun get(path: String): Bitmap? = cache.get(path)

    /** 存入缓存，若已存在则替换。已回收的 Bitmap 会被忽略。 */
    fun put(path: String, bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        cache.put(path, bitmap)
    }

    companion object {
        /** 全局懒加载单例，Composable 中直接 `LruBitmapCache.instance` 访问。 */
        val instance: LruBitmapCache by lazy { LruBitmapCache() }

        fun defaultSize(): Int {
            val maxMemory = Runtime.getRuntime().maxMemory()
            return if (maxMemory > 0) (maxMemory / 8).toInt() else 16 * 1024 * 1024
        }
    }
}
