## Context

仓库目前没有 Android 工程:`app/` 不存在，没有 `gradlew` 脚本，没有 `settings.gradle.kts`，也没有 `gradle/libs.versions.toml`。`docs/plans/writing-with-ai-mobile-roadmap.md` 已经把 v1 的栈、架构、目录结构和 M0~M5 里程碑都拍板(`§0 / §4 / §5 / §13 / §15.1`),CLAUDE.md 也把硬规则(`§架构要点` / `§AI 集成约定` / `§包结构` / `§约定`)落好了。M0 的目标是把这些规划**落实成可编译的代码骨架**，让后续 8 个 change 都能直接 `apply` 进同一个工程。

利益相关方:

- **AI(本文作者)**:负责按 `tasks.md` 落地代码，每个 milestone 跑 `./gradlew :app:check` 自验。
- **用户**:日常用 Android Studio 开发，关注 Preview / Layout Inspector / 真机调试体验;代码风格 / 包结构 / 命令行入口要符合预期。

约束(来自 roadmap §4 / §11 / §13 / §15.1):

- JDK 17、AGP 8.x、Kotlin 2.x、minSdk 26 / targetSdk 35 / compileSdk 35。
- 单 `MainActivity` + `NavHost`，无 AppCompat 主题;Compose Material 3。
- 包名 `com.yy.writingwithai`(CLAUDE.md §"项目概况" / roadmap §15.1 已定)。
- 不上架任何应用市场 → 不配置 release 签名。
- 多语言 zh + en，跟随系统。

## Goals / Non-Goals

**Goals:**

- 建立单 Gradle module 的 Android 工程，Version Catalog 统一管依赖。
- 跑通核心质量门:`./gradlew :app:assembleDebug` + `:app:testDebugUnitTest` + `:app:ktlintCheck` + `:app:lintDebug` + `:app:check` 全绿。
- 落地"应用入口骨架"(`WritingApp` / `MainActivity` / `AppNav`)与 Material 3 主题(light / dark / system)。
- 落地多语言骨架(`values/` + `values-en/`)+ `HardcodedText` lint 规则。
- 落地测试框架(JUnit5 + MockK + Turbine + Compose Test)+ 一个占位测试。
- 占位建好 `core/ data|prefs|ai|net|widget|common/`、`feature/ quicknote|aiwriting|settings|onboarding/`、`di/` 等空目录(`.gitkeep`)，让后续 change 直接 `apply`。
- 引入 Glance 与 OkHttp 依赖但**不使用**(留给 M4 widget / M2 AI 抽象层)，避免后续 change 改 Version Catalog。

**Non-Goals:**

- 不写任何业务代码(无 Note、Repository、UseCase、Widget、Provider、AiGateway 等)。
- 不配置 release 签名 / 不写 ProGuard 规则 / 不接 CI(留给后续 change 或 §"open questions"决定)。
- 不接 Crashlytics / 任何第三方分析 SDK(roadmap §9 隐私原则)。
- 不实现"首次启动同意页"(roadmap §15.2 item 8)。
- 不做 Room migration / schema(M2 才引入数据库 schema)。
- 不做侧滑返回手势适配(M4 才细化，M0 走系统默认)。

## Decisions

### 1. 单 Gradle module，不拆子模块

v1 整体规模不大(单 feature 数量 < 10，代码总量预计 < 30k 行 Kotlin)，过早拆子模块反而带来 build 复杂度、KSP 多次跑、Hilt 跨 module 调试。**单 module** 让 build graph 简单、IDE 索引快。

替代方案(已否决):`core-data` / `core-ai` / `feature-*` 多 module。否决理由:Gradle 8 + KSP 跨 module 的"接口与实现分离"价值，在 v1 阶段投入产出比低;一旦真有跨 feature 强耦合，再拆也来得及(roadmap §16 演进路径已留口)。

### 2. Kotlin DSL(`build.gradle.kts`)+ Version Catalog(`gradle/libs.versions.toml`)

CLAUDE.md §"架构要点"硬规定。Version Catalog 锁住所有版本号，防止散落字符串;`build.gradle.kts` 仅做"用谁"的引用，不写"几版本"。

替代方案(已否决):Groovy `build.gradle` / 直接在 `build.gradle.kts` 写版本号字符串。否决理由:不符合 CLAUDE.md 硬规则;`gradle/libs.versions.toml` 已经为 KSP / Hilt / Compose Compiler 等联动依赖的版本对齐提供了中心点。

### 3. KSP 而非 KAPT

Hilt 与 Room 都已支持 KSP(且 KSP 比 KAPT 快 ~2x)。整个工程用 KSP 一套。

- `com.google.devtools.ksp` 插件在 root `build.gradle.kts` 声明版本，通过 `alias(libs.plugins.ksp)` 引入到 app 模块。
- Hilt 的 `com.google.dagger.hilt.android` 仍用其官方 Gradle 插件(`com.google.dagger.hilt.android` plugin)，通过 `alias(libs.plugins.hilt)` 引入。Hilt 的注解处理走 KSP(替换原 KAPT)。

