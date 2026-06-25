# release-readiness Specification

## Purpose

TBD - created by archiving change `polish-and-internal-release`(2026-06-20)。定义 v1 内测前的 polish umbrella:ktlint Compose PascalCase baseline / Robolectric 集成 / Compose UI test 落地 / ROM 适配笔记 / 冷启 IO dispatcher 迁移;后由 change `release-readiness`(2026-06-20)扩展 release variant 构建 / R8 / 签名 / 冷启 / mapping 输出要求。

## Requirements

### Requirement: ktlint does not flag @Composable PascalCase functions

`app/build.gradle.kts` `ktlint {}` 块 MUST disable `standard:function-naming` 规则;ktlint 1.0.x 无法选择性排除 `@Composable` 注解函数,全局禁用是唯一方案。禁用后 `./gradlew :app:ktlintCheck` MUST return 0 violations。

非 Compose 普通函数 camelCase 检查 MUST 走 Kotlin `-Xreport-all-warnings` + IDE inspection `IdentifierNaming` 兜底,不因禁用 ktlint 规则而降级。

#### Scenario: ktlintCheck zero violations
- **WHEN** `./gradlew :app:ktlintCheck` 运行
- **THEN** 返回 0 violations(`standard:function-naming` 规则被 baseline 消纳或 ktlint 关闭)

#### Scenario: Gradle ktlint baseline 消纳
- **WHEN** `app/config/ktlint/baseline.xml` 文件存在
- **THEN** 包含 25+ 个 `standard:function-naming` 规则条目被 baseline 接收

### Requirement: Robolectric dependency integrated for Android Framework tests

`gradle/libs.versions.toml` MUST 新增 `robolectric = "4.13"` version + `robolectric-core` library;`app/build.gradle.kts` MUST 加 `testImplementation(libs.robolectric.core)` + `testImplementation(libs.androidx.test.runner)` + `testImplementation(libs.androidx.compose.ui.test.junit4)`。

Robolectric test 文件 MUST 使用 `@RunWith(AndroidJUnit4::class)` + `@Config(sdk = [34])` 注解,MUST 放在 `app/src/test/java/com/yy/writingwithai/` 相应子包内,套用 JUnit5 + `runTest` 协程测试器。

#### Scenario: Robolectric test suite compiles
- **WHEN** `./gradlew :app:compileDebugUnitTestKotlin` 运行
- **THEN** 所有 Robolectric test class 正常编译;`@Config` 注解无缺

#### Scenario: Robolectric test coverage
- **WHEN** `find app/src/test -name "*RobolectricTest*"` 执行
- **THEN** 至少返回 1 个文件(`SecureApiKeyStoreRobolectricTest.kt`)

### Requirement: OnboardingScreen has Compose UI test for scroll-to-bottom unlock

`app/src/test/java/com/yy/writingwithai/feature/onboarding/OnboardingScreenUiTest.kt` MUST 包含 Compose UI test,用 `createComposeRule()` + `setActivityContent { OnboardingRoute(vm) }` 验证 `FakeConsentStore` 注入的 `OnboardingViewModel` + `LazyColumn` 滚动到底部后"同意"按钮 `enabled = true`。

UI test MUST 不依赖真实 NavHost(`OnboardingRoute` 只有 Composable 层级,由 test 直接提供所有参数);MUST 不依赖 Hilt(构造 VM 用 FakeConsentStore)。

#### Scenario: 未滚动时按钮禁用
- **WHEN** Compose UI test 启动但未执行滚动
- **THEN** "同意并继续"按钮 `onNodeWithTag("accept_button")` 的 `enabled` property 为 `false`

#### Scenario: 短文一键同意阻止
- **WHEN** `LazyColumn` 总长不满一屏,`performScrollToIndex(2)` 执行
- **THEN** `firstVisible == 0` → 同意按钮仍 disabled

### Requirement: ROM compatibility notes document covers 4 major OEMs

`docs/usage/rom-compatibility-notes.md` MUST 涵盖小米 MIUI / 华为 HarmonyOS / OPPO ColorOS / vivo OriginOS 的 widget 限制、predictive back 行为、降级方案;每 ROM 一段 3-5 行,用 markdown 表格 + 段落;末尾提供统一降级说明(用户在 widget 不可用时可走 app 内快捷入口)。

