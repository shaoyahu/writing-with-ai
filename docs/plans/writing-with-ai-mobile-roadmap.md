# writing-with-ai · 移动端 v1 路线图

> 状态:草案 v1 · 范围:v1 整体规划 · 最后更新:2026-06-18

本文件是"v1 一次性发版"前提下的整体规划。后续如需分阶段发版或重大调整,另起 `writing-with-ai-mobile-roadmap-rN.md` 留档,本文件归档到 `-archived` 状态。

---

## 0. 拍板决策(2026-06-18)

| 维度 | 决策 |
| --- | --- |
| 平台 | 安卓(只做安卓) |
| 技术栈 | 原生 **Kotlin 2.x + Jetpack Compose + Material 3** |
| 数据 | **纯本地 + 可选导出**(JSON / Markdown),不引入后端 |
| 预置模型 | **deepseek / minimax / mimo** 三家起步,provider 适配器模式 |
| 发版节奏 | 一次性发 v1,内部仍按模块分阶段实现 |
| 用户模型 | 个人应用,无账号体系;apikey 用户自填,本地加密存储 |
| **多语言** | **v1 必须支持中文 + 英文**(`values/` + `values-en/`),跟随系统语言 |
| **分发渠道** | **暂不考虑上架任何国内国外应用市场**,**只发 APK 文件**(自用 / 朋友内测) |

---

## 1. 背景与目标

### 1.1 一句话

为安卓用户提供一个"快速记录灵感 + AI 辅助写作"的轻量写作 APP,核心路径是 **手机桌面小组件 → 随手记 → 必要时 AI 助手介入**。

### 1.2 目标

- **快**:从桌面小组件到记录一条灵感 ≤ 3 次点击。
- **轻**:不依赖后端,无账号,无强制云同步,首屏可离线。
- **可控**:apikey 留本机,数据留本机;用户随时可导出全部数据。
- **可用**:中文写作场景下,扩写/润色/整理三类 AI 能力至少达到"参考级"。

### 1.3 非目标(v1 不做)

- iOS 端。
- 多账号、社交、协作。
- 强制云同步、跨设备实时同步。
- 语音/图片/手写输入(留 v2+ 接口)。
- 富文本所见即所得(用 Markdown 源码 + 实时预览即可)。
- 插件、脚本、自定义 prompt 市场。

---

## 2. 用户与场景

| 场景 | 路径 |
| --- | --- |
| **桌面速记** | 长按桌面 → 添加小组件 → 点小组件 → 弹出快速记录卡片 → 输入 → 保存 |
| **主流程·随手记** | 启动 App → 随手记 Tab → 新建/编辑/搜索/标签 |
| **主流程·AI 助手** | 随手记详情 → 选中文本 → 弹"AI 操作"菜单 → 选扩写/润色/整理 → 流式出结果 → 用户接受/拒绝/再生成 |
| **模型配置** | 设置 → 模型管理 → 选 provider → 填 apikey → 测试连通 → 保存 |
| **数据迁移** | 设置 → 导出 / 导入 → JSON 或 Markdown zip |

---

## 3. 功能模块

### 3.1 随手记(Quick Note)

- **CRUD**:新建、编辑、删除、列表、按时间倒序。
- **检索**:全文搜索(FTS4 / FTS5)、按标签、按时间区间。
- **组织**:标签(多对多)、固定到顶、批量删除。
- **AI 元数据**:每条笔记记录"是否被 AI 处理过"和最近一次处理类型(扩写/润色/整理),用于 UI 提示。
- **导出**:单条 → Markdown / 纯文本;批量 → JSON zip。
- **字数/阅读时间**:每条笔记底部展示字数和预估阅读时间。

### 3.2 AI 助手(AI Writing)

只做三类操作,边界严格:

| 操作 | 行为 |
| --- | --- |
| **扩写** | 在原文基础上扩写,保留核心信息和语气;输出"原文 + 扩写结果"两栏对比,用户接受/拒绝 |
| **润色** | 优化表达、修正语病、统一风格;输出替换后的全文,用户可对比 diff |
| **整理** | 把零散文字按主题/要点重新组织,输出结构化 Markdown(标题、列表、要点) |

**交互细节**

