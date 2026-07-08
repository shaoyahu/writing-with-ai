package com.yy.writingwithai.core.widget

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * widget-rome-compat · [WidgetState] DataStore Serializer。
 *
 * GlanceStateDefinition 内部通过此 serializer 读 / 写 `widget_state` 文件;
 * 默认 JSON 配置 `ignoreUnknownKeys = true`(向前兼容 schema 演进)。
 */
object WidgetStateSerializer : Serializer<WidgetState> {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // fix-full-review MEDIUM:限制最大读取 64KB，防止异常大文件导致 OOM。
    // DataStore widget_state 文件正常 < 1KB，64KB 已是极大余量。
    // 注意:readNBytes() 是 Java 9+ API，Android minSdk 26 只支持 Java 8，
    // 改用 BufferedInputStream + 手动限长读取。
    private const val MAX_READ_BYTES = 64 * 1024

    override val defaultValue: WidgetState = WidgetState()

    override suspend fun readFrom(input: InputStream): WidgetState {
        return try {
            val buffered = input.buffered()
            val bytes = buffered.readUpTo(MAX_READ_BYTES)
            json.decodeFromString(
                WidgetState.serializer(),
                bytes.toString(Charsets.UTF_8)
            )
        } catch (e: SerializationException) {
            throw CorruptionException("Failed to read WidgetState", e)
        }
    }

    override suspend fun writeTo(t: WidgetState, output: OutputStream) {
        output.write(
            json.encodeToString(WidgetState.serializer(), t).toByteArray(Charsets.UTF_8)
        )
    }
}

/** 读取最多 [maxBytes] 字节，兼容 Java 8(Android minSdk 26)。 */
private fun InputStream.readUpTo(maxBytes: Int): ByteArray {
    val buffer = ByteArray(minOf(maxBytes, available().coerceAtLeast(256)))
    val output = java.io.ByteArrayOutputStream()
    var totalRead = 0
    while (totalRead < maxBytes) {
        val toRead = minOf(buffer.size, maxBytes - totalRead)
        val read = read(buffer, 0, toRead)
        if (read == -1) break
        output.write(buffer, 0, read)
        totalRead += read
    }
    return output.toByteArray()
}
