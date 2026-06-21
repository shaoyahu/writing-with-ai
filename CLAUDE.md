# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 会话规则

- **回复语言**:用中文回复用户。
- **提交控制**:每次修改完代码后**不自动提交**;`git commit` / `git push` / `git merge` 等操作只能由用户执行。完成代码改动后,等用户明确表示可以提交或推送时,再按照指示提交或推送。
- **任务持续性**:会话中无需顾虑 token / 成本花费,不要因为"成本太高"而主动暂停、缩短或放弃任务;按计划把当前任务做完。

## 分工约定

### 用户角色(只 3 个)

1. **项目规划设计** — roadmap / 架构 / 关键决策(如"三家统一走 Anthropic 兼容"、"包名 `com.yy.writingwithai`")。
2. **指令下发** — 告诉 AI 做什么(开始 change / 暂停 / 改这条 / **发起 review** 等);review 是 AI 的事,**用户通过指令触发 AI 发起 review**(随时可以要求)。
3. **实际应用体验反馈** — APP 装到手机上用过后的反馈,尤其是 bug / UX 问题。

### AI 角色(7 个)

1. **编写代码** — 走 `/opsx:apply` 实现 change 的 `tasks.md`。
2. **review 代码** — **AI 自审** + **用户指令触发的 review**(`"review 一下 X"` / "发起 code-review")。
3. **分析测试** — 写测试 / 跑测试 / CI / 静态分析(ktlint / Android Lint)。
4. **修 bug(自修)** — 测试失败 / 自检发现 / CI 报错 → AI **自修**(无须指令);跨方向调整(发现需要新增 OpenSpec change)→ **停下告知用户**。
5. **项目进度维护** — `docs/progress.md` 按规则主动追加(M 完成 / 关键 bug / 阶段切换 / 决策变更 / OpenSpec change 批量归档时)。
6. **整体进展把控** — 检测阻塞、风险预警、状态盘点;**不**主动发起新 OpenSpec change。
7. **用户问"下一个任务"时** — 浏览 `docs/plans/writing-with-ai-mobile-roadmap.md` §13 + §15.2 + `openspec/changes/` 已起草 + `openspec/changes/archive/` 已完成 → 列**可能的下一步候选**给用户。

### 工作模式

- **纯被动**(写完 change 后停下等指令):
  - AI 写完一个 change → 主动告知用户 + **停下**,等用户指令才开始下一个
  - AI **不**自动起草下一个 change 的 proposal(避免做用户不想要的提案)
- **随时响应 review 指令**:用户**任何时刻**都可以说"review 一下 X",AI 立即发起
- **AI 自修 bug 不需要指令**:测试 / 自检 / CI 发现的失败,AI 自己改
- **跨方向调整需要停**:如果 AI 发现需要新增 OpenSpec change / 改 roadmap / 改决策,**停下告知用户**,由用户决策

## 第一原则:OpenSpec 优先

用户提出的任何需求,**优先考虑使用 OpenSpec 的指令**处理:

| 动作 | Skill(按名触发) | Slash command |
| --- | --- | --- |
| 任务创建 | `openspec-propose` | `/opsx:propose` |
| 任务进行 | `openspec-apply-change` | `/opsx:apply` |
| 任务归档 | `openspec-archive-change` | `/opsx:archive` |
| 需求澄清(辅助) | `openspec-explore` | `/opsx:explore` |
| delta spec 合并(辅助) | `openspec-sync-specs` | `/opsx:sync` |

不熟悉 OpenSpec 时先读 `/docs/usage/open-spec.md` 复习完整用法。**由 agent 自行判断**当前场景是否值得走 OpenSpec —— 单行 typo、明显 bug、纯本地环境问题等不需要走 change 流程,直接动手即可。

## 项目概况

原生 Android 应用,**只支持安卓端**。技术栈:**Kotlin 2.x + Jetpack Compose + Material 3**,构建系统 Gradle 8.x + Version Catalog(`gradle/libs.versions.toml`)。当前仓库里**没有领域代码**;`app/` 模块尚未初始化,首个 OpenSpec change `init-android-project` 会拉起工程脚手架(整体规划见 `docs/plans/writing-with-ai-mobile-roadmap.md`)。任何新功能一律先按 OpenSpec change 起草,再实现。