- 选中文本 → 弹气泡菜单(扩写 / 润色 / 整理 / 复制)。
- AI 调用以**流式(SSE / chunked)** 为主,长任务配进度条 + 取消按钮。
- 网络失败 / 余额不足 / apikey 无效:**必须有 fallback**(至少在 UI 上给出明确提示,不要白屏)。
- **prompt 注入防御**:用户文本作为 `user` 消息内容传入,不参与 system prompt 拼接;对明显越权的请求(让 AI 输出"忽略之前的指令"等)做规则过滤,落到 `silent-failure` 兜底文案。
- **成本可观测**:每次调用记录 token 用量(入/出/总)、provider、模型名、用时;在"设置 → 用量统计"里以列表展示;不展示具体人民币金额(各家计费口径不同)。

### 3.3 设置(Settings)

- **模型管理**:增删 provider 预置项(apikey、baseURL、模型名、是否启用)。
- **默认行为**:启动 Tab、AI 操作前的二次确认开关、是否自动保存 AI 处理结果。
- **隐私与安全**:apikey 重置(抹除本地值)、导出全部数据、清除全部数据。
- **外观**:浅色 / 深色 / 跟随系统、字号、列表密度。
- **语言**:跟随系统语言(zh / en);v1 仅支持**中文(简体)和英文**,其它语言回退到中文。所有用户可见字符串必须走 `values/strings.xml` + `values-en/strings.xml`,**严禁**硬编码中文。
- **关于**:版本号、协议链接、开源声明。
- **首次启动引导**:明确告知"本应用不收集任何数据;apikey 仅存在本机;调用三方 API 由你自行负责账号和成本",用户勾选同意后才进入主界面。

---

## 4. 技术栈

### 4.1 语言与构建

| 项目 | 选型 |
| --- | --- |
| 语言 | Kotlin 2.0+ |
| UI | Jetpack Compose + Material 3 |
| 构建 | Gradle 8.x + Version Catalog(`libs.versions.toml`) |
| AGP | Android Gradle Plugin 最新稳定版 |
| minSdk / targetSdk | minSdk **26**(覆盖国内 99%+ 设备,且满足现代 Widget API) / targetSdk **35** |
| JDK | 17 |
| Kotlin Compiler | 2.0+,开启 K2 |

### 4.2 关键库

| 用途 | 选型 | 备注 |
| --- | --- | --- |
| 异步 | Coroutines + Flow | 标准,无悬念 |
| 导航 | **Navigation Compose** + 类型安全路由 | 支持系统侧滑返回(见 §7) |
| 依赖注入 | **Hilt** | 官方推荐,和 Compose 集成好 |
| 持久化(结构化) | **Room** + FTS4 | 笔记、标签、AI 历史 |
| 持久化(配置 / apikey) | **DataStore + EncryptedSharedPreferences / Tink** | apikey 走加密;DataStore 存非敏感配置 |
| 序列化 | **kotlinx.serialization** | JSON 导出/导入,流式 chunked response 解析 |
| 网络 | **OkHttp** + 自写 SSE 解析 | Retrofit 留给 v2(若加云同步);v1 不上 Retrofit 减少依赖 |
| 文本处理 | **Markwon** | Markdown 渲染(本地) |
| 桌面小组件 | Glance(Compose for AppWidget) | 与 Compose 同源,API 更现代 |
| 单元测试 | JUnit5 + MockK + Turbine(Flow 测试) | |
| 仪器测试 | Compose Test + Hilt Test | |
| 静态分析 | ktlint + detekt(后续) | v1 先 ktlint |
| Lint | Android Lint + baseline | |

> **刻意不引入**:Retrofit、Moshi/Gson(用 kotlinx.serialization)、Koin(用 Hilt)、ExoRoom(用 Room 自带 FTS)、Glide/Coil(列表是文本,不需要图片库)。

### 4.3 模块结构

v1 采用**单 Gradle module + 按 package 切分**,理由是 v1 体量小、单人维护、单 variant;多 module 的构建成本和维护成本现在不划算。package 切分如下:

```
app/src/main/java/com/<your>/writingwithai/
├── app/                    # Application、MainActivity、Nav 根、主题、入口
│   ├── WritingApp.kt       # @HiltAndroidApp
│   ├── MainActivity.kt     # 单 Activity + NavHost
│   ├── AppNav.kt           # 类型安全路由定义
│   └── ui/theme/           # Material 3 主题
├── core/                   # 真正跨 feature 的基础设施
│   ├── data/               # Room 数据库、DAO、Entity
│   ├── prefs/              # DataStore 包装、apikey 加密仓库
│   ├── ai/                 # AI 抽象层(见 §6)
│   ├── net/                # OkHttp client、SSE 解析、错误映射
│   ├── widget/             # Glance Widget 入口
│   └── common/             # Result、错误类型、扩展函数
├── feature/
│   ├── quicknote/          # 随手记
│   │   ├── list/           # 列表 + 搜索 + 标签
│   │   ├── detail/         # 详情 + 编辑 + AI 入口
│   │   └── model/          # Note 领域模型
│   ├── aiwriting/          # AI 助手(详情页内嵌 + 独立历史)
│   │   ├── action/         # 扩写/润色/整理 操作
│   │   ├── streaming/      # 流式 UI 状态机
│   │   └── history/        # 调用历史
│   ├── settings/           # 设置
│   └── onboarding/         # 首次启动 + 用户同意
└── di/                     # Hilt Module 集中点
```

