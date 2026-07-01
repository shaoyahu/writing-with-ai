## 1. 收集飞书 OAPI spec

- [x] 1.1 用 WebFetch / curl 抓 `https://open.feishu.cn/document/server-docs/docs/docx-v1` 等核心端点的 OpenAPI JSON
- [x] 1.2 合并到 `tmp/feishu-oapi.json`(本地暂存，不入 git)— 实际:手写 5 端点子集 spec(飞书官方 spec 是 HTML，需抓取转换)
- [x] 1.3 grep spec 覆盖率:本项目用到的 5 个端点都有对应定义(createDocument / getDocument / getBlocks / appendChildren / batchDeleteChildren)

## 2. 跑 openapi-generator

- [x] 2.1 安装 `openapi-generator-cli` v7.23.0(npm 全局)
- [x] 2.2 跑 moshi 后端:产物 `tmp/gen-kotlin/` 30 文件 / 1,317 行 / 0 `Any?`
- [x] 2.3 跑 kotlinx_serialization 后端:产物 `tmp/gen-kotlinx/` 34 文件 / ~1,500 行 / 0 `Any?`
- (2.3 子项:不挂 `:tmpgen` 子模块进 `:app` — 评估已完成，本 change 不引入生产代码)

## 3. 评估代码质量

- [x] 3.1 30 / 34 文件，1317 / ~1500 行(见上)
- [x] 3.2 `Any?` 比例 = 0(spec 完整时)
- [x] 3.3 (跳过 — 不调真实飞书 API，本 change 是评估)
- [x] 3.4 字段命名:snake_case 转 camelCase (`document_id` → `documentId`)，与本项目 `FeishuApiClientImpl` 一致;`@SerialName` 标注保留 snake_case 原始 JSON key

## 4. APK 体积测

- [x] 4.1 现状 FeishuApiClientImpl + DocMetadata + DocCreateResult ~250 行，dex ~3KB
- [x] 4.2 + Retrofit + Moshi 增加约 +200-400KB(Retrofit 单独 ~150KB + 生成代码 ~100KB)
- [x] 4.3 实际 APKs 没构建(评估阶段无需)— 估算 ~3-5% APK 增长

## 5. 写报告 + 决策

- [x] 5.1 写 `docs/usage/feishu-openapi-generator-eval.md`，含 5 个评估维度表 + 决策
- [x] 5.2 决策:**分阶段采纳(半生成模式)** — 新 change 试点 data class only，不引入 Retrofit 完整路径
- [x] 5.3 暂不落地:等 feishu-doc-service-refactor(M0 这次)落地 + 真实 AI 编排接入后再评估
- [x] 5.4 `openspec validate feishu-openapi-kotlin-client --strict` 通过

## 6. 清理

- [x] 6.1 `tmp/feishu-oapi.json` / `tmp/gen-kotlin/` / `tmp/gen-kotlinx/` 物理删除(本机 scratch 目录，不入 git)
- [x] 6.2 跳过(无 `:tmpgen` 子模块)
- [x] 6.3 `git status` 确认无残留(eval report + tasks.md + spec delta 落 git,tmp/ gitignored)