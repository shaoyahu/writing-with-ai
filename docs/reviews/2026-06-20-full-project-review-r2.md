# Code Review: 全项目 review r2(本次 session 全量扫描)

**Reviewed**: 2026-06-20
**Branch**: main(本地未提交 + 大量 untracked)
**Decision**: **REQUEST CHANGES**(2 CRITICAL 阻断构建 + AI 网关失能)

## Summary

r1 仅覆盖 session 内 3 change 的局部改动;r2 全量扫描发现 **2 项 CRITICAL**:`QuickNote1x4Widget.kt` 引用不存在的 Glance API(build 失败) + `CoreAiGateway.kt` 硬编码 `apikey = "fake-apikey"` 导致真实 provider 调用全 401(provider-real-integration 名存实亡)。`./gradlew :app:assembleDebug` 与 `:app:ktlintCheck` **均失败**,ktlint 共 ~580 处违规(主源 ~477，测试源 ~109)，需先恢复 green 再议提交。

## Delta from r1

| r1 finding | r2 status |
|---|---|
| M1 widget color tokens 重复 | 未修(实际已隐式修，见 L5) |
| M2 无用 `size` / `width` import | 未修 |
| L1 KDoc 仍写 `SizeMode.Exact` | 未修 |
| L2 `cSurface` 只在 2x2 用 | 未修 |

r1 给的 2 项 MEDIUM + 1 项 LOW 均未处理。

## Findings

### CRITICAL

#### C1 — `QuickNote1x4Widget.kt` 引用不存在的 Glance API,build 失败

- 位置:`app/src/main/java/com/yy/writingwithai/core/widget/QuickNote1x4Widget.kt:25,61,89,92`
- 错误:
  - `Unresolved reference 'RoundedCornerRadius'`(行 25 / 61 / 92)
  - `Unresolved reference 'fillMaxHeight'`(行 89)
  - `Argument type mismatch: actual type is ColorProvider, but ImageProvider was expected`(行 61 — `background()` 第二参)
- 现象:`./gradlew :app:assembleDebug` 在 `compileDebugKotlin` 阶段直接失败。`widget-1x4-compact` change 落地后没真编过。
- 修复方向:
  - Glance 当前版本(查 `libs.versions.toml` 的 `androidx.glance.appwidget`)实际无 `RoundedCornerRadius` 数据类;圆角走 `Modifier.cornerRadius(radius: Dp)` 链式 modifier;`background()` 重载只接 `ColorProvider`，无第二参。
  - `fillMaxHeight` Glance 也没有，用 `defaultWeight()` + `fillMaxSize()` 组合或外层 `Row.verticalAlignment = Alignment.CenterVertically` 配合固定 `height(...)`。
  - 先在本机跑 `find ~/.gradle/caches -name "glance-appwidget-*.jar" -exec unzip -l {} \| grep -i "RoundedCorner\|fillMaxHeight" \;` 确认实际 API 形态，再改实现。
- 影响:active change (`openspec/changes/widget-1x4-compact/`)未真正能产出 APK。

#### C2 — `CoreAiGateway.kt` 硬编码 `apikey = "fake-apikey"`，真实 provider 调用全 401

- 位置:`app/src/main/java/com/yy/writingwithai/core/ai/CoreAiGateway.kt:64,115`
- 证据:
  ```kotlin
  val credentials = AiCredentials(apikey = "fake-apikey")
  ...
  return provider.stream(request, credentials)
  ```
- 链路:
  1. `AiActionViewModel.start()` 读 `ProviderPrefsStore` + `SecureApiKeyStore.has(selectedId)` 判 `ProviderNotConfigured` → 看起来 gate 工作。
  2. 但调 `aiGateway.streamWritingOp(providerId = "deepseek", ...)` 时，**`AiGateway` 签名没有 apikey 参数**,gateway 内部直接 `AiCredentials(apikey = "fake-apikey")` 写死传给 provider。
  3. `AnthropicCompatibleAdapter.addAuthHeaders()` 用 `credentials.apikey` → 发到 deepseek/minimax/mimo 都是字符串 `"fake-apikey"` → 服务端 401/403。
  4. `SecureApiKeyStore` 存的真 apikey 永远到不了网络。用户在设置 → 模型管理配的 apikey 形同虚设。