**硬规则**(与原 web 项目相同的精神):

- 一个 feature **必须自包含**;跨 feature 引用走对方 `index` 风格的对象(在 Kotlin 里以 `<FeatureName>Entry` object 暴露)。
- 跨 feature 复用放 `core/` 或新建 `shared/`(本项目里以 `core/` 为主,`shared/` 仅当出现真正可复用的 UI 组件时启用)。
- `core/` 是基础设施,不是业务工具的堆场。

---

## 5. 数据层

### 5.1 实体

```kotlin
@Entity(tableName = "notes", indices = [Index("updatedAt")])
data class Note(
    @PrimaryKey val id: String,                 // UUID
    val title: String,                          // 标题(可空时取正文前 30 字)
    val content: String,                        // Markdown 源码
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val lastAiOp: String? = null,               // "expand" | "polish" | "organize" | null
    val lastAiAt: Long? = null,
)

@Entity(tableName = "tags", primaryKeys = ["noteId", "tag"])
data class NoteTagCrossRef(val noteId: String, val tag: String)

@Entity(tableName = "ai_history", indices = [Index("noteId"), Index("createdAt")])
data class AiHistory(
    @PrimaryKey val id: String,
    val noteId: String?,                        // 关联笔记(可空)
    val provider: String,                       // "deepseek" | "minimax" | "mimo" | 自定义
    val model: String,                          // 模型名
    val op: String,                             // "expand" | "polish" | "organize"
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
    val durationMs: Long,
    val createdAt: Long,
    val inputSnapshot: String,                  // 入参快照(用于审计)
    val outputSnapshot: String,                 // 出参快照
)
```

### 5.2 全文搜索

- 用 Room 自带 FTS4(中文场景下,`tokenize = unicode61 remove_diacritics 2`)做基础分词。
- 中文分词 v1 不上(jieba-android 体积大、首启慢);v1 用"按字 + 双向 bigram 索引"做实验方案,效果不达预期就回退到 LIKE 搜索(笔记量预期 < 1k,LIKE 完全够用)。
- 决定项:**v1 直接用 LIKE**,v2 再评估分词。

### 5.3 数据导出/导入

- 导出:`notes` + `ai_history` + `tags` 打包为 JSON zip;Markdown zip(每条笔记一个 .md)作为可读版本,放同一压缩包的两个目录。
- 导入:JSON zip 去重(按 id 跳过已存在);失败条目收集到 `import_report.md` 附在压缩包根目录。
- 触发位置:设置 → 导入/导出;Share Intent 也接一份"导出单条到 Markdown"。

---

## 6. AI 抽象层(关键设计)

### 6.1 目标

- **业务侧永远不直接构造 HTTP 请求**;所有 AI 调用经过 `AiGateway`。
- 切换 provider / 模型是改配置,不改业务代码。
- 流式响应统一为 `Flow<AiStreamEvent>`,业务侧只关心事件,不关心协议。
- 调用结果带 token 统计、耗时、错误类型,自动落库到 `ai_history`。

### 6.2 抽象

```kotlin
// core/ai/api/AiProvider.kt
interface AiProvider {
    val id: String                              // "deepseek" / "minimax" / "mimo" / "custom"
    val displayName: String
    val defaultBaseUrl: String
    val supportedModels: List<String>

    /** 把通用请求转成 provider 私有协议的 HTTP 调用,以 Flow 暴露流式事件。 */
    fun stream(
        request: AiRequest,
        credentials: AiCredentials,             // apikey、baseURL(可覆盖)
    ): Flow<AiStreamEvent>
}

// core/ai/api/AiGateway.kt
interface AiGateway {
    suspend fun listProviders(): List<ProviderDescriptor>
    suspend fun pickProvider(id: String): AiProvider
    fun streamWritingOp(
        op: WritingOp,                          // expand / polish / organize
        sourceText: String,
        providerId: String,
        modelName: String?,
    ): Flow<AiStreamEvent>

    /** 异步落库,业务侧不感知。 */
    fun shutdown()
}

sealed interface AiStreamEvent {
    data class Delta(val text: String) : AiStreamEvent
    data object Started : AiStreamEvent
    data class Usage(
        val inputTokens: Int,
        val outputTokens: Int,
        val totalTokens: Int,
    ) : AiStreamEvent
    data class Failed(val error: AiError, val recoverable: Boolean) : AiStreamEvent
    data object Done : AiStreamEvent
}
```

