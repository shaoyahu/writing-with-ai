## Why

M1 已经把随手记的完整数据闭环(`Note` + `NoteTagCrossRef` + CRUD + 列表/详情/编辑)落地，`Note.lastAiOp` / `lastAiAt` 字段已预留但始终为 null。现在进入 M2:把 AI 抽象层搭好 — `AiGateway` 统一入口 + `AnthropicCompatibleAdapter`(三家合一)+ `FakeProvider` 端到端走通 — **不接真 provider**(roadmap §15.1 拍板:真联调推迟到 M5)。M2 是为 M3 `ai-writing-actions` 铺路，避免 M3 一边写 UI 一边调 SSE。

## What Changes

- 新增 **AiGateway 抽象层**(`core/ai/api/`):`AiGateway` 接口(业务入口)+ `AiProvider` SPI(provider 适配器)+ `AiRequest`/`AiCredentials`/`AiStreamEvent`/`AiError` 通用类型
- 新增 **ProviderConfig 数据驱动模型**(`core/ai/provider/`):`ProviderConfig`(id / baseUrl / endpointPath / authStyle / model 列表)+ `AuthStyle` 枚举(BEARER / X_API_KEY / CUSTOM_HEADER)
- 新增 **AnthropicCompatibleAdapter**(唯一 `AiProvider` 实现):基于 OkHttp + SSE 解析，支持 `POST /v1/messages`(Anthropic Messages API 兼容端点)，由 `ProviderConfig` 驱动认证/URL/字段差异，不 为每家写独立 adapter(roadmap §6.3)
- 新增 **SSE 解析器**(`core/ai/stream/`):`SseParser` 把 OkHttp streaming body 解析为 `Flow<SseEvent>`，处理 chunked/line-delimited/重连/超时;`SseEvent` 映射为 `AiStreamEvent`
- 新增 **三家 ProviderConfig 数据**(`core/ai/provider/{deepseek,minimax,mimo}/`):纯 Kotlin object，贡献配置(id / baseUrl / auth / models)，不 写 adapter
- 新增 **FakeProvider** + **FakeAiProvider**(`core/ai/fake/`):返回可配置的固定文本(支持故意延迟 + 错误注入 + token 用量)，供 M2/M3 所有单测和 UI 验收用(真 provider 联调不阻塞 M2)
- 新增 **AiHistory 持久化**(`core/data/db/`):`AiHistoryEntity` Room Entity + `AiHistoryDao` + `AppDatabase` 补充;`AiGateway` 在每个完成/失败事件落库
- 新增 **AiHistory model** + `AiHistoryRepository`:供 M3+ 设置页"用量统计"查询
- 新增 **prompt 模板目录**(`core/ai/prompt/`):每个操作(扩写/润色/整理)的 system prompt 模板，纯常量 `internal object`，用户文本不拼进 system 段(注入防御)
- 修改 **`core/data/db/AppDatabase`**:新加 `AiHistoryEntity`,`version` 升到 2 + `Migration(1→2)`(v1→v2 AutoMigration 或 manual)
- **BREAKING**:无(全新增，不改 M1 接口)
- **不引入**:真 provider 联调(推迟 M5)/ Retrofit(v1 不用)/ 用户同意页(M4)/ apikey 加密存储(M4，当前用 fake credentials)/ 设置页 UI(M3/M4)

## Capabilities

### New Capabilities
- `ai-gateway`:AiGateway + AiProvider SPI + ProviderConfig + AnthropicCompatibleAdapter + SSE 解析 + SseEvent 错误映射 + FakeProvider + prompt 模板
- `ai-history`:AiHistory Room Entity + DAO + Repository + token 用量记录 + 每次 AI 调用自动落库

### Modified Capabilities
- `quick-note`:M2 在 `Note.lastAiOp` / `lastAiAt` 中写入 AI 操作类型和时间戳(当 AiGateway 完成一次 stream 后回调 Repository 更新);M1 这两字段一直为 null，现在被 M2 端到端流程填充

## Impact

- **新增 package**:
  - `core/ai/api/` — AiGateway / AiProvider / AiRequest / AiCredentials / AiStreamEvent / AiError
  - `core/ai/provider/` — ProviderConfig / AuthStyle / AnthropicCompatibleAdapter
  - `core/ai/provider/{deepseek,minimax,mimo}/` — 三家 ProviderConfig 数据
  - `core/ai/fake/` — FakeProvider(FakeAiProvider + FakeConfig)
  - `core/ai/stream/` — SseParser / SseEventType / SseError
  - `core/ai/prompt/` — 三类操作的 system prompt 模板
  - `core/data/db/entity/AiHistoryEntity.kt` — Room Entity
  - `core/data/db/AiHistoryDao.kt` — DAO
  - `core/data/model/AiHistory.kt` — 领域模型
  - `core/data/repo/AiHistoryRepository.kt` — 仓库
- **修改**:
  - `core/data/db/AppDatabase.kt` — 加 `AiHistoryEntity`,version→2，加 Migration(1→2)
  - `core/data/repo/NoteRepository.kt` — `updateAiMetadata(noteId, op, at)` 写 `lastAiOp`/`lastAiAt`
  - `app/build.gradle.kts` — 加 OkHttp 测试依赖(若 `mockwebserver` 未引入)
  - `gradle/libs.versions.toml` — 加 kotlinx-serialization(已在 M1 引入，确认)
- **新增依赖**:无(M0 已加 OkHttp / kotlinx-serialization;M2 只加 `okhttp-mockwebserver` testImplementation)
- **风险**:
  - Anthropic 协议兼容性:M2 不接真 provider，用 FakeProvider 验;数据驱动的 ProviderConfig 和 AnthropicCompatibleAdapter 的协议正确性只能靠 FakeProvider + 单元测试验;**真 API 行为差异推迟 M5 联调**
  - SSE 解析健壮性:OkHttp streaming body + line-delimited SSE 解析;reconnection/truncation/incomplete-last-chunk 用单元测试覆盖
  - 数据库 version 升级路径:已有 M1 schema(v1)→ v2 加 AiHistory 表;用 `Migration(1, 2)` explicit(不依赖 AutoMigration，因为 v1 用户可能还没数据)
  - Prompt 模板维护:纯常量，无动态拼接;后续 review 锁
  - FakeProvider 延迟在 UI 线程上的表现:Compose 侧需要用 LaunchedEffect / collectAsStateWithLifecycle，不可阻塞主线程