# Design: fix-m5-blockers

## Context

`main` 当前处于 broken 状态 — `widget-1x4-compact` 和 `provider-real-integration` 两个 M5 polish change 标注"完成"但实际 AC 未达成,具体:

- `core/widget/QuickNote1x4Widget.kt` 引用 Glance 库中不存在的 `RoundedCornerRadius` / `fillMaxHeight` API,`./gradlew :app:assembleDebug` 在 `compileDebugKotlin` 阶段直接失败。`openspec/changes/widget-1x4-compact/` 已 archive,留下 broken artifact。
- `core/ai/CoreAiGateway.kt:64,115` 硬编码 `AiCredentials(apikey = "fake-apikey")`,所有真实 provider(deepseek / minimax / mimo)调用实际发的是字面量 `"fake-apikey"` → 服务端必然 401/403。`SecureApiKeyStore` 存的用户真 apikey 永远到不了网络。`AiActionViewModel.start()` 的 `ProviderNotConfigured` gate 形同虚设(只校验 apikey 是否存在,不校验是否真的用上)。`openspec/changes/provider-real-integration/` 已 archive,留下 broken artifact。
- `app/src/main` 累计 477 处 ktlint 违规(主因:M5 polish 改完 `Type.kt` 整文件 indent 错 12 vs 8 + 改完没跑 ktlintFormat),`app/src/test` 累计 109 处(`AiActionViewModelTest.kt:47,73,94,112,128,148,165` 多参数单行)。
- `config/ktlint/.editorconfig` + 项目根 `.editorconfig` 仍带 obsolete `ktlint_disabled_rules` property(ktlint 1.0+ rule-engine 不认),启动打 18+ 行 warning。

CLAUDE.md §"命令" 把 `ktlintCheck` 列为基础阻断门,§"会话规则" 写"完成代码改动后等用户明确表示可以提交"。当前 working tree 不能编 / 不能 lint / 不能跑测试 → 必须恢复 green 才能继续 M5 release-readiness。

## Goals / Non-Goals

**Goals**:
- 恢复 `./gradlew :app:assembleDebug` 0 错误
- 恢复 `./gradlew :app:ktlintCheck` 0 violation(main + test)
- 恢复 `./gradlew :app:testDebugUnitTest` 全部 PASS
- 兑现 `ai-gateway` spec §"AiProvider SPI is data-driven via ProviderConfig" 的"M4 从 EncryptedSharedPreferences 读真 apikey"承诺
- 加 MockWebServer 端到端测试,真验真 apikey 落到 HTTP header

**Non-Goals**:
- 不改 `release.isMinifyEnabled`(留 `release-readiness` change)
- 不动 prompt template / 数据导入导出 / onboarding 业务逻辑
- 不重构 `AiModule` 的 eager 3 provider 构造(M2 推后再议,见 r2 M2)
- 不改 `SecureApiKeyStore` 设计(5s reveal timeout + ActivityLifecycleCallbacks 方案保留)
- 不改 `NoteRepository` / 数据层

## Decisions

### D1 — C1 Glance API 替换路径(改用 1.1+ 标准 modifier)

**选择**:
- 圆角:`Modifier.cornerRadius(16.dp)`(`androidx.glance.layout.cornerRadius` 在 Glance 1.1+ 可用;实际路径需在 `libs.versions.toml` 确认 `androidx.glance.appwidget` 版本后定 import)
- 高度:`Row` 外层 `verticalAlignment = Alignment.CenterVertically`,按钮 `Box` 用 `Modifier.defaultWeight().height(48.dp)`(Glance 无 `fillMaxHeight`,用 `defaultWeight()` 占满 + 显式 `height` 撑开)
- 背景:`background(cp(cBlue))` 单参,删第二参的 `RoundedCornerRadius`

**替代方案对比**:
- 方案 A(选):标准 1.1+ modifier 链。优点:简单、官方支持;缺点:依赖 Glance ≥ 1.1,需先验证版本。
- 方案 B(弃):用 `androidx.glance.appwidget.cornerRadius` 旧版 API。缺点:1.0 才有,可能与项目版本不符。
- 方案 C(弃):自绘圆角 Box + 颜色拼接。缺点:Glance 不支持 `drawBehind`,不可行。

**行动**:先 `find ~/.gradle/caches -name "glance-appwidget-*.jar" -exec unzip -l {} \; | grep -i "RoundedCorner\|cornerRadius\|fillMaxHeight"` 确认实际 API 形态,再写代码。

### D2 — C2 apikey 透传走 `AiGateway` 接口增参数(gateway 不依赖 prefs)