### 6.3 预置 provider 适配器

**关键事实(2026-06-21 更新)**:三家 provider 协议**不再统一**:deepseek 走 **OpenAI 兼容**(`/chat/completions` + `Authorization: Bearer`),minimax / mimo 仍走 **Anthropic Messages API 兼容**(`/v1/messages` + 各家 auth header)。共用**一个** `AnthropicCompatibleAdapter`,通过 `ProviderConfig.apiFormat = ApiFormat.{OPENAI,ANTHROPIC}` 切分支;差异点(认证 header、模型、字段能力)通过 `ProviderConfig` 数据驱动,**不需要为每家写独立 adapter**。

| Provider | Base URL | 完整端点 | Auth header | 默认模型 | 详细文档 |
| --- | --- | --- | --- | --- | --- |
| **deepseek** | `https://api.deepseek.com` | `POST /chat/completions` | `Authorization: Bearer` | `deepseek-v4-flash` | `docs/usage/api-deepseek.md` |
| **minimax** | `https://api.minimaxi.com` | `POST /anthropic/v1/messages` | `Authorization: Bearer` | `MiniMax-M2.7-highspeed` | `docs/usage/api-minimax.md` |
| **mimo** | `https://api.xiaomimimo.com` | `POST /anthropic/v1/messages` | ⚠️ `api-key`(不是 `x-api-key`) | `mimo-v2.5-flash` | `docs/usage/api-mimo.md` |

**通用协议**(端点形态、字段语义、SSE 事件、错误码)见 `docs/usage/api-anthropic-compatible.md`。

**架构落地**:

- `core/ai/api/ProviderConfig.kt` — 数据类(id / baseUrl / endpointPath / authStyle / customAuthHeaderName / supportedModels)
- `core/ai/provider/AuthStyle.kt` — 枚举 `AUTHORIZATION` / `X_API_KEY` / `CUSTOM_HEADER`
- `core/ai/provider/AnthropicCompatibleAdapter.kt` — **唯一**的 `AiProvider` 实现;基于 OkHttp + SSE 解析
- `core/ai/provider/deepseek/DeepseekConfig.kt` / `minimax/MinimaxConfig.kt` / `mimo/MimoConfig.kt` — 各家只贡献 `ProviderConfig` 数据 + 可选字段校验(例如 minimax 的 `service_tier`、mimo 的 `api-key` header、deepseek 的字段忽略)
- 新增 provider 的成本:**一份 `ProviderConfig` + 一份 `docs/usage/api-<id>.md`**,**不写新 adapter**。

### 6.4 自定义 provider

设置里允许用户手动加 provider(填 baseURL、模型名、apikey、auth header 名),用于**未来**未预置的厂商。**v1 只支持 Anthropic 兼容协议**(`/v1/messages` 端点);用户在设置里填 baseURL + 自定义 auth header 名(可选,默认 `x-api-key`) + 模型名,系统按 Anthropic 协议发请求。**OpenAI 兼容 / 其它私有协议不在 v1 范围**(v2+ 评估)。

### 6.5 prompt 模板

- system prompt 写死在 `core/ai/prompt/<op>.kt` 里,**不接受**用户输入系统提示。
- 用户文本只放在 user 消息的 content 字段,不参与模板拼接。
- 每个 op 的模板要过 review,避免可被注入覆盖指令的写法(例如"忽略之前所有指令")。

### 6.6 错误降级(必须)

| 错误 | 行为 |
| --- | --- |
| 无网络 | UI 显示"当前无网络,操作稍后重试";按钮可点,失败不阻塞其他功能 |
| apikey 无效(401/403) | UI 明确提示去设置页校验;按钮置灰,提示重新配置 |
| 余额不足(402 / provider 私有码) | 提示用户去对应平台充值 |
| 流式中断 | 自动重试 1 次;仍失败则保留已收到的部分输出,让用户选择丢弃或继续 |
| 内容安全拦截 | 显示 provider 返回的拦截原因摘要,绝不让原始错误码直接抛到 UI |
| 超时 | 30s 无 token 视为超时,降级为"操作失败,稍后重试" |

