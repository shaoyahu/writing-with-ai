# 进度总览

> 只回答"项目从开工到现在走了多远"。具体实现查 git log,单次评审查 `docs/reviews/`,规划查 `docs/plans/`。

## 维护规则

- **时间倒序**(最近在上)。
- **记录时机**:每个 M 完成 / 关键 bug 修复 / 阶段切换 各记一条;**不**写每次 commit(那是 git log 的事)。
- **不写实现细节**:commit hash / 行号 / diff / 代码片段一律不进本文。
- **不写 review 细节**:单次评审内容查 `docs/reviews/`。
- **一条典型 1-3 行**;太长说明在写文档而不是进度。
- 新增条目写在对应日期分组的最上面(同一日期内倒序)。

---

## 2026-06-18 · M1 随手记闭环完成 + 归档

- OpenSpec change `quick-note-feature` apply + 归档完整闭环:28 个新文件 + 4 个修改(`core/data` 实体 / DAO / Repo / DI + `feature/quicknote` 三屏 + `strings.xml` 双语 + `AppNav` 三路由 + `build.gradle.kts` 加 `kotlinx-serialization` 插件/运行时)
- sync spec 到 `openspec/specs/quick-note/spec.md`(11 个 Requirement × 26 个 Scenario);archive 到 `openspec/changes/archive/2026-06-18-quick-note-feature/`
- **M1 验收**:✅ `assembleDebug` / `testDebugUnitTest` 12 tests / `lintDebug` 全绿;`app/schemas/com.yy.writingwithai.core.data.db.AppDatabase/1.json` 自动生成;⚠️ `ktlintCheck` 11 个 `standard:function-naming` 全是 Compose PascalCase,见 memory `ktlint-compose-pascalcase-1.0`
- **下一步候选**:M2 `ai-abstraction-layer`(`AiGateway` + `ProviderConfig` + `AnthropicCompatibleAdapter` + `FakeProvider`),或 M0/M1 polish follow-up

---

## 2026-06-18 · M1 review r1 + 11 项 fix 完成