## 命令

所有命令在仓库根目录运行。**前置依赖**见 [`docs/usage/development-setup.md`](docs/usage/development-setup.md)(JDK 17 + Gradle,本机一次装好即可)。当前**没有测试框架依赖**(`app/build.gradle.kts` 里没有 testImplementation),"跑单个测试"目前不存在 —— `init-android-project` change 会一起把 JUnit5 / MockK / Turbine 加进来,再加业务测试。

**编译环境重要**: 本机 JDK 17 在 `/opt/homebrew/opt/openjdk@17`。macOS 的 `/usr/bin/java` 是 stub,直接 `java -version` 会报 "Unable to locate a Java Runtime",但**这不代表无法编译**——所有 Gradle 命令前先 `export JAVA_HOME=/opt/homebrew/opt/openjdk@17` 即可正常编译和跑测试。**每次修改代码后默认跑编译 + ktlint + 单测验证**,不要因为 `java -version` 报错就跳过。

| 任务 | 命令 | 作用 |
| --- | --- | --- |
| 编译 Debug APK | `./gradlew :app:assembleDebug` | 构建可调式 APK 到 `app/build/outputs/apk/debug/` |
| 编译 Release APK | `./gradlew :app:assembleRelease` | 构建发布 APK(需先在 `~/.gradle/gradle.properties` 配签名) |
| 安装到连接的设备 / 模拟器 | `./gradlew :app:installDebug` | 编译 + 安装,等价于 `adb install` |
| 跑单元测试 | `./gradlew :app:testDebugUnitTest` | JVM 单测 |
| 跑仪器测试 | `./gradlew :app:connectedDebugAndroidTest` | 需要设备 / 模拟器已连接 |
| 跑所有 check(lint + test) | `./gradlew :app:check` | CI 入口 |
| 代码风格 | `./gradlew :app:ktlintCheck` | ktlint 静态检查,见 `config/ktlint/.editorconfig` |
| 修复 ktlint 问题 | `./gradlew :app:ktlintFormat` | 自动修复可修项 |
| 清理 | `./gradlew clean` | 清空 `build/` 目录 |

Android Studio 仍是日常开发首选(Compose Preview / Glance Preview / Layout Inspector 都靠它);`./gradlew` 命令留给 CI 或脚本场景。

## 架构要点

**Gradle Version Catalog**
- 所有依赖集中在 `gradle/libs.versions.toml`,业务代码不直接写版本号。
- KSP / Hilt / Compose 编译器版本联动,放在 `[versions]` 块统一管理。
- 加新库:先在 `libs.versions.toml` 加版本,再在 `app/build.gradle.kts` 引用,不要在子模块里硬编码。

**ktlint**
- 配置文件在 `config/ktlint/.editorconfig`,全局生效。
- 业务 lint 规则加在 `app/build.gradle.kts` 的 `ktlint { }` 块里(或自定义 RuleSet),不要新开配置文件。
- pre-commit / CI 都跑 `ktlintCheck`,失败则阻断。

**样式系统**
- 主题用 Material 3 ColorScheme,定义在 `app/src/main/java/.../ui/theme/Color.kt` / `Theme.kt` / `Type.kt`。
- 颜色、字体、间距等一律走 `MaterialTheme.colorScheme.*` / `MaterialTheme.typography.*`,不直接写 hex 或 sp 值。
- 暗色 / 亮色 / 跟随系统 三档在主题层处理,UI 层不感知。
- 自定义设计 token(如统一圆角、间距)放在 `Theme.kt` 的 `CompositionLocal` 中,避免业务侧散落 `Modifier.cornerRadius(...)`。

**图标 / 资源**
- 矢量图标放 `app/src/main/res/drawable/`,命名 `ic_<name>_<size>dp.xml`(如 `ic_ai_24.xml`)。
- 图片资源放 `res/drawable-nodpi/` 或 `res/mipmap-*/`(启动图标),用 `@drawable/ic_xxx` 引用。
- 字符串一律走 `res/values/strings.xml`,**不**在 Composable 里硬编码中文。