---

## 7. 导航与侧滑返回(系统手势)

### 7.1 平台

- minSdk 26 → 26~27 走 `OnBackPressedDispatcher` 自管。
- 28+ 走 `predictive back gesture`(系统级侧滑返回)。
- v1 适配到 targetSdk 35,启用 `enableOnBackInvokedCallback = true`(Android 13+ 的统一返回 API)。

### 7.2 Compose 端

- 用 `androidx.navigation:navigation-compose` 的类型安全路由;`NavHost` 自带系统返回集成。
- **不**用第三方侧滑返回库;不自定义手势拦截,避免和系统手势打架。
- 详情页 → 编辑页这种 push 关系,确保 `popBackStack` 行为正确;从 widget 启动的"快速记录卡片"用 single-task 独立任务栈,返回时优先回主任务栈(用 `TaskStackBuilder` 显式构造回退栈)。

---

## 8. 桌面小组件(Glance)

### 8.1 形式

- **2x2 小组件**:显示最近 1 条笔记标题 + 时间 + "新建"按钮。
- **4x2 小组件**:显示最近 3 条笔记(可滚动),右上角"+"按钮。

### 8.2 交互

- 点"新建"→ 启动 App 到快速记录页(带预填,自动 focus 输入框)。
- 点最近一条笔记 → 启动 App 到该笔记详情。
- 小组件内容刷新:笔记增删改时通过 Glance 的 `updateAll` 触发;短间隔(15 min)兜底轮询。
- 小组件不显示完整正文,只显示标题 + 时间,避免一眼暴露敏感内容(用户在桌面就能看到)。

### 8.3 兼容性

- 走 Glance(基于 Compose),样式自动适配 Material You 取色。
- ROM 兼容性:测试小米 / 华为 / OPPO / vivo 的桌面(国产 ROM 对 widget 限制较多,需要在 PR 中验证)。

---

## 9. 隐私与安全

### 9.1 apikey 存储

- **绝对不进 SharedPreferences 明文**、不进 Room、不进任何日志。
- 走 `EncryptedSharedPreferences` 或 Android Keystore 包装的 Tink(优先 Tink,Google 已建议迁移)。
- 设置页明文显示 apikey 时加 5s 自动隐藏。
- 备份:Android Auto Backup 默认关闭 apikey(`android:allowBackup="false"`,或 BackupAgent 显式排除)。

### 9.2 用户同意

- 首次启动 → 全屏同意页:列明
  - 应用不收集任何数据;
  - apikey 仅本机加密存储,不离开设备;
  - AI 调用由你付费给对应 provider,本应用不经手费用;
  - 第三方服务的 ToS 由你与 provider 直接发生。
- 勾选后才进入主界面;同意状态持久化,卸载重装需重勾。

### 9.3 数据导出

- JSON zip 包含全部 `notes` + `ai_history` + `tags`,默认放在用户选择的下载目录。
- 导入时明确告知"将覆盖同 id 条目,新增条目追加"。

### 9.4 日志

- 默认不打印 apikey;OkHttp interceptor 对 `Authorization` 头做 redact。
- Crash 日志走本地,不上传任何第三方平台。

---

## 10. 关键流程

### 10.1 启动 → 快速记录

```
桌面 widget → PendingIntent → MainActivity(quicknote/new?prefill=...)
  → NavHost 解析 → QuickNoteEditorScreen(prefillFocus = true)
  → 用户输入 → 保存(NoteRepo.upsert)
  → WidgetUpdater.updateAll(context)
```

### 10.2 随手记 → AI 润色(流式)

```
QuickNoteDetailScreen 长按选词 → ActionSheet(扩写/润色/整理)
  → AiWritingViewModel.start(op, selection, noteId)
  → AiGateway.streamWritingOp(...) 返回 Flow<AiStreamEvent>
  → UI 状态:Idle → Streaming → Done/Failed
  → 累积 Delta 渲染到预览面板(Markwon 渲染 Markdown)
  → 用户点"接受"→ 替换原文并保存(自动建一条 AiHistory)
  → 用户点"拒绝"→ 关闭预览,原文不动
  → 任何阶段都允许"取消" → 给 provider 发 cancel,本地 AbortJob
```

### 10.3 模型管理