- 证据 2:`ping()` 行 115 同样硬编码。
- 证据 3:Adapter 注释 `M2 用 fake,M4 从 EncryptedSharedPreferences 读真 apikey`(`AiCredentials.kt:3`) 明确说应该读真 key，实际未实现。
- 证据 4:测试 `AiActionViewModelTest.kt` / `AiActionViewModelConsentTest.kt` 全是 stub gateway，未覆盖真实 provider → 没测出。
- 修复方向:
  1. `AiGateway.streamWritingOp` 加 `apikey: String` 参数(从 `SecureApiKeyStore` 在 caller 拿，避免 gateway 反向依赖 prefs)。
  2. `AiActionViewModel.start()` 在 `resolveProviderId()` 同步 `secureApiKeyStore.get(providerId)`，判 `null` → `ProviderNotConfigured` Failed;非 null 传进 `streamWritingOp`。
  3. `CoreAiGateway.ping()` 同样读 `SecureApiKeyStore`。
  4. 加 `ProviderPrefsStore + SecureApiKeyStore` 联合的端到端测试(MockWebServer + 真 apikey header 校验)。

### HIGH

#### H1 — `./gradlew :app:assembleDebug` 失败(C1 的后果，重复列)

- 阶段:`compileDebugKotlin`
- 错误同 C1。修完 C1 这条自动消失。

#### H2 — `./gradlew :app:ktlintCheck` 失败，~580 处违规

- 主源:477(14 类规则，top 5:`indent` 183、`trailing-comma-on-call-site` 142、`argument-list-wrapping` 34、`function-signature` 30、`trailing-comma-on-declaration-site` 23)
- 测试源:109(`argument-list-wrapping` 60 占多数)
- 高频违规文件:
  - `app/ui/theme/Type.kt` — 整文件 indent 12 vs 8(M5 polish 改完未格式化)
  - `app/ui/theme/Color.kt` — 缺末尾换行 + trailing comma
  - `core/widget/QuickNoteWidget.kt` — 9 个 unused imports,`multiline-if-else` 缺 `{}`
  - `core/widget/QuickNote1x4Widget.kt` — import order + statement-wrapping
  - `core/prefs/PrefsModule.kt` — `function-signature` 多处
  - `core/ai/provider/ProviderPrefsStore.kt` — `max-line-length` 120 超(line 35 的 `preferencesDataStore(name = ...)`)
  - `feature/aiwriting/streaming/AiActionViewModel.kt` — 17 处 indent 不对
  - `feature/aiwriting/action/ActionSheet.kt` — `statement-wrapping` / `no-multi-spaces` 反复
  - `feature/aiwriting/streaming/AiActionViewModelTest.kt` — `argument-list-wrapping` 60 处(每行多参数，ktlint 要求单行)
  - `feature/aiwriting/AiActionViewModelConsentTest.kt` — `function-signature` 多处
- CLAUDE.md §"约定" / §"命令" 把 `ktlintCheck` 列为阻断门，目前绿不了。
- 修复:跑 `./gradlew :app:ktlintFormat` 一把梭，再 `ktlintCheck` 验证;若 format 也卡住，剩 `Type.kt` 这种大块 indent 错，手工改一遍。

#### H3 — `.editorconfig` 含 obsolete `ktlint_disabled_rules` 属性

- 位置:`config/ktlint/.editorconfig` + 项目根 `.editorconfig`
- 现象:ktlint 1.0 rule-engine 不认 `disabledRules` 旧 SetProperty 写法，反而在每次启动打 18+ 行 warning。
- 内存里已有 `ktlint-compose-pascalcase-1.0` 备忘，目前项目仍带 obsolete key 没清。
- 修复:删 `ktlint_disabled_rules = ...` 行，改用 ktlint 1.0 per-rule 格式(`[*.{kt,kts}]\nktlint_standard_function-naming = disabled`)或在 `app/build.gradle.kts` 的 `ktlint {}` 块集中管。

### MEDIUM

#### M1 — `AiActionViewModel` 收到 `Failed` 时也写 `lastUsage = event` 是为 null，顺手审计

- `streaming/AiActionViewModel.kt:140` `is AiStreamEvent.Usage -> lastUsage = event` 在 Failed 前 OK，逻辑对。
- 跳过 — 无问题。

#### M2 — `AiModule` 3 个真 provider 在 App 启动时全部 eager 构造

- 位置:`core/ai/di/AiModule.kt:38-55`
- 现象:`@Provides @Singleton` 加 3 个 `@Named("deepseek" | "minimax" | "mimo")` 都在 `SingletonComponent` 初始化，等于启动时全部 new 一次 `AnthropicCompatibleAdapter`(其内 `Json {}` 初始化)。用户若只用 deepseek，其他两个白做。
- 影响:启动 +1~2ms 内存。极小。
- 修复:用 `dagger.Lazy<>` 或 `Provider<>` 包装，真正调用才解析。LOW 边界，推迟。

#### M3 — `NoteImporter.kt:171` 行数偏大

- 倒数第 2 大文件，可能含未拆分子函数。spot-check 略。
- 修复:读后看是否能抽 `parseImportStream()` 之类。本次不展开。

#### M4 — `QuickNoteDetailScreen.kt:60` `unused import` 触发 ktlint

- 已在 H2 列举。修 H2 顺带消。

