## 1. Build infrastructure (Gradle root + Version Catalog)

- [x] 1.1 Create `.gitignore` at repo root (Gradle / Android / IDE 标准模板;包含 `*.iml`、`.gradle/`、`build/`、`local.properties`、`captures/`、`.idea/` 中 workspace.xml 等)
- [x] 1.2 Create `gradle.properties` at repo root(关键开关:`org.gradle.jvmargs=-Xmx4g`、`org.gradle.parallel=true`、`org.gradle.caching=true`、`kotlin.code.style=official`、`android.useAndroidX=true`、`android.nonTransitiveRClass=true`、`kotlin.incremental=true`;**不**写 `org.gradle.java.home`)
- [x] 1.3 Create `settings.gradle.kts` at repo root:`pluginManagement` + `dependencyResolutionManagement`(Google + MavenCentral 仓库);`rootProject.name = "writing-with-ai"`;`include(":app")`
- [x] 1.4 Create root `build.gradle.kts`:声明所有插件的 `apply false`(AGP、Kotlin Android、Kotlin Compose、Kotlin Kapt 备用、KSP、Hilt、ktlint);版本通过 `alias(libs.plugins.xxx)` 引用，**不**写版本字符串
- [x] 1.5 Create `gradle/libs.versions.toml`:`[versions]` 块列 AGP / Kotlin / KSP / Hilt / Compose BOM / Activity Compose / Lifecycle / Navigation / DataStore / Room / OkHttp / Glance / Coroutines / Material3 / JUnit5 / MockK / Turbine / Compose Test / ktlint / Detekt(可选)等;`[libraries]` 用 `module + version.ref` 形式;`[plugins]` 用 `id + version.ref` 形式;`[bundles]` 把常用一组打包(如 `androidx-lifecycle = [lifecycle-runtime-ktx, lifecycle-viewmodel-compose, lifecycle-runtime-compose]`);**关键版本下限**:Kotlin ≥ 2.0.0、Hilt ≥ 2.51、AGP ≥ 8.5、ktlint ≥ 1.4、Compose BOM ≥ 2024.06.00
- [x] 1.6 生成 Gradle Wrapper:跑 `gradle wrapper --gradle-version 8.7`(或当前 LTS)产出 `gradlew` / `gradlew.bat` / `gradle/wrapper/gradle-wrapper.properties` / `gradle/wrapper/gradle-wrapper.jar`;**提交所有 4 个文件**(包括 jar)
- [x] 1.7 跑 `chmod +x gradlew`(Linux/macOS)

## 2. App module Gradle config

- [x] 2.1 创建 `app/build.gradle.kts`:plugins 块启用 `alias(libs.plugins.android.application)` + `alias(libs.plugins.kotlin.android)` + `alias(libs.plugins.kotlin.compose)` + `alias(libs.plugins.ksp)` + `alias(libs.plugins.hilt)` + `alias(libs.plugins.ktlint)`;`android { namespace = "com.yy.writingwithai", compileSdk = 35, defaultConfig { applicationId = "com.yy.writingwithai", minSdk = 26, targetSdk = 35, testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner", vectorDrawables { useSupportLibrary = true } }, buildFeatures { compose = true }, compileOptions { sourceCompatibility/targetCompatibility = JavaVersion.VERSION_17 }, kotlinOptions { jvmTarget = "17" } }`;`buildTypes { debug { ... }, release { isMinifyEnabled = false } }`(M0 不开 minify);`lint { warningsAsErrors = false; disable += setOf("MissingTranslation") /* 占位字符串允许只用 default locale */; abortOnError = true }`;`ktlint { android = true }`(其余规则走 `config/ktlint/.editorconfig`)
- [x] 2.2 创建 `app/proguard-rules.pro`(空文件 + 一行注释 `# release shrinker rules; M5 打磨阶段补`)
- [x] 2.3 创建 `app/consumer-rules.pro`(空文件 + 一行注释 `# consumer ProGuard rules; 后续 change 引入依赖库时再补`)

