## Context

`core/feishu/api/FeishuApiClientImpl.kt` 手写 20+ 飞书端点,字段名手抄、请求体手拼,新加端点 = 查官方文档 + 手写 30-50 行 boilerplate。飞书 CLI 仓库(Go)用 auto-generated from Lark OAPI metadata 把这部分代码生成出来。本 change **只评估** `openapi-generator` 是否能在 Android Kotlin 项目里 mirror 这个流程,不直接落地生成代码。

## Goals / Non-Goals

**Goals**
- 产出评估报告 `docs/usage/feishu-openapi-generator-eval.md`,含:
  - openapi-generator 对飞书 OAPI spec 的可生成性
  - 产出 Kotlin 代码质量(可读性、类型推断、Any? 比例)
  - APK 体积影响(增量 KB / MB)
  - 编译时间影响(增量秒数)
  - 维护成本(spec 更新 → 重新生成 → diff 整合)
- 给出明确决策:**继续(进 v2 change 落地)** / **放弃(归档结论)** / **分阶段(先小范围)**

**Non-Goals**
- 不直接生成 client 代码
- 不引入 Gradle plugin / Gradle 依赖
- 不替换现有 `FeishuApiClientImpl`

## Decisions

### 1. 调研方法

- 下载飞书官方 OpenAPI spec(从 `https://open.feishu.cn/document/server-docs/*` 各端点收集)
- 用 `openapi-generator-cli generate -g kotlin` 跑出最小子集(只 `docx` 端点)
- 评估:代码行数 / `Any?` 比例 / 编译时间
- 用 `apkanalyzer` 或 `unzip apk + size` 测体积影响(对比现状 + 加 generated client)

### 2. 评估维度

| 维度 | 关键问题 | 决策阈值 |
| --- | --- | --- |
| 可生成性 | 飞书 spec 是否有完整 JSON? | 必填字段是否齐全 |
| 代码质量 | `Any?` 比例 | < 30% 可用;30-60% 包装层补救;> 60% 弃 |
| APK 体积 | generated class 数 + dex 大小 | < 2MB 可接受 |
| 编译时间 | incremental KSP/kapt 增量 | < 30s 可接受 |
| 维护成本 | spec 漂移检测 | 简单 cron + diff 即可 |

### 3. 决策点

- **继续** → 新开 change 落地(用生成代码替换 `FeishuApiClientImpl`)
- **放弃** → 归档报告到 `docs/progress.md`,"手写 client 在当前规模下更可控"
- **分阶段** → 只生成高频端点(5-10 个),其余继续手写

## Risks / Trade-offs

[Risk] **飞书 spec 不全或字段漂移** — 官方文档可能未全部 openapi 化
→ Mitigation:调研阶段先 grep spec 覆盖率,若 < 80% 走"分阶段"

[Risk] **generated 代码风格与项目不符** — openapi-generator 产出可能用 Moshi/Gson,与项目当前用的 kotlinx.serialization 不一致
→ Mitigation:评估时检查 generated 文件依赖,不一致就在生成后改模板或写包装层

[Risk] **APK 体积爆炸** — 每个端点生成 1 个 request + 1 个 response class,20 端点 = 40+ 类
→ Mitigation:用 `--global-property apis,models` 控制只生成需要的;或只生成 data class 层 + 手写 HTTP 调

## Migration Plan

1. M1 — 下载飞书 OpenAPI spec(docx 子集 5-10 端点)→ 存 `tmp/feishu-oapi.json`
2. M2 — 跑 `openapi-generator-cli generate -g kotlin -i tmp/feishu-oapi.json -o tmp/gen`
3. M3 — 评估:代码行数 / Any? 比例 / 编译过 / 字段命名
4. M4 — APK 体积测:复制现有最小子集,加 generated,apkanalyzer 比对
5. M5 — 写评估报告 `docs/usage/feishu-openapi-generator-eval.md` + 决策

**回退**:报告不通过,本 change 直接归档,不引入代码。

## Open Questions

- Q1:飞书官方 OpenAPI spec 是否可机器获取?(`https://open.feishu.cn/document/server-docs` 是 HTML,需抓取转换)
- Q2:openapi-generator 的 Kotlin 模板支持 `kotlinx.serialization` 还是只支持 Moshi?
- Q3:若走 generated,`FeishuApiClient` 公开接口是否需要保留 facade 兼容现有 caller?