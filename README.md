# writing-with-ai

> 安卓原生轻量写作 APP:从桌面小组件快速记录灵感,必要时由 AI 辅助扩写 / 润色 / 整理。
> apikey 本机加密,数据本地为主,**只发 APK,任何应用市场都不上架**。

## 快速开始(开发机)

1. **安装前置依赖** —— 按 [`docs/usage/development-setup.md`](docs/usage/development-setup.md) 装 JDK 17 + Gradle + Android SDK。
2. **拉仓库**:`git clone <repo>` → `cd writing-with-ai`。
3. **构建 Debug APK**:`./gradlew :app:assembleDebug`(首次会下载 Gradle wrapper 与 AGP 依赖)。
4. **跑测试 + 静态检查**:`./gradlew :app:check`。
5. **装到设备**:`./gradlew :app:installDebug`(需要真机 / 模拟器已连接)。

Android Studio 仍是日常开发首选(Compose Preview / Glance Preview / Layout Inspector);命令行 `./gradlew` 留给 CI / 脚本场景。

## 项目文档

| 路径 | 用途 |
| --- | --- |
| [`CLAUDE.md`](CLAUDE.md) | 会话规则 / 分工约定 / OpenSpec 优先 / 命令 / 架构要点 / 包结构 / 约定 |
| [`docs/usage/development-setup.md`](docs/usage/development-setup.md) | **新开发机环境安装清单**(JDK 17 + Gradle + Android SDK) |
| [`docs/usage/signing.md`](docs/usage/signing.md) | Release 签名(`~/.gradle/gradle.properties` + keystore) |
| [`docs/usage/open-spec.md`](docs/usage/open-spec.md) | OpenSpec 工作流(`/opsx:propose|apply|archive|sync|explore`) |
| [`docs/plans/writing-with-ai-mobile-roadmap.md`](docs/plans/writing-with-ai-mobile-roadmap.md) | v1 整体路线图(M0~M6 里程碑、风险、技术栈) |
| [`docs/progress.md`](docs/progress.md) | 项目进度总览(只记走向不记细节) |
| [`docs/usage/api-anthropic-compatible.md`](docs/usage/api-anthropic-compatible.md) | AI provider 通用协议(Anthropic Messages API) |
| [`docs/usage/api-deepseek.md`](docs/usage/api-deepseek.md) / [`api-minimax.md`](docs/usage/api-minimax.md) / [`api-mimo.md`](docs/usage/api-mimo.md) | 各 provider 特定协议 |
| [`docs/usage/domestic-rom-widget.md`](docs/usage/domestic-rom-widget.md) | 国产 ROM widget 兼容(MIUI / ColorOS / HarmonyOS / OriginOS) |
| [`docs/usage/rom-compatibility-notes.md`](docs/usage/rom-compatibility-notes.md) | ROM 兼容性测试记录 |
| `openspec/changes/` | 正在起草 / 实施中的 OpenSpec change |
| `openspec/changes/archive/` | 已归档的 change |
| `docs/reviews/` | review 产物归档 |

## 当前状态

M0~M6 全部落地;主屏/详情/编辑/小组件/AI 操作/同意门/自定义 Provider 全链路可用。

下一个候选:`docs/plans/writing-with-ai-mobile-roadmap.md §13 + §15.2` 列出的 v1+ 方向。

## 技术栈

- Kotlin 2.x + Jetpack Compose + Material 3
- Gradle 8 + Version Catalog(`gradle/libs.versions.toml`)
- minSdk 26 / targetSdk 35 / compileSdk 35,JDK 17
- Hilt + KSP / Room / DataStore / OkHttp / Glance(桌面小组件)
- AI:抽象层统一走 `core/ai/`,支持 Anthropic 兼容 + OpenAI 兼容格式,内置 DeepSeek / MiniMax / MiMo 三家,**用户可自定义 Provider**(模型管理 → FAB "+")
- 多语言:`values/`(中文)+ `values-en/`(英文),跟随系统语言
- 包名:`com.yy.writingwithai`

## 隐私 / 分发

- apikey **从不**入库,本机加密存 EncryptedSharedPreferences(Tink 包装 Android Keystore),不进 logcat / Auto Backup / BuildConfig。
- 数据 **本地为主**,可选导出 JSON / Markdown;**无**后端、**无**账号、**无**云同步(v1)。
- 分发渠道:**只发 APK**,任何国内国外应用市场都不上架。

## 项目本地工具

- `package.json` / `node_modules/` 在仓库根目录存在(`headroom-ai` 等本地辅助脚本),已被 `.gitignore` 屏蔽,不进库。新开发机无需装 Node,除非要跑这些本地脚本。