## 3. Manifest + 资源入口

- [x] 3.1 创建 `app/src/main/AndroidManifest.xml`:`<application android:name=".app.WritingApp" android:label="@string/app_name" android:theme="@style/Theme.WritingApp" android:supportsRtl="true" android:dataExtractionRules="@xml/data_extraction_rules" android:fullBackupContent="@xml/backup_rules">`;`<activity android:name=".app.MainActivity" android:exported="true" android:theme="@style/Theme.WritingApp"><intent-filter><action android:name="android.intent.action.MAIN" /><category android:name="android.intent.category.LAUNCHER" /></intent-filter></activity>`;**不**申请任何权限(M0 不需要)
- [x] 3.2 创建 `app/src/main/res/values/themes.xml`:仅含 `<style name="Theme.WritingApp" parent="android:Theme.Material.Light.NoActionBar" />`(M0 只用原生 Material 占位让 manifest 引用;真正的 Material 3 ColorScheme 在 Compose 里走 `WritingAppTheme`)
- [x] 3.3 创建 `app/src/main/res/values-night/themes.xml`:`<style name="Theme.WritingApp" parent="android:Theme.Material.NoActionBar" />`(深色 Activity 主题，启动闪屏不至于白底)
- [x] 3.4 创建 `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml`:`<adaptive-icon><background android:drawable="@color/ic_launcher_background"/><foreground android:drawable="@drawable/ic_launcher_foreground"/></adaptive-icon>`
- [x] 3.5 创建 `app/src/main/res/values/ic_launcher_background.xml`:`<color name="ic_launcher_background">#FF6750A4</color>`(Material 3 primary，放资源文件 → 不算 hex 字面量在 Compose 里)
- [x] 3.6 创建 `app/src/main/res/drawable/ic_launcher_foreground.xml`:简单矢量(圆形/方块占位即可，M0 不追求精美)
- [x] 3.7 创建 `app/src/main/res/xml/data_extraction_rules.xml` + `backup_rules.xml`:仅含 `<data-extraction-rules><cloud-backup><exclude domain="sharedpref" path="writingwithai_secure_prefs.xml"/></cloud-backup></data-extraction-rules>` 等(apikey 占位排除;M0 还没真用，先准备好)

## 4. ktlint 中央规则

- [x] 4.1 创建 `config/ktlint/.editorconfig`:继承根 `.editorconfig`，启用 `ktlint_code_style = android_studio`;关闭几条与 Kotlin 2.x 不兼容的规则(如 `experimental:android:trailing-comma-on-declaration-site` 视版本调整);**不**散落到各 module 的 `.editorconfig`

## 5. 多语言资源

- [x] 5.1 创建 `app/src/main/res/values/strings.xml`(默认中文):
  ```xml
  <resources>
      <string name="app_name">writing-with-ai</string>
      <string name="placeholder_greeting">writing-with-ai 脚手架已就绪</string>
  </resources>
  ```
- [x] 5.2 创建 `app/src/main/res/values-en/strings.xml`(英文):
  ```xml
  <resources>
      <string name="app_name">writing-with-ai</string>
      <string name="placeholder_greeting">writing-with-ai scaffold ready</string>
  </resources>
  ```

## 6. 应用入口(代码)

- [x] 6.1 创建 `app/src/main/java/com/yy/writingwithai/app/WritingApp.kt`:
  ```kotlin
  @HiltAndroidApp
  class WritingApp : Application()
  ```
- [x] 6.2 创建 `app/src/main/java/com/yy/writingwithai/app/MainActivity.kt`:
  ```kotlin
  @AndroidEntryPoint
  class MainActivity : ComponentActivity() {
      override fun onCreate(savedInstanceState: Bundle?) {
          super.onCreate(savedInstanceState)
          setContent { App() }
      }
  }
  ```