**应用入口**
- 单 `MainActivity`(`ComponentActivity`)+ `setContent { App() }`,承载整个 NavHost。
- `WritingApp : Application` 是 `@HiltAndroidApp` 入口,启动时完成 Hilt / Room / DataStore / OkHttp 初始化。
- 桌面小组件、Quick Settings Tile 等入口走 `PendingIntent` / `Action` 显式启动到指定 Nav 路由。

## 约定

- **类型导入**:Kotlin 没有 `import type`,但避免 `import xxx.*`(会触发 ktlint 警告)。
- **未使用变量是构建错误**(Kotlin 编译器 `-Xreport-all-warnings` + ktlint `unused` 规则),提交前清掉;确实不用的形参加 `_` 前缀(如 `fun foo(_bar: String)`)。
- **Composable 函数必须大写开头**,`@Composable` 注解的私有函数同样遵守;预览函数命名 `XxxPreview` 配 `@Preview` 注解,放同文件底部。
- **包名小写、类名 PascalCase、函数 camelCase、常量 UPPER_SNAKE_CASE**;`internal` 工具函数放同包 `internal` 文件。
- **新功能先 OpenSpec 后代码**:`openspec/changes/<name>/` 下产出 `proposal.md` / `design.md` / `tasks.md`,再用 `/opsx:apply` 实现。
- **业务 lint 规则**加在 `app/build.gradle.kts` 的 `ktlint {}` / `detekt {}` 块(或 `config/` 目录),**不要**散落多个配置文件。

## AI 集成约定

项目核心"writing-with-ai"涉及 LLM 调用,统一约定防止散乱:

- **单一抽象层**:所有 AI 调用必须经过 `core/ai/`(路径见 §"包结构")下的统一封装,业务代码**禁止**直接 `OkHttp` / `Retrofit` / `HttpURLConnection` 调 provider API。
- **apikey 仅本机加密存储**:走 `EncryptedSharedPreferences` 或 Tink 包装的 Android Keystore,**绝不**进 Room / 明文 SharedPreferences / logcat / Auto Backup / `BuildConfig`。
- **v1 备份策略**:`AndroidManifest.xml` 设 `android:allowBackup="false"`,**完全关闭** Auto Backup(包括 cloud-backup + device-transfer);`res/xml/{backup_rules,data_extraction_rules}.xml` 是 forward-looking 配置,等 M2 启用 apikey 落 EncryptedSharedPreferences 时再决定要不要开 backup + 用 rules 排除 `writingwithai_secure_prefs.xml` 路径。**当前 v1 接受"备份关闭"换"apikey 绝对不外流"**。
- **用户同意前置**:首次 AI 调用前必须有用户同意(条款、隐私、可能的成本),同意状态持久化在 DataStore,具体实现走 proposal。
- **流式优先**:长生成(扩写、润色、整理)用 SSE / chunked 流式响应;短查询可一次性,具体协议走 proposal。
- **错误降级**:AI 调用失败必须 fallback 到无 AI 的体验,绝不允许白屏或阻塞核心流程(写作流程永远可手动继续)。
- **Token / 成本可观测**:所有出站调用经过能记录 token 消耗(入/出/总)和耗时的 wrapper,落 `ai_history` 表;具体存储走 proposal。
- **prompt 注入防御**:用户文本只放在 user 消息的 content 字段,**不参与** system prompt 拼接;`core/ai/prompt/` 下的模板文件要过 review。

Provider 选型、prompt 设计、prompt 注入防御等设计细节由各 OpenSpec change 的 `design.md` 决定 —— 这里只定"必须遵守的硬规则"。

## 包结构

项目当前 `app/src/main/java/com/<your>/writingwithai/` 尚未建立。首个 `init-android-project` change 会按下列结构拉起:

```
app/src/main/java/com/<your>/writingwithai/
├── app/                    # Application、MainActivity、Nav 根、主题、入口
│   ├── WritingApp.kt       # @HiltAndroidApp
│   ├── MainActivity.kt     # 单 Activity + NavHost
│   ├── AppNav.kt           # 类型安全路由定义
│   └── ui/theme/           # Material 3 ColorScheme / Typography / Shape
├── core/                   # 真正跨 feature 的基础设施
│   ├── data/               # Room(AppDatabase + DAO + Entity)、Repository
│   ├── prefs/              # DataStore 包装、apikey 加密仓库
│   ├── ai/                 # AI 抽象层(见 §"AI 集成约定")
│   │   ├── api/            # AiGateway / AiProvider SPI / AiStreamEvent
│   │   ├── provider/       # 各家 adapter(deepseek / minimax / mimo / custom)
│   │   ├── prompt/         # 扩写/润色/整理模板
│   │   └── stream/         # SSE 解析、错误映射
│   ├── net/                # OkHttp client、interceptor、错误分类
│   ├── widget/             # Glance 桌面小组件入口
│   └── common/             # Result、错误类型、扩展函数
├── feature/
│   ├── quicknote/          # 随手记
│   │   ├── list/           # 列表 + 搜索 + 标签
│   │   ├── detail/         # 详情 + 编辑 + AI 入口
│   │   └── model/          # Note 领域模型
│   ├── aiwriting/          # AI 助手
│   │   ├── action/         # 扩写/润色/整理 操作
│   │   ├── streaming/      # 流式 UI 状态机
│   │   └── history/        # 调用历史
│   ├── settings/           # 设置
│   └── onboarding/         # 首次启动 + 用户同意
└── di/                     # Hilt Module 集中点
```

**硬规则**:

- 一个 feature **必须自包含** —— 删除 / 移动它不能影响其他 feature;跨 feature 引用走 `feature/<name>/<Feature>Entry.kt`(Kotlin 里以 object 暴露),不直接 import 对方内部文件。
- 跨 feature 复用代码放 `core/`,**不**在 features 之间互相 import;如果出现真正可复用的 Composable 组件(按钮、空状态、骨架屏等),放 `core/ui/`(本项目里 `core/ui/` 留作可选,先在 `feature/<主>/` 自管)。
- `core/` 是基础设施(AI、加密、网络、Room、Widget),不是业务工具的堆场 —— 业务工具放 feature 自己的子包。
- 桌面小组件(`core/widget/`)、Nav 根(`app/AppNav.kt`)、主题(`app/ui/theme/`)是平台入口,放最外层,不进 `feature/`。

## Git workflow

- **单人项目,无 feature branch / worktree,直接 main**:OpenSpec change 名是 spec 维度标识,跟 git branch 是两个命名空间;所有工作直接在 `main` 上 commit,不需要开分支、worktree 或 PR。
- **Commit message 格式**:遵循 Conventional Commits(`<type>(<scope>): <subject>`)。
  - `type` 限 `feat` / `fix` / `refactor` / `docs` / `style` / `test` / `chore` / `perf`
  - `scope` 建议填 OpenSpec change 名(如 `init-android-project`),方便回溯 commit 和 change 的对应关系;非 change 相关的提交可省略
  - `subject` 用中文,一句话说清改了什么
- **不用 PR / merge / squash**:单人项目没有"审阅门",commit 即发布。

## 文档与 review 目录约定

仓库的文档相关目录,职责严格分离 —— 文档规划、需求分析、review 归档三者互不交叉:

| 目录 | 职责 | 例子 |
| --- | --- | --- |
| `openspec/changes/` | 需求分析和变更规划(走 OpenSpec 工作流) | `init-android-project/proposal.md` |
| `docs/plans/` | 文档自身的规划(roadmap、大纲、写作规范) | `api-docs-roadmap.md`、`style-guide.md` |
| `docs/usage/` | 项目自身的使用 / 集成说明(OpenSpec 怎么用、AI provider 协议等稳定文档) | `open-spec.md`、`api-anthropic-compatible.md`、`api-deepseek.md` |
| `docs/progress.md` | 项目进度总览(从开工到现在的关键节点 / bug 修复,**只记走向不记细节**) | `docs/progress.md` |
| `docs/reviews/` | review 产物归档(对 OpenSpec change / 文档的评审) | `2026-06-18-init-android-project-code-review-r1.md` |

### `docs/plans/` 命名

`kebab-case.md`(不带日期 —— 规划通常跨较长时间,文件元数据够用)。

- 例:`api-docs-roadmap.md`、`style-guide.md`、`getting-started-outline.md`
- 例(本项目):`writing-with-ai-mobile-roadmap.md` 是 v1 整体路线图。
- 状态后缀(可选,版本变化时用):`-draft` / `-final` / `-archived`
- 时间敏感变体(季度 / 月度 OKR 类):`YYYY-Qn-<topic>.md` 或 `YYYY-MM-<topic>.md`