### 4. Compose Compiler 不单独指定版本

Kotlin 2.x 之后，Compose Compiler 由 Kotlin 团队与 Compose 团队对齐，**作为 Kotlin 编译器插件内置**。libs.versions.toml 不再需要 `kotlin-compose-compiler` 版本号，只需在 app 模块启用 `org.jetbrains.kotlin.plugin.compose` 插件即可。

替代方案(已否决):继续显式指定 `kotlinCompilerExtensionVersion` + `composeOptions { kotlinCompilerExtensionVersion = ... }`。否决理由:Kotlin 2.x 已废弃该字段，继续配置会触发警告;且版本对齐责任转移到 Compose 插件本身，反而更安全。

### 5. 主题:`isSystemInDarkTheme()` + `MaterialTheme` 三档默认走"system"

M0 不暴露"跟随系统 / 强制明亮 / 强制暗黑"切换 UI(M5 打磨阶段才加);`App()` Composable 用 `isSystemInDarkTheme()` 决定 light/dark `ColorScheme`。`darkTheme: Boolean = isSystemInDarkTheme()` 是 Material 3 标准做法。

### 6. 自定义 token:`staticCompositionLocalOf` 而不是 `compositionLocalOf`

`LocalSpacing` / `LocalCornerRadius` 等 token 在 `App()` 根节点提供一次，**整个 App 生命周期不变**，且读取频率高(每个 Composable 都可能读)。`staticCompositionLocalOf` 比 `compositionLocalOf` 快(不追踪读取栈)，适合这种"几乎不变"的场景。

替代方案(已否决):`compositionLocalOf`。否决理由:本场景 token 不变化，`compositionLocalOf` 的"细粒度重组"特性用不上，徒增开销。

### 7. 多语言:`values/` 为中文 default,`values-en/` 为英文

Android 资源 fallback 规则:`values-zh/` 才匹配中文;`values/` 是所有未匹配 locale 的 fallback。我们 v1 默认中文 + 兜底英文，所以:

- `values/strings.xml`:中文(`app_name="writing-with-ai"` 中文)
- `values-en/strings.xml`:英文(`app_name="writing-with-ai"` 英文)

跟随系统:用户切到英文系统，Android 自动挑 `values-en/`;切到中文系统，挑 `values/`。不需要代码层处理。

### 8. 硬编码中文拦截:Android Lint 内置 `HardcodedText`

Lint 规则里把 `HardcodedText` 的 severity 提到 `error`(默认是 `warning`)，不需要写自定义 Lint 规则。

### 9. ktlint:用 `org.jlleitschuh.gradle.ktlint` 插件 + 中央化 `config/ktlint/.editorconfig`

插件版本与 Kotlin 2.x 兼容性需在落 `tasks.md` 时确认(ktlint 1.x 系列对 Kotlin 2.x 有支持窗口);规则集中在 `config/ktlint/.editorconfig`,**不**在多处散落 `ktlint {}` 块。CLAUDE.md §"架构要点" 硬规定。

### 10. 测试框架配置

- **JUnit5**:通过 `org.junit.jupiter:junit-jupiter` + `useJUnitPlatform()` 启用 Jupiter 引擎。
- **MockK**:`io.mockk:mockk`(纯 JVM 与 Android 都有，本项目用 `mockk` 即可)。
- **Turbine**:`app.cash.turbine:turbine`。
- **Compose Test**:`androidx.compose.ui:ui-test-junit4` + `androidx.compose.ui:ui-test-manifest`(仅 `androidTest`)。

> 注:Android Gradle Plugin 默认 JUnit 4，要跑 JUnit5 必须显式 `tasks.withType<Test> { useJUnitPlatform() }` + 加 `junit-jupiter-engine` 到 `testRuntimeOnly`。

### 11. 占位目录策略:`.gitkeep` 而非空目录

git 不跟踪空目录。所有占位目录(`core/data/` / `feature/quicknote/` / `di/` 等)放一个 `.gitkeep` 文件，每个文件带一行注释说明该目录后续装什么(M1 / M2 / M3 / M4 装哪类代码)。

## Risks / Trade-offs

