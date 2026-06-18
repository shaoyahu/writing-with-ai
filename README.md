# writing-with-ai

> 安卓原生轻量写作 APP:从桌面小组件快速记录灵感,必要时由 AI 辅助扩写 / 润色 / 整理。
> apikey 本机加密,数据本地为主,**只发 APK,任何应用市场都不上架**。

## 快速开始(开发机)

1. **安装前置依赖** —— 按 [`docs/usage/development-setup.md`](docs/usage/development-setup.md) 装 JDK 17 + Gradle + Android SDK。
2. **拉仓库**:`git clone <repo>` → `cd writing-with-ai`。
3. **构建 Debug APK**:`./gradlew :app:assembleDebug`(首次会下载 Gradle wrapper 与 AGP 依赖)。
4. **跑测试 + 静态检查**:`./gradlew :app:check`。
5. **装到设备**:`./gradlew :app:installDebug`(需要真机 / 模拟器已连接)。

Android Studio 仍是日常开发首选(Compose Preview / Layout Inspector);命令行 `./gradlew` 留给 CI / 脚本场景。

## 项目文档

| 路径 | 用途 |
| --- | --- |
| [`CLAUDE.md`](CLAUDE.md) | 会话规则 / 分工约定 / OpenSpec 优先 / 命令 / 架构要点 / 包结构 / 约定 |
| [`docs/usage/development-setup.md`](docs/usage/development-setup.md) | **新开发机环境安装清单**(JDK 17 + Gradle + Android SDK) |
| [`docs/plans/writing-with-ai-mobile-roadmap.md`](docs/plans/writing-with-ai-mobile-roadmap.md) | v1 整体路线图(M0~M5 里程碑、风险、技术栈) |
| [`docs/progress.md`](docs/progress.md) | 项目进度总览(只记走向不记细节) |
| [`docs/usage/api-anthropic-compatible.md`](docs/usage/api-anthropic-compatible.md) | AI provider 通用协议(Anthropic Messages API) |
| [`docs/usage/api-deepseek.md`](docs/usage/api-deepseek.md) / [`api-minimax.md`](docs/usage/api-minimax.md) / [`api-mimo.md`](docs/usage/api-mimo.md) | 各 provider 特定协议 |
| `openspec/changes/` | 正在起草 / 实施中的 OpenSpec change |
| `openspec/changes/archive/` | 已归档的 change |
| `docs/reviews/` | review 产物归档 |

## 当前状态

**M0 · 工程脚手架 ✅**(2026-06-18):Gradle + Hilt + Compose + Material 3 + 测试框架 + 包结构占位全部就绪;`./gradlew :app:assembleDebug` + `testDebugUnitTest` + `lintDebug` 全过。已知遗留:ktlint 与 Compose PascalCase 命名规则冲突,推迟到 M5 打磨阶段。

下一个候选 change:`quick-note-feature`(M1 随手记闭环)。

## 技术栈

- Kotlin 2.x + Jetpack Compose + Material 3
- Gradle 8 + Version Catalog(`gradle/libs.versions.toml`)
- minSdk 26 / targetSdk 35 / compileSdk 35,JDK 17
- Hilt + KSP / Room / DataStore / OkHttp / Glance(Glance + OkHttp 在 M0 占用但不写代码)
- 多语言:`values/`(中文)+ `values-en/`(英文),跟随系统语言
- 包名:`com.yy.writingwithai`

## 隐私 / 分发

- apikey **从不**入库,本机加密存 EncryptedSharedPreferences(Tink 包装 Android Keystore),不进 logcat / Auto Backup / BuildConfig。
- 数据 **本地为主**,可选导出 JSON / Markdown;**无**后端、**无**账号、**无**云同步(v1)。
- 分发渠道:**只发 APK**,任何国内国外应用市场都不上架。