### LOW

#### L1 — `release.isMinifyEnabled = false`

- `app/build.gradle.kts:40` 注释说"配 release 签名 / ProGuard 规则;M0 不开 minify"。M5 polish 阶段仍 false，生产构建会缺混淆 + R8 优化。
- 修复:发版前开 `isMinifyEnabled = true` + 配 `proguard-rules.pro`(Hilt/Room/Compose/Glance/OkHttp/Kotlinx Serialization keep rules)。

#### L2 — `CoreAiGateway.kt:64/115` 硬编码 string `"fake-apikey"` 应改 const

- 就算 gateway 暂不读真 apikey，这个字面量也应提到 `companion object { private const val PLACEHOLDER_KEY = "fake-apikey" }`，而不是散落 2 处。
- 与 C2 一并修。

#### L3 — `WritingApp.onCreate` 同步 `runBlocking` 写 DataStore

- `app/WritingApp.kt:48` `runBlocking { consentStore.setAccepted(...) }` 阻塞主线程 1 次。在 `CONSENT_GATE_ENABLED=false` 路径才走，作为逃生口可接受。常规启动不触。
- 注释里已经说明"卸载重装即重置"+"一次性"。不修。

#### L4 — `AiError.kt:34` `summary()` 一行可被 ktlint 折叠

- 已列 H2，不重复。

#### L5 — `WidgetColorTokens` 重复定义(r1 M1 顺延)

- `QuickNoteWidget.kt:41-47` 已有 `internal val cBlue / cWhite / cBg / cTitle / cBody / cMeta` + `cp()`。
- `QuickNote1x4Widget.kt:35` 注释说"Color tokens from QuickNoteWidget.kt (same package)" → 已经共享 ✓。但 r1 提到的 `QuickNote1x4Widget.kt:61-65` 当时还在的本地 `cPrimary/cOnPrimary/cBg/cText/cTextMuted` 已被删 → r1 M1 已隐式修。
- 不重复。

### INFO

#### I1 — `app/src/test/java/.../core/ai/provider/ProviderPrefsStoreTest.kt` 等测试在没 build 过的情况下能不能跑

- 已被 H1 阻断;若 C1 修了，跑 testDebugUnitTest 验证 AiActionViewModel 全部测试。
- 现状未知(本次未跑成功)。

## Validation Results

| Check | Result | 详情 |
|---|---|---|
| `./gradlew :app:assembleDebug` | ❌ **FAIL** | `compileDebugKotlin` 失败:`QuickNote1x4Widget.kt` 4 处 unresolved(C1) |
| `./gradlew :app:ktlintCheck`(main) | ❌ **FAIL** | 477 处违规(H2) |
| `./gradlew :app:ktlintCheck`(test) | ❌ **FAIL** | 109 处违规(H2) |
| `./gradlew :app:testDebugUnitTest` | ⏸ **BLOCKED** | 因 build 失败未跑 |
| Android Lint | ⏸ **BLOCKED** | 同上 |
| Glance 约束(`grep compose.foundation\|material3` in `core/widget/`) | ✅ 0 matches | OK |
| Feature self-containment | ✅ 无跨 feature import | 仅同包(`feature.aiwriting.*` 内互引) |
| Manifest 权限边界 | ✅ `allowBackup=false` + maxSdkVersion=29 | OK |
| apikey 落点 | ✅ `EncryptedSharedPreferences` | OK |
| apikey 是否真发到网络 | ❌ **C2 阻断** | gateway 写死 `"fake-apikey"` |

## Files Reviewed

**CRITICAL/ HIGH 涉及**:
- `core/widget/QuickNote1x4Widget.kt`(C1 build break)
- `core/ai/CoreAiGateway.kt`(C2 hardcoded apikey)
- `core/ai/provider/AnthropicCompatibleAdapter.kt`(C2 链路下游，改后需配合)
- `feature/aiwriting/streaming/AiActionViewModel.kt`(C2 修复点)
- `app/build.gradle.kts`(H1, L1 release minify)
- `config/ktlint/.editorconfig` + `.editorconfig`(H3 obsolete property)
- `app/src/main/java/.../ui/theme/Type.kt`(H2 整文件 indent)
- `app/src/main/java/.../ui/theme/Color.kt`(H2)
- `core/widget/QuickNoteWidget.kt`(H2)
- `core/prefs/PrefsModule.kt`(H2)
- `core/ai/provider/ProviderPrefsStore.kt`(H2)