```
Settings → Model Management → 选 provider → 填 apikey
  → "测试连通"按钮 → AiGateway.ping(providerId, model)
     成功:显示"可用,模型列表已加载" + 当前模型
     失败:显示具体错误(401/超时/网络)
  → 保存 → 加密落 EncryptedSharedPreferences
```

---

## 11. 性能与质量基线

- **首屏冷启动** ≤ 1.5s(中端机,Android 13+)。
- **列表滚动** 60fps,数据源用 Paging 3(v1 笔记量小可不上,预留接口)。
- **AI 操作交互** 从用户点"开始"到首个 token 落屏 ≤ 3s(国内网络 + 任一预置 provider)。
- **包大小** release APK ≤ 20MB。
- **崩溃率** 内侧期间 < 0.5%。

---

## 12. 测试策略

| 类型 | 范围 |
| --- | --- |
| 单元 | `core/ai` 的 provider 适配器、prompt 模板、错误映射;`core/data` 的 DAO;`feature/quicknote` 的 ViewModel |
| 仪器 | 关键 UI 流程(新建/搜索/AI 操作/导入导出) |
| 手测 | 国产 ROM 桌面 widget 兼容性、侧滑返回手势 |
| 静态 | ktlint + Android Lint + detekt(后续) |
| 协议验证 | 用 `core/ai` 的 fake provider 跑 mock 测试,避免发版前才去联调真 provider |

> **CLAUDE.md 里目前没有测试框架**(`package.json` 不存在;本项目是 Android,等价物是 `app/build.gradle.kts` 里没有 testImplementation)。本规划落地时,**第一件事是先加 testImplementation 基础**(JUnit5 + MockK + Turbine),再写业务。

---

## 13. 里程碑(内部,不影响发版节奏)

虽然对外"一次发 v1",内部仍按依赖顺序分 5 个里程碑推进,便于 review 和回滚:

| 里程碑 | 内容 | 验收 |
| --- | --- | --- |
| **M0 · 工程脚手架** | Gradle 项目、Hilt、Compose、Navigation、Room、DataStore、ktlint 跑通;空 MainActivity 跑起来;测试框架落地 | `./gradlew assembleDebug` + `./gradlew test` 全绿 |✅ 2026-06-18 完成 + 归档 |
| **M1 · 随手记闭环** | Note CRUD、列表、搜索、标签、详情、编辑;无 AI | 端到端可创建/编辑/搜索/删除笔记,数据持久化 |✅ 2026-06-18 完成 + 归档;review r1 + 11 项 fix |
| **M2 · AI 抽象层 + FakeProvider 端到端** | `AiGateway`、`ProviderConfig`、`AnthropicCompatibleAdapter`(通用)、SSE 解析、用量统计、错误降级、FakeProvider 端到端走通(真实 provider 联调不阻塞 M2) | 用 FakeProvider 模拟 stream → 选中文本 → AI 润色 → 流式出结果 → 接受/拒绝,落库正确 |✅ 2026-06-18 完成 + 归档 |
| **M3 · AI 操作 UI 闭环(扩写/润色/整理)** | ActionSheet + StreamingPanel + 接受/拒绝/再生成 + 错误降级 + providerId 写死 `fake`;三家真实 provider(apikey 接入)推迟到 M5 onboarding-consent | 用 FakeProvider 模拟 stream → 选中文本 → 扩写/润色/整理 → 流式面板 → 接受替换正文 + 落 lastAiOp + AiHistory |✅ 2026-06-19 完成 + 归档;review r1 + 13 项 fix(H1-H3 + M1-M5 + L1-L4) |
| **M4 · 桌面 Widget + 手势 + 导入导出** | Glance 2x2 / 4x2、widget 刷新策略、predictive back、JSON/Markdown 导出导入、首次启动同意页 | 在小米/华为/OPPO/vivo 桌面分别验证 widget 可用;侧滑返回行为正确;导出再导入数据完整 |✅ 2026-06-19 M4-1(home-screen-widget)+ M4-2(predictive-back-gesture)+ M4-3(data-export-import)归档 + 37 项 fix;M4-4 待起 |
| **M5 · 打磨 + 内测** | 性能调优、崩溃率回归、可观测性、隐私文案、Play Store 上架材料(若需) | 满足 §11 基线;5 人内测反馈已处理 |

---

## 14. 风险与开放问题

