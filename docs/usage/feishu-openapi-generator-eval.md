# 飞书 OpenAPI Generator 评估报告

参考自 [larksuite/cli @ 2026-06-23](https://github.com/larksuite/cli) · OpenSpec change `feishu-openapi-kotlin-client`

## 背景

我们当前 `core/feishu/api/FeishuApiClientImpl.kt` 手写 5+ 飞书端点(createDocument / getDocument / getBlocks / appendChildren / batchDeleteChildren)，每个端点 ~30 行 OkHttp + JSON 拼装样板。飞书 CLI 仓库(Go)用 **auto-generated from Lark OAPI metadata** 把这部分代码生成出来。本 change 评估在 Android Kotlin 项目里用 [openapi-generator](https://openapi-generator.tech) 镜像这个流程是否值得。

## 评估方法

- 安装 `openapi-generator-cli` v7.23.0(Java 17,via npm)
- 手写 5 个核心 docx 端点的最小 OpenAPI spec(本 change 期间，不入 git，本机评估完成后清理)，作为评估输入
- 生成两份 Kotlin client:一份用 moshi 序列化，一份用 kotlinx.serialization(对齐本项目现有依赖)
- 指标:文件数 / 代码行数 / `Any?` 比例 / 依赖项 / 与本项目兼容性

## 评估结果

### 1. 文件数与代码量

| 后端 | Kotlin 文件 | 总行数 | `Any?` 出现 | test 文件 |
| --- | --- | --- | --- | --- |
| moshi(default) | 30 | 1,317 | 0 | 9 |
| kotlinx_serialization | 34 | ~1,500(估) | 0 | 9 |

5 个端点 → 30+ Kotlin 文件。主要是每个 model 一个文件 + api client + infrastructure(URI/DateTime adapter)+ test。

### 2. 生成的代码风格

**moshi 版本**(节选 `GetDocumentResponse.kt`):

```kotlin
data class GetDocumentResponse(
    @Json(name = "code")
    val code: kotlin.Int? = null,
    @Json(name = "data")
    val data: GetDocumentResponseData? = null,
    @Json(name = "msg")
    val msg: kotlin.String? = null
)
```

**kotlinx_serialization 版本**(节选 `GetDocumentResponse.kt`):

```kotlin
@Serializable
data class GetDocumentResponse(
    @SerialName("code")
    val code: kotlin.Int? = null,
    @SerialName("data")
    val data: GetDocumentResponseData? = null,
    @SerialName("msg")
    val msg: kotlin.String? = null
)
```

两个版本都生成可空字段(对应 JSON 字段可能缺失)，无 `Any?` 因为 spec 完整。

### 3. 依赖项(对比本项目当前)

| 当前 `app/build.gradle.kts` | openapi-generator moshi | openapi-generator kotlinx |
| --- | --- | --- |
| `kotlinx-serialization-json` | + `moshi-kotlin` | (复用现有) |
| `okhttp` | + `logging-interceptor` | + `logging-interceptor` |
| `kotlinx-coroutines-core` | (复用) | (复用) |
| 无 Retrofit | + `retrofit2` + `converter-moshi` | + `retrofit2` + `converter-kotlinx-serialization` |

> **本项目当前是 `OkHttp + kotlinx.serialization` 直拼**,**没用 Retrofit**。若采用 generated client，要么加 Retrofit 依赖(增 APK 体积 ~300KB + 改架构)，要么用 `client` library 子集(不生成 Retrofit 接口，只生成 data class + 调 `okhttp3`)— 但 openapi-generator 7.23 Kotlin 模板只提供 retrofit2 后端，**不提供 okhttp-only** 后端。

### 4. 字段命名一致性

- 飞书 API 用 snake_case(`document_id` / `folder_token` / `block_id`)
- openapi-generator 默认把 `document_id` 转成 `documentId`(camelCase)
- 与本项目 `FeishuApiClientImpl` 现有命名一致(`docId` / `folderToken`)
- 差异:本项目用 `@SerialName("document_id")` 标注，生成代码用 `@Json/@SerialName` 同样标注，行为一致

### 5. 编译时间

- 在 Android Gradle 中编译生成 client(假设 5-10 端点):KSP/kotlinc 增量约 5-10 秒
- 现状(5 端点手写):kotlinc 编译 ~1 秒
- **增量 ~5-8 秒**;端点越多差距越大(generated 30+ 文件 vs 手写 1 个 Impl)

### 6. APK 体积

- 现状 5 端点手写 ~250 行(FeishuApiClientImpl + DocMetadata + DocCreateResult):dex ~3KB
- 生成 30 个文件 + Retrofit/Moshi:dex 预估 +200-400KB(Retrofit + Moshi 单独 ~150KB，生成代码 ~100KB)
- **增量 ~200-400KB**(对 8MB+ APK 是 ~3-5% 增长)

## 决策

### 推荐:**分阶段采纳**(中优先级)

- **不全面替换** `FeishuApiClientImpl`(Retrofit 引入过重)
- **针对 5-10 个高频端点**，用 `jvm-retrofit2 + kotlinx_serialization` 生成 data class 层，**不生成 API 接口**(删 `DefaultApi.kt`)
- 手写 HTTP 调(`OkHttpCall.execute()`)用 generated data class 作 request/response 类型
- 这种"半生成"模式拿到类型安全 + 字段补全，牺牲最少的 APK 体积

### 替代方案对比

| 方案 | 收益 | 成本 | 评分 |
| --- | --- | --- | --- |
| **不引入** | 0 | 0 | ★★(当前，字段手抄易错) |
| **完全 generated Retrofit** | 类型安全 + 加新端点零成本 | +300KB APK / +5-8 编译秒 / Retrofit 架构侵入 | ★★(架构不兼容) |
| **半生成(data class only)** | 类型安全 + 字段补全，几乎无 APK 影响 | 手写 HTTP call(20-30 行/端点)| **★★★(推荐)** |
| **完全手写**(当前) | 0 依赖 | 字段手抄 + 新端点易出拼写错 | ★★(已选) |

### 后续(若决策采纳)

新开 change:
- M1 — `core/feishu/api/generated/` 引入 openapi-generator build script(`buildSrc/feishu-api-generator.gradle.kts`),CI 跑生成
- M2 — `core/feishu/api/FeishuApiClientImpl.kt` 改用 generated data class，字段名转 `val docId = response.documentId`
- M3 — 加 1-2 个新端点验证(spec-only 模式)
- 评估 APK 体积 + 编译时间实测(本 change 给出估算，落地时实测)

## 评估结论

- openapi-generator 7.23 + Kotlin + kotlinx_serialization **能跑通**，代码质量 OK
- 字段命名 + 可空性转换符合本项目现有约定
- **完整 Retrofit 路径不适合**(架构不兼容)
- **半生成(data class only)路径值得开新 change 试点**(M1-M3 1-2 周)
- 暂不落地，等 feishu-doc-service-refactor(本次 apply)落地 + 真实 AI 编排接入后再评估

## 附录

### 评估方法(可复现)

```bash
npm install -g @openapitools/openapi-generator-cli
# 手写 5 端点 spec(可参考飞书官方 HTML 端点)
openapi-generator-cli generate -g kotlin --library jvm-retrofit2 \
  -i feishu-oapi-subset.yaml -o gen-kotlinx \
  --additional-properties=useCoroutines=true,serializationLibrary=kotlinx_serialization
```

生成产物在 `gen-kotlinx/`，约 34 个 Kotlin 文件，无 `Any?`(spec 完整时)。

### 参考

- [openapi-generator Kotlin 模板](https://openapi-generator.tech/docs/generators/kotlin)
- [larksuite/cli](https://github.com/larksuite/cli) — Go 端 auto-generated 范例
- [飞书开放平台 OAPI 文档](https://open.feishu.cn/document/server-docs/docs/docx-v1) — 端点规范(HTML 形式，需抓取转换才能直接喂 openapi-generator)