- `docs/reviews/2026-06-18-quick-note-feature-code-review-r1.md` 落档(3 个并行 reviewer 整合:6 HIGH + 6 MEDIUM + 11 LOW)
- 全部修完:🔴 H1 editor VM `return@collect` 改 `.first()` + hadUserInput 防覆盖;H2 同源;H3 detail VM `requireNotNull` 改可空 NotFound;H4 search LIKE 加 `ESCAPE '\'` + Repository 端 `%`/`_`/`\` 转义;H5 share catch `ActivityNotFoundException`;🟡 M1 "404" 走 R.string;M2 `fallbackToDestructiveMigration()` 用 `BuildConfig.DEBUG` gate;M3 `observeAllTags` 提升外层 combine;M4 `TITLE_FALLBACK_LEN` 提升到 `Note.Companion`;M5 删 `TagRepository.kt`(dead code);M6 delete 用 `withContext(NonCancellable)`
- 删 2 文件:`TagRepository.kt` / `RepositoryModule.kt`(空 placeholder)
- 验收:`assembleDebug` / `testDebugUnitTest` 12 tests 全绿;`ktlintCheck` 仍 11 个 Compose PascalCase = 已知 M0 follow-up,本次未引入新违规
- **H6 提醒**(不在 fix 范围):`app/schemas/.../1.json` 仍 untracked,commit 前需手动 `git add -f`
- **下一步**:开 r2 review 验修复(本 change 收口)/ commit / 起 M2 `ai-abstraction-layer`

---

## 2026-06-18 · 进入 M0 实施阶段(待 `/opsx:apply init-android-project` 启动)

- OpenSpec change `init-android-project` 起草完成(4/4 artifacts):`proposal.md` / `design.md` / `specs/{android-build-system,app-shell,material-theme,localization,testing-framework}/spec.md` / `tasks.md`;落到 `openspec/changes/init-android-project/`
- M0 范围:Gradle 8 + Version Catalog + Hilt + Compose + Room + DataStore + ktlint + 测试框架;`./gradlew :app:assembleDebug` + `:app:testDebugUnitTest` + `:app:ktlintCheck` + `:app:lintDebug` + `:app:check` 全部 0 错误为 M0 完成标志
- 不引入业务代码;Glance / OkHttp 依赖进 Version Catalog 但**不**使用,留给 M2 / M4

---

## 2026-06-18 · M0 完成 + review r1/r2 + 归档

- OpenSpec change `init-android-project` apply + review + 归档完整闭环:
  - apply 落地 43 个文件(源 + Gradle 配置 + 资源 + 测试骨架)
  - review r1 发现 2 HIGH + 3 MEDIUM + 4 LOW,review r2 全修复(HIGH 1.2 / MEDIUM 2.1 / 2.2 / LOW 3.4 完整修;HIGH 1.1 修复范围缩小)
  - sync 5 份 spec 到 `openspec/specs/{android-build-system,app-shell,localization,material-theme,testing-framework}/spec.md`
  - archive 到 `openspec/changes/archive/2026-06-18-init-android-project/`
- **M0 完成状态**:`assembleDebug` + `testDebugUnitTest` + `lintDebug` 全绿;`ktlintCheck` 剩 5 个 standard:function-naming 已知 follow-up(详见 memory `ktlint-compose-pascalcase-1.0`)
- **v1 上线策略**:`allowBackup="false"` 完全关闭 Auto Backup;`backup_rules.xml` forward-looking,M2 真上 apikey 时再决定;**v1 接受"备份关闭"换"apikey 绝对不外流"**
- **下一步候选**:M1 `quick-note-feature`(随手记闭环),或 M0 后 polish follow-up

---

## 2026-06-18 · M0 实施落地(apply 完成;ktlint polish 待补)

- OpenSpec change `init-android-project` apply 落地:43 个文件中 1.x/2.x/3.x/4.x/5.x/6.x/7.x/8.x/9.x 任务全部完成(源文件 + Gradle 配置 + 资源 + 测试骨架)
- 环境补装:`brew install openjdk@17` + `brew install gradle` + `brew install --cask android-commandlinetools` + `sdkmanager` 装 platforms;android-35 / build-tools;35.0.0 / platform-tools;JAVA_HOME / ANDROID_HOME 持久化进 `~/.zshrc`;记录文档落 `docs/usage/development-setup.md`
- **M0 验收结果**:
  - ✅ `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk` 20.7 MB
  - ✅ `./gradlew :app:testDebugUnitTest` → PlaceholderTest SUCCESSFUL(JUnit5 Jupiter 引擎 + useJUnitPlatform 跑通)
  - ✅ `./gradlew :app:lintDebug` → BUILD SUCCESSFUL
  - ⚠️ `./gradlew :app:ktlintCheck` → 6 个 standard:function-naming + standard:property-naming 违规:Compose Composable PascalCase 跟 ktlint 1.0.x 默认规则硬冲突;`disabledRules` 配置 + `@file:Suppress` + `@Suppress` + `ktlint-disable` 注释均未生效(rule-engine 1.0.x 已知行为)
  - ⚠️ `./gradlew :app:check` → 上述 5 项聚合,因 ktlintCheck 失败
- **kts 插件版本修正**:`gradle/libs.versions.toml` 的 `ktlint` 由 1.4.0 升到 12.1.0(plugin marker 才能解析)
- **已知 follow-up**(M5 打磨 / `polish-and-internal-release` change 统一处理):
  - ktlint rule-engine ≥ 1.1 / `experimental:annotation` 排除 Compose 命名规则
  - Android Studio 项目内 Preview 渲染人工验收(本机没装 AS)
  - wrapper pin 版本 8.10.2 与 AGP 8.7.3 兼容性,后续 AGP 升级时一并 bump

---

## 2026-06-18 · 规划阶段(已完成)

- v1 路线图定稿:`docs/plans/writing-with-ai-mobile-roadmap.md`
- 三家 AI provider 协议统一走 Anthropic Messages API 兼容(1 个通用 `AnthropicCompatibleAdapter` 替代 3 个独立 adapter);4 份协议文档落 `docs/usage/`(`api-anthropic-compatible.md` / `api-deepseek.md` / `api-minimax.md` / `api-mimo.md`)
- 关键决策定档(roadmap §0 / §15.1):
  - 平台:安卓 only;技术栈 Kotlin + Compose + Material 3
  - 数据:本地 + 可选导出(JSON / Markdown),无后端
  - 包名:`com.yy.writingwithai`
  - 分发:APK only,**任何**国内国外应用市场都不上架
  - apikey:开发期不需要真实值,M2 用 `FakeProvider` 端到端验收,真实 provider 联调推迟到 M5 / 实际使用时
  - 多语言:v1 必须支持**中文 + 英文**,跟随系统
  - 预置 provider:deepseek / minimax / mimo(全部 Anthropic Messages API 兼容)
- CLAUDE.md 从"Vite + React"基线切到"原生 Android"基线;新增 `docs/usage/api-*.md` 扩展约定
- 后续 OpenSpec change 顺序已规划(roadmap §15.2):`init-android-project` → `quick-note-feature` → `ai-abstraction-layer` → `ai-writing-actions` → ...