**选择**:
```kotlin
interface AiGateway {
    suspend fun listProviders(): List<ProviderDescriptor>

    fun streamWritingOp(
        op: WritingOp,
        sourceText: String,
        providerId: String,
        apikey: String,           // 新增:由 caller 传入,gateway 不再硬编码
        modelName: String?,
        systemPrompt: String? = null,
    ): Flow<AiStreamEvent>

    suspend fun ping(
        providerId: String,
        apikey: String,           // 新增
        modelName: String,
    ): Boolean
}
```

caller 侧(`AiActionViewModel.start()` / `ModelManagementViewModel.ping()`)在调 gateway 前 `secureApiKeyStore.get(providerId)`:
- `null` → emit `ProviderNotConfigured` Failed,不调 gateway
- 非 null → 传入 gateway

**替代方案对比**:
- 方案 A(选):`AiGateway` 增 `apikey` 参数。优点:接口契约清晰,gateway 单一职责;缺点:**BREAKING** — `FakeAiProvider` stub / 测试桩 / 所有 caller 改签名。
- 方案 B(弃):`CoreAiGateway` 内部注入 `SecureApiKeyStore`,自行 `get(providerId)`。优点:caller 签名不变;缺点:gateway 依赖 prefs(DI 复杂 + gateway 可测性降 + 与 CLAUDE.md §"AI 集成约定""所有 AI 调用必须经过 AiGateway,业务侧不直接构造 apikey"边界冲突 — SecureApiKeyStore 算业务设施)。
- 方案 C(弃):`AiCredentials` 由 caller 构造后传入,`AiGateway` 不变。优点:零 BREAKING;缺点:把"apikey 来源"从 gateway 内部挪到 caller 内部,`AiActionViewModel` 仍要 `secureApiKeyStore.get`,等于把硬编码挪了个位置,没解决根问题。

**理由**:`ai-gateway` spec §"AiGateway provides a single entry point" 的 "执⾏扩写/润色/整理,流式返回事件" 接口定义本就没承诺"gateway 自己管 apikey 来源",`AiCredentials` 是 `AiProvider` SPI 的入参不是 `AiGateway` 的入参 — 把 apikey 提升到 `AiGateway` 签名是表达正确的边界。

### D3 — C2 caller 同步取 apikey(避免 race / fire-and-forget)

`AiActionViewModel.start()` 流程:
1. `providerPrefsStore.getSelectedProviderId()`(已存在)
2. `secureApiKeyStore.get(providerId)`(新增,同步)
3. `null` → `_state.value = Failed(ProviderNotConfigured)`,return
4. 非 null → `aiGateway.streamWritingOp(..., apikey = apikey)`(传进去)

理由:apikey 必须在 `start()` 主流程同步取,放进 `viewModelScope.launch` 内部(已有 r1 H2 修同步 consent 的同款 pattern,不再用 `runBlocking`)。SecureApiKeyStore 内部已用 `withContext(Dispatchers.IO)`,不会 block。

### D4 — C2 测试用 MockWebServer 端到端验 header

新增 `app/src/test/java/.../core/ai/provider/AnthropicCompatibleAdapterApikeyTest.kt`:
- `MockWebServer` 启在 localhost
- 构造 `AnthropicCompatibleAdapter(ProviderConfig(id="deepseek", ...), OkHttpClient.Builder().build())`
- `adapter.stream(AiRequest(...), AiCredentials(apikey = "sk-real-test-123"))`
- 拦截 request,断言 `request.header("Authorization")` 或 `x-api-key` == `"sk-real-test-123"`

不依赖 `CoreAiGateway` / `SecureApiKeyStore` — 单元测 `AnthropicCompatibleAdapter` 本身已经断在 r2 C2 的真正根因处。

另:`FakeAiProvider` 单元测已有,无需改。

### D5 — H2 ktlint 修复:format + 手工

- 跑 `./gradlew :app:ktlintFormat` 自动修大部分
- 手工修 `app/src/main/java/.../ui/theme/Type.kt` 整文件 indent(12 → 8,可能 ktlintFormat 不全)
- 手工拆 `AiActionViewModelTest.kt:47,73,94,112,128,148,165` 多参数单行(ktlintFormat 不拆 `AiActionViewModel(...)` 这种 8 参构造调用)
- 验 `./gradlew :app:ktlintCheck` 0 violation

### D6 — H3 obsolete property 删除