- **[JDK 17 检测]** 用户必须机器上能跑 JDK 17。Mitigation:在 `gradle/wrapper/gradle-wrapper.properties` 里不写 `org.gradle.java.home`;`CLAUDE.md` §"命令"已明确 JDK 17 要求;README(后续 change 补)会再写一次。
- **[Kotlin 2.x ↔ ktlint 兼容性]** ktlint 1.4+ 才稳定支持 Kotlin 2.x。Mitigation:`tasks.md` 里指定 ktlint 插件版本 ≥ 1.4;落地后立刻跑 `:app:ktlintCheck` 验证，失败则升级 ktlint。
- **[Hilt + KSP]** Hilt 官方在 Kotlin 2.x 之后才完整支持 KSP(早期版本用 KAPT)。Mitigation:`libs.versions.toml` 锁定 Hilt ≥ 2.51;`tasks.md` 落地时跑 `:gradlew :app:assembleDebug` 验证 Hilt 编译通过。
- **[Compose Compiler 插件 ID]** Kotlin 2.x 用 `org.jetbrains.kotlin.plugin.compose`,Kotlin 1.x 用 `kotlinCompilerExtensionVersion`。Mitigation:在 `tasks.md` 写明 plugin ID 必须用前者，文档化落点。
- **[kapt 残留风险]** 即使本工程声明不用 kapt，某些第三方库仍可能在 transitive 依赖里拉 kapt 插件。Mitigation:在 root `settings.gradle.kts` 不引入任何 kapt 插件;若后续 change 引入，需要在 review 里特别说明。
- **[Glance / OkHttp 占位依赖膨胀 APK]** M0 引入但不使用，APK 会大几 MB。Mitigation:M0 不在意 APK 大小(仅 debug);roadmap §11 性能基线在 M5 才检查 APK 体积，届时可裁剪。
- **[`enableOnBackInvokedCallback` 在不同厂商 ROM 的行为]** M0 设为 `true`(标准做法)，但国内 ROM 偶尔会有自己的 back 拦截。Mitigation:M4 的 `predictive-back-gesture` change 专门处理。
- **[`HardcodedText` lint 与"允许英文 default"冲突]** Android Lint 的 `HardcodedText` 只看是否 hardcoded,**不区分语言**;所以英文硬编码也会被标记。Mitigation:`values-en/strings.xml` 里的英文是"翻译产物",Lint 不扫描资源文件，只看 `.kt` 源码;只有 Compose 函数里写死 `Text("...")` 才会被警告——这正是我们想要的(防止业务代码散落字符串)。

## Migration Plan

本 change 是 greenfield，不存在 migration。M0 落地的"部署步骤"等同于"开发环境初始化":

1. 拉仓库。
2. 确认本机有 JDK 17(`java -version` 输出 `17.x`)。
3. 给 Gradle 包装器可执行权限:`chmod +x gradlew`(Linux/macOS)。
4. 跑 `./gradlew :app:assembleDebug` 验证编译。
5. 跑 `./gradlew :app:testDebugUnitTest` 验证测试。
6. 跑 `./gradlew :app:ktlintCheck` 验证代码风格。
7. 跑 `./gradlew :app:check` 一键跑完所有质量门。
8. 用 Android Studio 打开项目，确认 `MainActivity` 有 Compose Preview(后续 change 补)。

**回滚策略**:M0 还未提交(本 change 走 `/opsx:apply` 才会真的写文件到仓库)，回滚等价于删除本 change 的所有产物:

- 删除 `app/`、`gradle/`、`config/ktlint/`。
- 删除 `settings.gradle.kts`、根 `build.gradle.kts`、`gradle.properties`、`gradlew`、`gradlew.bat`、新增的 `.gitignore`。
- 保留 `CLAUDE.md`、`docs/`、`openspec/` 不动。

## Open Questions

- **AGP 与 Kotlin 2.x 最小版本对齐**:AGP 8.5+ 才稳定支持 Kotlin 2.0 编译器插件。Mitigation 候选:`tasks.md` 落 AGP ≥ 8.5;若用户机器上 AGP cache 旧，首次 build 慢但在预期内。
- **是否落 `gradle/libs.versions.toml.lock`**:Gradle 7.6+ 支持 version catalog lock，用于 CI 复现。**暂不引入**(roadmap §11 性能基线要求再补);后续 `polish-and-internal-release` change 评估。
- **是否生成 Compose Preview**:M0 不写业务 Composable,`App()` 函数会带一个 `@Preview` 注解用于 Android Studio 实时预览。需在 `tasks.md` 列出。
- **`gradle.properties` 关键开关**:`org.gradle.jvmargs=-Xmx4g`、`org.gradle.parallel=true`、`org.gradle.caching=true`、`kotlin.code.style=official`、`android.useAndroidX=true`、`android.nonTransitiveRClass=true`、`kotlin.incremental=true`(KSP 友好)。Mitigation 候选:统一在 `tasks.md` 落一份默认值。
- **`enableOnBackInvokedCallback` 设置位置**:写在 `MainActivity.onCreate` 还是 `App.onCreate`?Mitigation:`MainActivity.onCreate`(`enableOnBackInvokedCallback = true` 在 SDK 33+ 才生效，M0 走系统默认即可，M4 的 `predictive-back-gesture` change 细化)。
- **是否在 `app/` 加 `consumer-rules.pro`**:`proguard-rules.pro` 留给 release 配置，M0 不写;`consumer-rules.pro` 也暂不写(无依赖库需要 keep 规则)。后续 change 引入第三方库(如 Glance)时再补。