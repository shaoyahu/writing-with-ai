## Why

我们当前 `core/feishu/api/FeishuApiClientImpl.kt` 每个飞书端点(createDoc / getDoc / updateContent / appendChildren / batchDelete 等)都是手写 OkHttp 调用，字段名手抄、请求体手拼。飞书 CLI 仓库(Go)把"端点封装"做成了 **auto-generated from Lark OAPI metadata** — 加新端点 = 加 method 声明 + 调，字段拼错编译就挂。Android 端可以用 `openapi-generator` 镜像这个流程，从飞书官方 OpenAPI spec 生成 Kotlin client，替换手写样板。

## What Changes

- **调研**:`openapi-generator`(CLI / Maven plugin / Docker)对 Kotlin client 的产出质量、APK 体积、编译时间
- **不直接落地生成代码**(本 change 只评估 + 决策)
- **产出物**:`docs/usage/feishu-openapi-generator-eval.md` 评估报告 + 决策(继续 / 放弃 / 分阶段)
- 若决策"继续"，新开 change 做实际生成与替换;若"放弃"，归档结论到 `docs/progress.md`

## Capabilities

### New Capabilities

无。

### Modified Capabilities

无。

## Impact

- **新增文档**:`docs/usage/feishu-openapi-generator-eval.md`(纯调研报告，无代码改动)
- **代码**:无
- **依赖**:调研阶段需 `openapi-generator-cli` 二进制(本地运行，不进 APK);不引入 Gradle plugin
- **风险**:APK 体积可能涨 3-8MB(generated Kotlin client 偏 verbose);生成代码可读性差;spec 不全时大量 `Any?` 类型
- **回退**:本 change 只产出报告，不引入代码;若决策"放弃"，零成本回退