## Why

仓库目前只有 `CLAUDE.md`、`docs/`、`openspec/` 三个目录,还没有 Android 工程脚手架。`docs/plans/writing-with-ai-mobile-roadmap.md` 已经把 v1 的技术栈、架构、目录结构和里程碑(M0~M5)拍板,但**没有任何可编译的代码**——后续每个 change(`quick-note-feature` / `ai-abstraction-layer` / 等)都要在 `app/` 模块里落地,没有脚手架就无从开始。M0 的目标就是把工程骨架 + 基础能力一次拉起来,让后续 change 只需"加代码"而不是"边写代码边搭工程"。

## What Changes

- 新建 Gradle 8 单模块工程,Version Catalog(`gradle/libs.versions.toml`)统一管依赖;`./gradlew assembleDebug` 出 APK,`./gradlew test` 跑通单测。
- 接入 Kotlin 2.x、Jetpack Compose Compiler、Material 3、Hilt + KSP、Room、DataStore、OkHttp、Glance(仅配置,不写 widget 代码——M4 才有 widget)。
- 配置 ktlint(规则在 `config/ktlint/.editorconfig`)+ `gradlew :app:ktlintCheck` 跑通;CI 入口 `./gradlew :app:check`。
- 落地测试框架:**JUnit5 + MockK + Turbine + Compose Test**;提供一个最小的占位测试,验证 `test` task 可跑。
- 落地应用入口骨架:`WritingApp : Application`(`@HiltAndroidApp`)、`MainActivity : ComponentActivity`(`setContent { App() }`)、`AppNav.kt` 含一个空 NavHost(无业务路由,只为后续 change 留位)。
- 落地 Material 3 主题:light / dark / system 三档;颜色 / 字体 / 形状走 `MaterialTheme.*`,不写 hex / sp。
- 落地多语言骨架:`res/values/strings.xml`(中文 default)+ `res/values-en/strings.xml`(英文),跟随系统语言;**只放最少占位字符串**(app_name 等),业务字符串由后续 change 补充。
- 不引入业务逻辑(无 Note / AI / Widget 代码);所有 `core/*` / `feature/*` / `di/` 包只创建空目录(或一个 `.gitkeep` 文件),不写任何类。

## Capabilities

### New Capabilities

- `android-build-system`: Gradle 8 + Version Catalog + AGP + Kotlin 2.x + KSP + Compose Compiler + Hilt + Room/DataStore + OkHttp + Glance + ktlint 的依赖和插件配置;`assembleDebug` 与 `test` task 可跑通。
- `app-shell`: `WritingApp`(`@HiltAndroidApp`)+ `MainActivity`(`ComponentActivity` + `setContent { App() }`)+ `AppNav.kt` 空 NavHost;`App()` 承载整个应用根 Composable。
- `material-theme`: Material 3 ColorScheme / Typography / Shape;light / dark / system 三档;`CompositionLocal` 提供统一圆角 / 间距 token。
- `localization`: `values/strings.xml`(中文 default)+ `values-en/strings.xml`,跟随系统语言;占位 `app_name` 等最少字符串;Lint 规则禁止 Compose 内硬编码中文字符串。
- `testing-framework`: JUnit5 + MockK + Turbine + Compose Test 依赖配置,示例测试(纯 JVM)跑通;`./gradlew :app:testDebugUnitTest` 全绿。

### Modified Capabilities

无(首个 change,`openspec/specs/` 当前为空,不存在要改的需求)。

## Impact

- **新增目录**:
  - `app/`(Android module)
  - `app/src/main/java/com/yy/writingwithai/`(包名根,见下方)
  - `app/src/main/res/{values,values-en,drawable,mipmap-*,xml}/`
  - `app/src/test/java/.../`、`app/src/androidTest/java/.../`
  - `gradle/`(Version Catalog)
  - `config/ktlint/`(ktlint 规则)
- **新增根文件**:`settings.gradle.kts`、`build.gradle.kts`(根)、`gradle.properties`、`gradlew`、`gradlew.bat`、`.gitignore`。
- **包结构占位**(只建空目录或 `.gitkeep`,不写代码):
  ```
  app/src/main/java/com/yy/writingwithai/
  ├── app/        # WritingApp / MainActivity / AppNav / theme
  ├── core/       # data / prefs / ai / net / widget / common
  ├── feature/    # quicknote / aiwriting / settings / onboarding
  └── di/         # Hilt Module 集中点
  ```
- **新依赖**(全部走 Version Catalog):
  - 构建:AGP 8.x、Kotlin 2.x、Compose Compiler、ksp、Hilt、AndroidX core/appcompat/activity-compose/lifecycle/navigation-compose/datastore-preferences、Room、OkHttp、Glance。
  - 测试:JUnit5、MockK、Turbine、androidx.test、compose-ui-test-junit4。
- **新脚本命令**(`CLAUDE.md` 已列):`./gradlew :app:assembleDebug` / `testDebugUnitTest` / `connectedDebugAndroidTest` / `check` / `ktlintCheck` / `ktlintFormat`。
- **新环境要求**:JDK 17(在 `gradle.properties` 配 `org.gradle.java.home` 不写死,跟系统默认即可;`CLAUDE.md` 已定)。
- **隐私 / 安全**:M0 不涉及 apikey、不涉及网络请求;**不**引入任何第三方分析或崩溃上报 SDK。
- **多语言**:本 change 落 `values/` + `values-en/` 双目录;**业务字符串严禁硬编码中文**的 Lint 规则在 M0 一并落地(在 `app/build.gradle.kts` 的 `lint {}` 块)。
- **签名**:不上架任何应用市场,本 change 不配置 release 签名;`assembleDebug` 直接用 debug keystore(由 AGP 自动生成)。