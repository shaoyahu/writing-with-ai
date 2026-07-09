package com.yy.writingwithai.core.serialization

import kotlinx.serialization.json.Json

/**
 * fix-full-review M57:统一 Json 配置基线,避免各处独立 `Json {}` 配置漂移。
 *
 * 默认配置:
 * - `ignoreUnknownKeys = true`:服务端新增字段不破坏客户端(向后兼容)
 * - `encodeDefaults = false`:默认字段不写入序列化输出(M40 教训 —— AiRequest.maxOutput
 *   因为 encodeDefaults=true 默认行为导致默认值不序列化,Anthropic 协议 422 报错)
 * - `explicitNulls = false`:`null` 不写入输出,服务端拿不到字段时按 nullable 处理
 *
 * 单实例(singleton):Json 内部 `JsonInstance` 是线程安全的,所有调用方共享同一份配置。
 * 注意:已经有 `encodeDefaults=true` 需求的模块(主动期望默认值被序列化)不要直接换,
 * 保留本地 `Json {}` —— 此处只解决「多数场景配置一致」问题,不强制替换所有 Json 实例。
 */
object JsonConfig {
    val Default: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }
}