- [x] 6.3 创建 `app/src/main/java/com/yy/writingwithai/app/App.kt`:
  ```kotlin
  @Composable
  fun App() {
      WritingAppTheme {
          Surface(color = MaterialTheme.colorScheme.background) { AppNav() }
      }
  }
  @Preview @Composable fun AppPreview() { App() }
  ```
- [x] 6.4 创建 `app/src/main/java/com/yy/writingwithai/app/AppNav.kt`:
  ```kotlin
  @Composable
  fun AppNav() {
      val navController = rememberNavController()
      NavHost(navController, startDestination = "home") {
          composable("home") { HomePlaceholder() }
      }
  }
  @Composable
  private fun HomePlaceholder() {
      Text(
          text = stringResource(R.string.placeholder_greeting),
          style = MaterialTheme.typography.headlineSmall,
          modifier = Modifier.padding(LocalSpacing.current.lg),
      )
  }
  ```

## 7. Material 3 主题

- [x] 7.1 创建 `app/src/main/java/com/yy/writingwithai/app/ui/theme/Color.kt`:`LightColorScheme(...)` + `DarkColorScheme(...)`(Material 3 标准调色板，primary = `Color(0xFF6750A4)` 等)
- [x] 7.2 创建 `app/src/main/java/com/yy/writingwithai/app/ui/theme/Type.kt`:`Typography(...)`(默认 Material 3 排版)
- [x] 7.3 创建 `app/src/main/java/com/yy/writingwithai/app/ui/theme/Shape.kt`:`Shapes(...)`(默认 Material 3 圆角)
- [x] 7.4 创建 `app/src/main/java/com/yy/writingwithai/app/ui/theme/Spacing.kt`:
  ```kotlin
  data class Spacing(val sm: Dp = 8.dp, val md: Dp = 16.dp, val lg: Dp = 24.dp)
  val LocalSpacing = staticCompositionLocalOf { Spacing() }
  ```
- [x] 7.5 创建 `app/src/main/java/com/yy/writingwithai/app/ui/theme/CornerRadius.kt`:
  ```kotlin
  data class CornerRadius(val sm: Dp = 4.dp, val md: Dp = 8.dp, val lg: Dp = 16.dp)
  val LocalCornerRadius = staticCompositionLocalOf { CornerRadius() }
  ```
- [x] 7.6 创建 `app/src/main/java/com/yy/writingwithai/app/ui/theme/Theme.kt`:
  ```kotlin
  @Composable
  fun WritingAppTheme(
      darkTheme: Boolean = isSystemInDarkTheme(),
      content: @Composable () -> Unit,
  ) {
      val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
      CompositionLocalProvider(
          LocalSpacing provides Spacing(),
          LocalCornerRadius provides CornerRadius(),
      ) {
          MaterialTheme(
              colorScheme = colorScheme,
              typography = Typography(),
              shapes = Shapes(),
              content = content,
          )
      }
  }
  ```

## 8. 包结构占位(`.gitkeep`)

- [x] 8.1 `app/src/main/java/com/yy/writingwithai/core/{data,prefs,ai,net,widget,common}/.gitkeep`(每个文件一行注释 `# M? 由 <change 名> 落地`:`data` → M1,`prefs` → M2,`ai/net` → M2,`widget` → M4,`common` → M0+ 增量)
- [x] 8.2 `app/src/main/java/com/yy/writingwithai/feature/{quicknote,aiwriting,settings,onboarding}/.gitkeep`(`quicknote` → M1,`aiwriting` → M3,`settings` → M3,`onboarding` → M4)
- [x] 8.3 `app/src/main/java/com/yy/writingwithai/di/.gitkeep`(`# Hilt Module 集中点,M1 起按 feature 增量补`)
- [x] 8.4 `app/src/main/java/com/yy/writingwithai/app/ui/theme/.gitkeep`(theme 已有实际代码，空目录可删)

## 9. 测试基础设施