**安全相关(全过)**:
- `core/prefs/SecureApiKeyStore.kt` ✓ Tink AES256_GCM，文件名隔离，MasterKey 走 Builder,5s reveal timeout
- `core/prefs/PrefsModule.kt` ✓ Hilt 显式 Provides
- `core/ai/provider/ProviderPrefsStore.kt` ✓ DataStore 明文只存 provider id,apikey 走 SecureApiKeyStore
- `AndroidManifest.xml` ✓ `allowBackup=false`, `dataExtractionRules` 已 exclude secure prefs file
- `app/src/main/res/xml/data_extraction_rules.xml` ✓ forward-looking
- `app/src/main/res/xml/backup_rules.xml`(未读但 manifest 引)— 待验
- `WritingApp.onCreate` 同步写 consent 是逃生口，可接受
- `NoteRepository` widget 刷新走 NonCancellable 防 race ✓

**架构(全过)**:
- `app/AppNav.kt` ✓ 类型安全路由，consent gate 双向门，onboarding popUpTo(0) 清栈
- `app/MainActivity.kt` ✓ Hilt EntryPoint 注入，IO dispatcher 拿 consent 不 block
- `feature/*/Entry.kt` 路径 OK
- 无 cross-feature import ✓

**Hilt / DI**:
- `core/ai/di/AiModule.kt` 结构 OK,eager 3 provider 推 M2 修

**测试**:
- 22 个测试文件存在，涵盖 prefs / consent / AI / settings / widget / quicknote / onboarding / export
- **缺**:`AnthropicCompatibleAdapter` 端到端 MockWebServer 测试(没测出 C2)
- **缺**:`CoreAiGateway.streamWritingOp` 集成测试(没测出 C2)
- **缺**:`CoreAiGateway` happy path 用真 apikey header 的断言

## Required Fixes Before Commit

1. **C1** — 修 `QuickNote1x4Widget.kt` 的 Glance API:
   - 删 `RoundedCornerRadius` import + 用法，改 `Modifier.cornerRadius(16.dp)`(需查版本对应 API)
   - 删 `fillMaxHeight()`，改 `defaultWeight()` + `Modifier.height(48.dp)` 之类
   - 删 `background()` 第二参(Glance 的 `background()` 签名只接 `ColorProvider`，无圆角重载)
   - 编 `:app:assembleDebug` 验证

2. **C2** — 修 `CoreAiGateway` 真 apikey 透传:
   - `AiGateway.streamWritingOp(...)` 加 `apikey: String` 参数(避免 gateway 反向依赖 SecureApiKeyStore)
   - `CoreAiGateway.streamWritingOp` 内部用传入的 apikey 构造 `AiCredentials`，删 `apikey = "fake-apikey"` 字面量
   - `CoreAiGateway.ping()` 同样要求 caller 传 apikey，或者构造时拿 `SecureApiKeyStore.get()`(`AiModule` 加 Provider)
   - `AiActionViewModel.start()` 在 `providerPrefsStore.getSelectedProviderId()` 后 `secureApiKeyStore.get(selectedId)`,null → `ProviderNotConfigured`;非 null → 传入 `streamWritingOp`
   - 加 MockWebServer 集成测试，断言 `Authorization: Bearer <真 apikey>` 落到 HTTP header(把 `M2 用 fake,M4 从 EncryptedSharedPreferences 读真 apikey` 注释兑现)

3. **H1** — 由 C1 修复后自动过

4. **H2** — 跑 `./gradlew :app:ktlintFormat`，验 `:app:ktlintCheck`:
   - `Type.kt` 整 indent 错(12 vs 8),`ktlintFormat` 不一定全修，可能要手工看
   - `AiActionViewModelTest.kt:47,73,94,112,128,148,165` 多参数行需手工拆(ktlint format 不拆多参)
   - 完成后跑 `:app:testDebugUnitTest` 验

5. **H3** — 删 `.editorconfig` 里 `ktlint_disabled_rules = ...`，改 `[*.kt]` per-rule 写法

6. **L1** — 修 C2 同时，改 `"fake-apikey"` 字面量为 `private const val PLACEHOLDER_KEY`(若保留 placeholder 路径)

7. **L2** — release `isMinifyEnabled = true` + 配 `proguard-rules.pro`(推迟到 release-readiness change 内，本次不强制)

## 跨方向建议(交给用户决策，AI 不擅自动)

- `provider-real-integration` change 当前标注"完成"，实际 gateway 透传未实现 → 应作为"未达成 AC"重新开 `openspec/changes/provider-real-integration-revise/` 或在原 r1 review 上重新发起。
- `widget-1x4-compact` change 标注"完成"，实际编不过 → 同上，需重开。
- 这两条都属于"跨方向调整需要停"，按 CLAUDE.md 工作模式 → AI **停下告知用户**，由用户决策:
  1. 是回滚到上一个 green commit?
  2. 还是开新 change 修?
  3. 还是先在 working tree 修完再 commit?

## 总结给用户

**当前 main 不能编、不能 lint 过、不能跑测试**。两个 active change 都是"未真正完成"状态，需要用户拍板怎么处理。