#### Scenario: ROM notes file exists and covers 4 OEMs
- **WHEN** `docs/usage/rom-compatibility-notes.md` 文件检查
- **THEN** 包含 `## 小米 MIUI` / `## 华为 HarmonyOS` / `## OPPO ColorOS` / `## vivo OriginOS` 四个 H2 段

#### Scenario: ROM notes references widget fallback
- **WHEN** `docs/usage/rom-compatibility-notes.md` 末尾段检查
- **THEN** 含"降级方案"段,说明 widget 不可用时用户可进 App 内快捷入口完成笔记操作

### Requirement: MainActivity consent check uses async IO dispatcher

`MainActivity.handleRawRoute` 内 `runBlocking { consentStore.isConsented(...) }` MUST 替换为 `lifecycleScope.launch(Dispatchers.IO) { val isConsented = consentStore.isConsented(...); withContext(Dispatchers.Main) { ... } }`;主线程不再阻塞 `runBlocking` ~50ms。

`widgetPendingRoute` 写入 State 逻辑 MUST 保持在 `withContext(Dispatchers.Main)` 块内。

#### Scenario: handleRawRoute no runBlocking
- **WHEN** `MainActivity.kt` 源码 grep `runBlocking`
- **THEN** 0 匹配(`handleRawRoute` 内无 `runBlocking`)

#### Scenario: consent check runs on IO
- **WHEN** `MainActivity` 收到 widget Intent,`lifecycleScope.launch(Dispatchers.IO)` 被执行
- **THEN** `consentStore.isConsented` 调用在 IO 调度器而非主线程;`navigate` 回到 Main 线程;主线程无阻塞

### Requirement: Release variant enables R8 minify and resource shrinking

`app/build.gradle.kts` 的 `release` buildType MUST 设:
- `isMinifyEnabled = true`(启用 R8 代码混淆 + 死代码消除)
- `isShrinkResources = true`(资源压缩,未引用的 `res/` 资源剔除)
- `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")`(AGP 默认 + 项目规则叠加)
- `signingConfig = signingConfigs.getByName("release")`

`debug` buildType 保持现状(M0 + M1+ 验证过):`isMinifyEnabled = false`,无 signingConfig(走 AGP 默认 debug keystore)。

#### Scenario: Release build outputs R8-mapped APK
- **WHEN** 用户本地配置 `~/.gradle/gradle.properties` 4 个 release 凭据后跑 `./gradlew :app:assembleRelease`
- **THEN** 产物 `app/build/outputs/apk/release/app-release.apk` 存在,体积预期 < 10MB(debug ~20MB 对照,R8 + shrink 后应 < 50%);`app/build/outputs/mapping/release/mapping.txt` 存在

#### Scenario: Debug build remains unchanged
- **WHEN** 跑 `./gradlew :app:assembleDebug`
- **THEN** 走 M0+ 既定 `debug` 块,无 R8 / 资源压缩 / 签名配置;产物在 `app/build/outputs/apk/debug/app-debug.apk`,行为与 release-readiness 落地前一致

#### Scenario: Release build without signing config fails fast
- **WHEN** 用户未配 `~/.gradle/gradle.properties` 4 个 release 凭据就跑 `./gradlew :app:assembleRelease`
- **THEN** Gradle 在 `signingConfigs.release` 解析阶段 fail,exit code 非 0,error log 显式说明缺 `RELEASE_STORE_FILE` / `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD`

### Requirement: ProGuard keep rules cover Hilt / Room / Compose / Glance / kotlinx-serialization

`app/proguard-rules.pro` MUST 含 5 段 keep 规则:

```
# ----- Hilt -----
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel
-keep,allowobfuscation @interface dagger.hilt.android.lifecycle.HiltViewModel
-keep @dagger.hilt.android.AndroidEntryPoint class *

# ----- Room -----
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# ----- Compose -----
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ----- Glance -----
-keep class androidx.glance.** { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }

# ----- kotlinx-serialization -----
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class * implements kotlinx.serialization.KSerializer {
    static **$* *;
}
-keep class com.yy.writingwithai.core.ai.api.** { *; }
-keep class com.yy.writingwithai.app.AppNavKt { *; }

# ----- App 入口 -----
-keep class com.yy.writingwithai.app.WritingApp
-keep class com.yy.writingwithai.app.MainActivity
```

#### Scenario: Hilt ViewModel 在 R8 后仍可反射构造
- **WHEN** Hilt `@HiltViewModel` 标注的 VM(`AiActionViewModel` / `QuickNoteListViewModel` / `OnboardingViewModel` 等)在 release APK 运行时被 Compose 拉起
- **THEN** Hilt 能反射找到 `@HiltViewModel` 标注的类并构造实例,无 `ClassNotFoundException` / `NoSuchMethodException`

#### Scenario: Room DAO / Entity 反射可用
- **WHEN** `AppDatabase` 在 release APK 启动,Room 反射读 `@Dao` / `@Entity` 标注的类
- **THEN** DAO 调用 + 实体映射正常,无 R8 误删字段

#### Scenario: Glance widget 在 release APK 桌面正常渲染
- **WHEN** 用户把 2x2 / 4x2 / 1x4 widget 加到桌面
- **THEN** widget UI 正常显示,`GlanceAppWidget.provideContent` 反射调用成功,`OpenNoteAction` 回调正常

#### Scenario: kotlinx-serialization 反序列化不崩
- **WHEN** 启动 App,`AppNav` 解析 nav route 时反序列化 `@Serializable data class` / `data object`(`QuicknoteDetail` / `SettingsModelProviderDetail` 等)
- **THEN** `serializer()` 静态方法可调到,无 `SerializationException: Serializer for class ... is not found`

### Requirement: Release signing reads credentials from user-local gradle.properties

`app/build.gradle.kts` 的 `signingConfigs.create("release")` MUST 从 `~/.gradle/gradle.properties` 读 4 个字段:
- `RELEASE_STORE_FILE`(绝对路径,keystore `.jks` 文件)
- `RELEASE_STORE_PASSWORD`(keystore 密码)
- `RELEASE_KEY_ALIAS`(key alias)
- `RELEASE_KEY_PASSWORD`(key 密码)

凭据 MUST **不**入库(keystore + 4 字段均在 `~/.gradle/gradle.properties`,**不**在项目 `gradle.properties` / `app/keystore.properties` / 环境变量)。

#### Scenario: 凭据不入 git 历史
- **WHEN** `git log --all --full-history -- "**/keystore*" "**/RELEASE_STORE_PASSWORD"` 跑
- **THEN** 0 匹配(keystore 文件 + 4 字段从不入库)

#### Scenario: docs/usage/signing.md 文档化 keytool 生成流程
- **WHEN** 读 `docs/usage/signing.md`
- **THEN** 包含 `keytool -genkey -v -keystore ... -alias ... -keyalg RSA -keysize 2048 -validity 10000` 完整命令 + `~/.gradle/gradle.properties` 4 占位 + 备份提示(keystore 一旦丢失无法重签名,需用原 keystore 升 versionCode)

### Requirement: APK cold start under 2s on mid-tier device

Release APK 在中等规格真机(Pixel 4a / 小米 6 / 同档)冷启动 MUST < 2s(含 Application.onCreate + MainActivity.onCreate + 首次 Composable 渲染到第一帧)。**AI 不能跑**(本机无 release APK),用户本地验。

#### Scenario: 冷启动基线
- **WHEN** 用户在真机 `am force-stop com.yy.writingwithai` 后从 launcher icon 启动 release APK + 拿 stopwatch 测到首屏笔记列表可见
- **THEN** 耗时 < 2s(Pixel 4a 实测基线;其他机型按 CPU 性能等比缩放)

### Requirement: R8 mapping.txt retained for crash deobfuscation

`./gradlew :app:assembleRelease` 必须在 `app/build/outputs/mapping/release/mapping.txt` 输出混淆映射表,版本控制中**不**入库,但 CI 留 artifact 上传(`actions/upload-artifact@v4` 或类似)。