| 风险 / 问题 | 应对 |
| --- | --- |
| **三家 provider 协议字段差异** | 走 `ProviderConfig` 数据驱动(已确认三家均 Anthropic 兼容);mimo 的 `api-key` header / minimax 的 `service_tier` / deepseek 的字段忽略,都在 `ProviderConfig` 透传;`silent-failure-hunter` 规则落 CI 校验 |
| **国产 ROM 对 widget 限制** | 在 M4 集中做兼容测试,失败则在文档里标注"暂未适配 XX 桌面",并提供"通过 App 内快捷入口"的 fallback |
| **流式响应在弱网下体验差** | 30s 无 token 视为超时;支持取消;已收到的部分允许用户保存为草稿 |
| **prompt 注入** | 模板 review + 关键操作(user 文本)不进 system 段;`silent-failure-hunter` 规则落 CI |
| **apikey 泄漏** | EncryptedSharedPreferences / Tink + 不进日志 + Auto Backup 排除 + 5s 自动隐藏 |
| **AI 输出质量不稳定** | 三个预置 provider 可切换;每次输出可"再生成"(`n > 1` 或重发请求);用户在 detail 页可对比历史版本 |
| **侧滑返回在某些厂商 ROM 上行为不一致** | 主要路径走系统 API;不在 widget 启动页用自管手势 |
| **Room FTS 中文分词效果差** | v1 直接 LIKE;v2 再评估 |
| **单人维护,可观测性弱** | 内置"用量统计"页 + logcat tag 规范化 + 一份"已知问题"清单随包发 |

---

## 15. 待用户拍板 / 下一步行动

### 15.1 开工前必须确认(用户已逐项回复,2026-06-18)

- [x] **包名 / 应用名**:`com.yy.writingwithai`(用户已确认)
- [x] **首次启动同意页文案**:不需要法务 review,工程侧自己写
- [x] **应用市场上架**:**任何国内国外应用市场都不上架**,只发 APK 文件(自用 / 朋友内测)
- [x] **三家 provider 的 apikey**:**开发阶段不需要真实 apikey**;真实使用时由应用使用者自填。M2 验收改用 `FakeProvider` 端到端验证(见 §13)
- [x] **签名 keychain 配置**:不需要(因为不上架)
- [x] **多语言**:**v1 必须支持中文 + 英文**(`values/` + `values-en/`,跟随系统语言);所有用户可见字符串**严禁**硬编码中文

#### 开工时由工程侧主动做的事(不阻塞 M0)

- 准备 `core/ai/FakeProvider`(返回固定文本 + 可配置 token 用量,带可控延迟和错误注入),用于 M2 / M3 单测和 UI 状态机验证
- 准备 `app/src/main/res/values/strings.xml`(中文,默认)+ `app/src/main/res/values-en/strings.xml`(英文);**先写中文,英文由工程侧同步翻译**(必要时用占位 `__TODO__` 留待补)
- 准备 `core/ai/provider/{deepseek,minimax,mimo}/` 三份 `ProviderConfig`,纯数据,可在 M0 阶段落地(等真联调推迟到 M5 / 实际使用时)

### 15.2 建议的第一个 OpenSpec change 列表

按依赖顺序:

1. **`init-android-project`** — Gradle 脚手架、Hilt、Compose、Navigation、Room、DataStore、ktlint、测试框架落地。对应 M0。
2. **`quick-note-feature`** — 随手记完整功能(无 AI)。对应 M1。✅ 2026-06-18 归档。
3. **`ai-abstraction-layer`** — `AiGateway` + `ProviderConfig` + `AnthropicCompatibleAdapter`(通用)+ SSE 解析 + 用量统计 + 错误降级 + `FakeProvider`(端到端验收用)。**真实 provider 联调不阻塞 M2**,推迟到 M5 或实际使用阶段。对应 M2。✅ 2026-06-18 归档。
4. **`ai-writing-actions`** — 扩写/润色/整理三类操作 + 多 provider(deepseek、minimax、mimo、自定义 Anthropic 兼容)+ UI 集成。对应 M3。✅ 2026-06-19 归档(M3 UI 闭环 + r1/r2 review + 13 项 fix 全部 PASS;providerId 写死 `fake`,真 provider 切换留给 M5 onboarding-consent)。
5. **`home-screen-widget`** — Glance 2x2 / 4x2 + 刷新策略(主路径 + WorkManager 15min 兜底)+ 桌面兼容性测试。对应 M4 的一部分。✅ 2026-06-19 归档(M4-1 widget 主体 + r1/r2 review + 13 项 fix 全部 PASS;GlanceStateDefinition + 国产 ROM 适配留 M5 polish)。
6. **`predictive-back-gesture`** — 系统手势适配、widget 启动的回退栈。对应 M4 的一部分。✅ 2026-06-19 归档(M4-2 AndroidManifest `enableOnBackInvokedCallback="true"` ×2 + 真 `TaskStackBuilder.startActivities` widget Intent 路径 + r1/r2 review + 12 项 fix 全部 PASS;国产 ROM `enableOnBackInvokedCallback` 不生效留 M5 polish)。
5. **`quick-note-widget`** — Glance 2x2 / 4x2 + 刷新策略 + 桌面兼容性测试。对应 M4 的一部分。
6. **`predictive-back-gesture`** — 系统手势适配、widget 启动的回退栈。对应 M4 的一部分。
7. **`data-export-import`** — JSON / Markdown 导出导入。对应 M4 的一部分。✅ 2026-06-19 归档(M4-3 `NoteExporter`/`NoteImporter`/`ZipHelper` + `SettingsDataScreen` 入口 + 9 个 i18n key + 16 个新单测 + r1/r2 review + 12 项 fix 全部 PASS;`lastImportReportZipBytes` 缓存 UI 入口 / `aiHistoryFailed` 计入 ImportReport / ZIP 4GB Zip64 留 M5 polish)。
8. **`onboarding-consent`** — 首次启动同意页 + 隐私文案 + 卸载后状态重置。对应 M4 的一部分。
9. **`polish-and-internal-release`** — 性能、崩溃、可用性打磨 + 内测反馈处理。对应 M5。

