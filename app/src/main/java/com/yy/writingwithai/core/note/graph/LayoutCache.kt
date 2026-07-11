package com.yy.writingwithai.core.note.graph

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * note-graph-view · 布局坐标 SharedPreferences 缓存。
 *
 * key 格式:`graph_layout_<sanitized>_v1`(tasks §1.4 + spec §"Note-graph-view layout coordinates are cached")。
 *
 * sanitize:noteId 替换非 `[a-zA-Z0-9_-]` 为 `_`,防止 SharedPreferences key 含 `:` 等保留字符。
 * value:kotlinx.serialization 序列化的 `Map<String, NodeCoordsJson>`。
 *
 * 写入路径收敛:仅 [put] 一次(收敛成功 / fallback circular 都写)— 保证下次同 note 进入直接命中。
 * 命中条件:缓存存在 **且** map key 集合 ⊇ snapshot.nodes.noteId(差集检查见 caller)。
 */
@Singleton
class LayoutCache
@Inject
constructor(
    @ApplicationContext context: Context
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val mapSerializer = MapSerializer(String.serializer(), NodeCoordsJson.serializer())

    private val json: Json = Json { ignoreUnknownKeys = true }

    /** 返回 null 当 key 不存在或反序列化失败(不抛异常,UI 层视同首次进入)。 */
    fun get(noteId: String): Map<String, NodeCoords>? {
        val key = keyFor(noteId) ?: return null
        val raw = prefs.getString(key, null) ?: return null
        return try {
            json.decodeFromString(mapSerializer, raw)
                .mapValues { NodeCoords(it.value.x, it.value.y) }
        } catch (e: Exception) {
            // corrupt JSON —— 删掉别卡住下次
            prefs.edit().remove(key).apply()
            null
        }
    }

    fun put(noteId: String, coords: Map<String, NodeCoords>) {
        val key = keyFor(noteId) ?: return
        val payload = json.encodeToString(
            mapSerializer,
            coords.mapValues { NodeCoordsJson(it.value.x, it.value.y) }
        )
        prefs.edit().putString(key, payload).apply()
    }

    /** 把 noteId 限于 [a-zA-Z0-9_-];任何不一致字符替成 `_`。 */
    internal fun keyFor(noteId: String): String? {
        if (noteId.isEmpty()) return null
        val sanitized = noteId.replace(SANITIZE_PATTERN, "_")
        return "graph_layout_${sanitized}_v1"
    }

    companion object {
        const val PREFS_NAME = "graph_layout_cache"
        private val SANITIZE_PATTERN = Regex("[^a-zA-Z0-9_-]")
    }

    @Serializable
    private data class NodeCoordsJson(val x: Float, val y: Float)
}