### `docs/usage/` 命名

- `kebab-case.md`(不带日期 —— 用法 / 集成说明是稳定文档,版本变化时改内容即可)。
- 例:`open-spec.md`(OpenSpec 工作流说明)、`api-anthropic-compatible.md`(AI provider 通用协议)。
- 状态后缀(可选,版本变化时用):`-draft` / `-final` / `-archived`。

#### `docs/usage/api-*.md` 扩展约定(AI provider 协议文档)

- **每加一个 AI provider 写一份 `api-<provider>.md`**,放 `docs/usage/`。
- 命名:小写连字符,provider id 与 `ProviderConfig.id` 一致,如 `api-deepseek.md` / `api-minimax.md` / `api-mimo.md`。
- **协议公共部分**(端点形态、字段语义、SSE 事件、错误码)放 `api-anthropic-compatible.md`,**不在每份 provider 文档里重复**;provider 文档只列**该 provider 特定** 的部分(base URL、auth header、模型、字段差异、错误码清单、注意事项)。
- 修改 provider 文档时同步检查 `api-anthropic-compatible.md` 是否仍准确(避免文档漂移)。
- 新增 provider 必须在 `docs/plans/writing-with-ai-mobile-roadmap.md` §6.3 / §15.1 同步更新(若 plan 已归档则新开 `docs/plans/writing-with-ai-mobile-roadmap-rN.md`)。

### `docs/reviews/` 命名

`YYYY-MM-DD-<subject>-<review-type>-rN.md`

- `YYYY-MM-DD` — 日期
- `<subject>` — **必须是** OpenSpec change 名(kebab-case,对应 `openspec/changes/<subject>/`)
- `<review-type>` — kebab-case,四种:`code-review` / `doc-review` / `design-review` / `change-review`
- `rN` — r 前缀 + 数字,不补零(`-r1`, `-r2`, ..., `-r10`, `-r100`);**每天重置**(每天从 `r1` 开始)
- 第一次 review 也带 `-r1`(格式一致,无"是否省略"的歧义)

**例子**:

```
2026-06-18-add-dark-mode-code-review-r1.md
2026-06-18-add-dark-mode-code-review-r2.md
2026-06-18-style-guide-doc-review-r1.md
2026-06-19-add-dark-mode-code-review-r1.md     # 跨天序号重置
```

> **排序注意**:`-r10` 字典序排在 `-r2` 前面(`r10` < `r2`)。日期前缀保证跨天排序正确;同一天轮次有限,用 mtime 判断顺序。如果以后需要严格数字序,改成 `-r01` 补零即可。

### `docs/progress.md` 维护

- **位置**:`docs/progress.md`(单文件;**不**在 `docs/progress/` 建子目录,除非条目膨胀到 200+ 行再拆)
- **维护人**:**AI 主动维护**(本仓库会话里的 Claude);用户**不**手动编辑;用户可在对话里指出"漏记了 / 这条不准",由 AI 修
- **更新时机**(由 AI 自行判断,以下为典型触发点):
  - 每个 M 完成(M0~M5)
  - 关键 bug 修复(尤其跨多次提交才解决的、影响主流程的)
  - 阶段切换(规划 → M0 → M1 ...;或里程碑内大方向调整)
  - 重大决策变更(roadmap §0 / §15.1 拍板项变化)
  - 一批 OpenSpec change 归档完成
- **不**在以下时机更新(避免噪音和膨胀):
  - 每次 commit 之后
  - 每次对话结束
  - 每次 review 之后
  - 用户问"进度怎么样"时(那时只查不写,除非用户明确要求)
- **格式**:严格遵守 `docs/progress.md` 顶部"维护规则"段;时间倒序;一条 1-3 行
- **与其它文档的边界**:
  - "做了什么 commit" → `git log`
  - "规划是什么" → `docs/plans/`
  - "单次评审说了什么" → `docs/reviews/`
  - "现在到哪了 / 修了什么 bug" → **`docs/progress.md`**

### 通用

- 全用 `.md`(Markdown)
- 文件名全用 kebab-case
- 不用空格 / 下划线 / 中文文件名