- 删 `config/ktlint/.editorconfig` 里 `ktlint_disabled_rules = ...` 整行
- 删项目根 `.editorconfig` 同名 property
- 改 ktlint 1.0 per-rule 写法:`[*.{kt,kts}]\nktlint_standard_function-naming = disabled`(`function-naming` 是唯一需禁的,因 Compose PascalCase 硬冲突,见 memory `ktlint-compose-pascalcase-1.0`)
- 也可放 `app/build.gradle.kts` 的 `ktlint { disabledRules.set(setOf("standard:function-naming", "standard:multiline-expression-wrapping")) }` 集中管(已存在),.editorconfig 里只放 per-rule 写法作为保险

## Risks / Trade-offs

- [R1] D2 BREAKING 接口变更,fake / 测试桩需全更新。→ 缓解:`openspec/.../tasks.md` 列完整 caller 清单,逐个改完跑测试。
- [R2] D1 依赖 Glance ≥ 1.1,本机当前版本未知。→ 缓解:D1 行动步骤先 `find` 验,若 < 1.1 改用其他 modifier 或加 `androidx.glance:glance-appwidget:1.1.0` 显式升(版本 catalog 改动需在 `libs.versions.toml` 同步)。
- [R3] H2 手工改 `Type.kt` 整文件 indent 工作量大。→ 缓解:ksp 生成的 `app/src/main/.../ui/theme/Type.kt` 99% 是 data class property indent 错,批量改 indent 即可,无逻辑改。
- [R4] C2 修完可能影响 `provider-real-integration` change 已 archive 的 spec 描述("`AiGateway.ping(providerId, modelName)`" 签名)。→ 缓解:在 `openspec/changes/fix-m5-blockers/specs/ai-gateway/spec.md` 的 `## MODIFIED Requirements` 段显式更新签名描述,`/opsx:archive` 时 sync 合并到 `openspec/specs/ai-gateway/spec.md`。
- [R5] 真 apikey 现在真发到网络,意外触发 provider 扣费。→ 缓解:用户已在前置 consent / SettingsData → 模型管理 主动配 apikey,有心理预期;不引入自动扣费路径;`M2 修:启用 BuildConfig 以便 DataModule 用 BuildConfig.DEBUG gate fallbackToDestructiveMigration()` 同款做法(已存在),不需新增。
- [R6] D3 同步取 apikey + `withContext(Dispatchers.IO)` 在主线程 `start()` 内 await,可能 50~100ms 卡顿。→ 缓解:已存在 r1 H2 修同款 pattern,实测可接受;若后期发现真延迟,改 `flow { ... }.flowOn(Dispatchers.IO)` 异步预热。

## Migration Plan

1. **改完不直接 commit**(CLAUDE.md §"提交控制" — AI 不自动 commit),等用户说"apply"或"开始做"再走 `/opsx:apply`。
2. `/opsx:apply` 时按 `tasks.md` 顺序执行,每步跑对应验证(单元测试 / assembleDebug / ktlintCheck)。
3. 最终一次性 commit `feat(fix-m5-blockers): 修复 widget-1x4 + apikey 透传 + ktlint`,scope = `fix-m5-blockers`。
4. 提交后归档:跑 `/opsx:archive` 把本 change 移到 `openspec/changes/archive/2026-06-20-fix-m5-blockers/`,同时 sync 4 个 modified capability 的 spec 到 `openspec/specs/`。
5. 回滚:本 change 集中在 `core/widget/` + `core/ai/` + `feature/aiwriting/streaming/` + `feature/settings/model/` + `config/ktlint/` + `.editorconfig`,若发布后出问题 `git revert <commit>` 即可;`AiGateway` 签名 BREAKING 在 archive 后已 sync 到主 spec,回滚要顺带恢复 spec 描述。

## Open Questions

- OQ1:`libs.versions.toml` 里 `androidx.glance.appwidget` 当前版本是多少?需查 `gradle/libs.versions.toml` 确认 Glance ≥ 1.1,否则 D1 路径需调整。**等 apply 时 D1 任务里第一行 grep 确认**。
- OQ2:`AiActionViewModelConsentTest.kt:151,181` 是 `fun foo(  bar: String)` 这种带前后空格的 `function-signature` 违规,改时是否保留多行 / 一行?推荐改 ktlintFormat 后剩这几处人工决定。**apply 时按 ktlint 1.0 默认 `function-signature` 规则要求处理**(无空格 / 参数间单空格)。
- OQ3:`M2 design` 提到 `core/ai/api/AiGateway.kt` 是"业务侧唯一 AI 入口:不直接调 `AiProvider`,不直接调 OkHttp",现在 BREAKING 签名变更,需不需要在 `openspec/specs/ai-gateway/spec.md` 里显式补一条"apikey 由 caller 提供,gateway 不持有凭证"的 requirement?推荐补。**apply 时 D2 任务里加**。
