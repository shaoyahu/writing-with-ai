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

    override val defaultValue: WidgetState = WidgetState()

    override suspend fun readFrom(input: InputStream): WidgetState {
        return try {
            json.decodeFromString(
                WidgetState.serializer(),
                input.readBytes().toString(Charsets.UTF_8)
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