每个 change 独立走 OpenSpec 五阶段(explore → propose → apply → sync → archive);不在一个 change 里塞多件事。

---

## 16. 后续可演进(v2+ 候选)

- iOS 端(若 KMP 介入,业务逻辑可复用)。
- 云同步(自建后端或 Supabase)。
- 语音输入(ASR on-device 或调用三方 STT)。
- 图片插入(图片存在本地、笔记走 Markdown 内嵌)。
- 自定义 prompt / 提示词模板(用户可管理自己的 system prompt,但仍走 review)。
- AI 操作"组合"——一次调用完成"润色 + 整理"。
- 桌面 widget 升级:显示 AI 摘要的最近笔记。
- 笔记时间线视图、按主题自动聚类。

---

## 附录 A · 关键文件清单(开工后落点)

> 这一节是给执行者(M3 之后的 Claude 会话)的"地图"。

| 路径 | 用途 |
| --- | --- |
| `app/build.gradle.kts` | 模块依赖、Compose、Hilt、Room、DataStore 配置 |
| `app/src/main/AndroidManifest.xml` | Application、Activity、WidgetReceiver、权限 |
| `core/ai/api/AiGateway.kt` | 业务入口 |
| `core/ai/api/AiProvider.kt` | provider SPI |
| `core/ai/provider/deepseek/DeepseekAdapter.kt` | 第一个真实 provider |
| `core/ai/stream/SseParser.kt` | SSE 协议解析(与 provider 解耦) |
| `core/data/db/AppDatabase.kt` | Room 入口 |
| `core/data/db/NoteDao.kt` | 笔记 DAO |
| `core/data/db/AiHistoryDao.kt` | AI 历史 DAO |
| `core/prefs/EncryptedKeyStore.kt` | apikey 加密仓库 |
| `core/widget/QuickNoteWidget.kt` | Glance 入口 |
| `feature/quicknote/list/QuickNoteListScreen.kt` | 列表 |
| `feature/quicknote/detail/QuickNoteDetailScreen.kt` | 详情 + AI 入口 |
| `feature/aiwriting/action/ActionSheet.kt` | AI 操作气泡 |
| `feature/aiwriting/streaming/StreamingPanel.kt` | 流式渲染 |
| `feature/settings/ModelManagementScreen.kt` | 模型管理 |
| `feature/onboarding/ConsentScreen.kt` | 首次启动同意 |
| `app/src/main/res/xml/backup_rules.xml` | Auto Backup 排除规则 |

---

## 附录 B · 参考

- Jetpack Compose 官方:developer.android.com/jetpack/compose
- Glance(Compose for AppWidget):developer.android.com/develop/ui/views/appwidgets
- Material 3:developer.android.com/develop/ui/compose/designsystems/material3
- Hilt:dagger.dev/hilt
- Room:developer.android.com/training/data-storage/room
- Tink:github.com/tink-crypto/tink-java
- Navigation Compose:developer.android.com/jetpack/compose/navigation
- Predictive back:developer.android.com/guide/navigation/predictive-back