#### Scenario: mapping 文件存在
- **WHEN** 跑 `./gradlew :app:assembleRelease` 成功
- **THEN** `app/build/outputs/mapping/release/mapping.txt` 文件存在,含 R8 重命名映射

#### Scenario: mapping 不入库
- **WHEN** `cat .gitignore | grep -i mapping`
- **THEN** `app/build/outputs/mapping/` 在 gitignore(默认 AGP 行为,确认)

### Requirement: UpdateDownloadReceiver safe filename derivation

`core/update/UpdateDownloadReceiver` MUST NOT derive the install-Intent filename from the server-side `DownloadManager.COLUMN_URI` (which is an HTTP URL whose last path segment is attacker-controlled). Instead, the filename MUST come from a manifest field (`manifest.apkName`) and MUST pass `PathSafety.SAFE_NAME` (`Regex("^[A-Za-z0-9._-]{1,128}$")`). If the manifest does not provide `apkName` or the value fails the regex, the receiver MUST fall back to the literal string `"update.apk"` and log a warning.

#### Scenario: Manifest filename passes
- **WHEN** `AppUpdateManifest.apkName = "writing-with-ai-1.2.3.apk"` and the download completes
- **THEN** the install Intent's `EXTRA_STREAM` `File` resolves to `<external-files>/app-update/writing-with-ai-1.2.3.apk`

#### Scenario: Malicious manifest filename rejected
- **WHEN** `AppUpdateManifest.apkName = "../../../etc/passwd"` (or any value not matching `SAFE_NAME`)
- **THEN** the receiver falls back to `"update.apk"` and logs `Log.w(TAG, "manifest.apkName unsafe, fallback to update.apk")`

#### Scenario: No URL substringAfterLast
- **WHEN** reading `UpdateDownloadReceiver.kt`
- **THEN** grep for `substringAfterLast` against `COLUMN_URI` MUST return 0 matches

### Requirement: UpdateDownloadReceiver cursor getColumnIndex null / range check

`UpdateDownloadReceiver` MUST guard every `cursor.getColumnIndex(...)` call against the documented `-1` return value (when the column is not present in the cursor). Acceptable patterns: `cursor.getColumnIndexOrThrow(COLUMN_URI)` (throws a typed exception caught at the boundary) OR an explicit `if (idx < 0) return / continue / bail` after retrieving the index. Bare `cursor.getString(cursor.getColumnIndex(...))` MUST NOT appear.

#### Scenario: Missing column handled
- **WHEN** the cursor does not contain `COLUMN_URI` for the given download id
- **THEN** the receiver logs `Log.w(TAG, "COLUMN_URI missing")` and exits early without throwing `IndexOutOfBoundsException`

#### Scenario: Valid column proceeds
- **WHEN** the cursor contains `COLUMN_URI` and `COLUMN_LOCAL_URI`
- **THEN** the receiver extracts both values and proceeds to SHA verification

#### Scenario: Lint Range error fixed
- **WHEN** `./gradlew :app:lintDebug` runs
- **THEN** the `Range` error on `UpdateDownloadReceiver.kt:59-60` MUST NOT appear in the lint report

## ADDED Requirements

### Requirement: UpdateDownloadReceiver SHA-256 off main thread

`core/update/UpdateDownloadReceiver.onReceive(context, intent)` MUST NOT run SHA-256 on the system broadcast thread (causes ANR for > 50 MB APK). The receiver MUST call `goAsync()` to obtain a `PendingResult`, then `withContext(Dispatchers.IO)` for the SHA computation, then `pendingResult.finish()` once done.

#### Scenario: SHA computation off main
- **WHEN** APK download completes and receiver is invoked
- **THEN** grep `UpdateDownloadReceiver.kt` shows `goAsync()` + `Dispatchers.IO`; main thread returns within milliseconds

#### Scenario: goAsync timeout handled
- **WHEN** SHA computation exceeds 10s (Android `goAsync` limit)
- **THEN** receiver logs `WARN: SHA timeout` and finishes the pending result without installing