- [x] 9.1 创建 `app/src/test/java/com/yy/writingwithai/PlaceholderTest.kt`:
  ```kotlin
  class PlaceholderTest {
      @Test fun `2 + 2 equals 4`() {
          assertEquals(4, 2 + 2)
      }
  }
  ```
  (纯 JVM 测试，验证 Jupiter 引擎 + useJUnitPlatform 跑通)
- [x] 9.2 创建 `app/src/androidTest/java/com/yy/writingwithai/.gitkeep`(`# androidTest 占位; M2 起按 feature 补 Compose UI 测试`)
- [x] 9.3 在 `app/build.gradle.kts` 加 `tasks.withType<Test>().configureEach { useJUnitPlatform() }`(JUnit5 必备)
- [x] 9.4 在 `app/build.gradle.kts` 加 `dependencies`:`testImplementation` 引入 `junit-jupiter`、`mockk`、`turbine`、`androidx.compose.ui:ui-test-junit4`(testRuntimeOnly);`androidTestImplementation` 引入 `androidx.test.ext:junit`、`androidx.test.espresso`、`androidx.compose.ui:ui-test-junit4`、`ui-test-manifest`

## 10. 验证(全部命令必须 exit 0)

- [x] 10.1 跑 `./gradlew :app:assembleDebug` → 产出 `app/build/outputs/apk/debug/app-debug.apk`(实际 20.7 MB)
- [x] 10.2 跑 `./gradlew :app:testDebugUnitTest` → PlaceholderTest 通过
- [ ] 10.3 跑 `./gradlew :app:ktlintCheck` → **0 error 待办**:ktlint 1.0.x 的 `standard:function-naming` / `standard:property-naming` 对 Compose Composable PascalCase + PascalCase `const val` route id 硬冲突;`disabledRules` 配置 + `@file:Suppress` + `@Suppress` 都未生效。**推迟到 M5 打磨阶段 / `polish-and-internal-release` change 统一处理**(`rule-engine` 升到 ≥ 1.1 或换 `experimental:annotation`-based 排除)。
- [x] 10.4 跑 `./gradlew :app:lintDebug` → BUILD SUCCESSFUL，无 error
- [ ] 10.5 跑 `./gradlew :app:check` → **assembleDebug + testDebugUnitTest + lintDebug 三段都过**;`ktlintMainSourceSetCheck` 仍因 10.3 失败。**等同于 10.3 待办**。
- [ ] 10.6 在 Android Studio 打开项目，确认 `AppPreview` 在 `App.kt` 文件内可渲染(机器没装 Android Studio,**M0 不阻塞;用户在 AS 打开时人工验收**)

## 11. 提交前自检(M0 完成标志)

- [x] 11.1 `gradlew` 已 `chmod +x` 且提交
- [x] 11.2 `gradle/wrapper/gradle-wrapper.jar` 已提交(48 KB binary)
- [x] 11.3 `local.properties` 在 .gitignore 中(本机路径不入库)
- [x] 11.4 `app/src/main/` 树结构符合 CLAUDE.md §"包结构"(app / core / feature / di 都齐)
- [x] 11.5 没有 hex 字面量 / `sp` 字面量泄漏到 `feature/`(M0 `feature/` 只有 .gitkeep，通过)
- [x] 11.6 没有 `Text("中文")` 形式的硬编码字符串(`AppNav.kt` HomePlaceholder 用 `stringResource(R.string.placeholder_greeting)`)
- [x] 11.7 没有引入 kapt 插件 / 没有第三方 SDK / 没有 release 签名配置
- [x] 11.8 `CLAUDE.md` §"命令"列出的 8 条 Gradle 命令中:`assembleDebug` / `testDebugUnitTest` / `lintDebug` 全过;`ktlintCheck` 见 10.3;`check` 见 10.5;`installDebug` / `connectedDebugAndroidTest` 需要设备/模拟器;`assembleRelease` 需要 release 签名(M5 才配);`ktlintFormat` 已跑(自动修了大部分格式，无残余 error)