# release-readiness Specification

## Purpose

TBD — synced from OpenSpec change `release-readiness`(2026-06-20)。生产 release APK 构建 + R8 混淆 + 资源压缩 + 签名 + mapping 输出。

v1 内测 APK 分发前的最后一道关:`./gradlew :app:assembleRelease` 出可装机的签名 APK;debug 路径完全独立、不受影响。

## Requirements

### Requirement: Release variant enables R8 minify and resource shrinking

`app/build.gradle.kts` 的 `release` buildType MUST 设:
- `isMinifyEnabled = true`(启用 R8 代码混淆 + 死代码消除)
- `isShrinkResources = true`(资源压缩，未引用的 `res/` 资源剔除)
- `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")`(AGP 默认 + 项目规则叠加)
- `signingConfig = signingConfigs.getByName("release")`

`debug` buildType 保持现状(M0 + M1+ 验证过):`isMinifyEnabled = false`，无 signingConfig(走 AGP 默认 debug keystore)。

#### Scenario: Release build outputs R8-mapped APK
- **WHEN** 用户本地配置 `~/.gradle/gradle.properties` 4 个 release 凭据后跑 `./gradlew :app:assembleRelease`
- **THEN** 产物 `app/build/outputs/apk/release/app-release.apk` 存在，体积预期 < 10MB(debug ~20MB 对照，R8 + shrink 后应 < 50%);`app/build/outputs/mapping/release/mapping.txt` 存在

#### Scenario: Debug build remains unchanged
- **WHEN** 跑 `./gradlew :app:assembleDebug`
- **THEN** 走 M0+ 既定 `debug` 块，无 R8 / 资源压缩 / 签名配置;产物在 `app/build/outputs/apk/debug/app-debug.apk`，行为与 release-readiness 落地前一致

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
- **THEN** Hilt 能反射找到 `@HiltViewModel` 标注的类并构造实例，无 `ClassNotFoundException` / `NoSuchMethodException`

#### Scenario: Room DAO / Entity 反射可用
- **WHEN** `AppDatabase` 在 release APK 启动，Room 反射读 `@Dao` / `@Entity` 标注的类
- **THEN** DAO 调用 + 实体映射正常，无 R8 误删字段

#### Scenario: Glance widget 在 release APK 桌面正常渲染
- **WHEN** 用户把 2x2 / 4x2 / 1x4 widget 加到桌面
- **THEN** widget UI 正常显示，`GlanceAppWidget.provideContent` 反射调用成功，`OpenNoteAction` 回调正常

#### Scenario: kotlinx-serialization 反序列化不崩
- **WHEN** 启动 App,`AppNav` 解析 nav route 时反序列化 `@Serializable data class` / `data object`(`QuicknoteDetail` / `SettingsModelProviderDetail` 等)
- **THEN** `serializer()` 静态方法可调到，无 `SerializationException: Serializer for class ... is not found`

### Requirement: Release signing reads credentials from user-local gradle.properties

`app/build.gradle.kts` 的 `signingConfigs.create("release")` MUST 从 `~/.gradle/gradle.properties` 读 4 个字段:
- `RELEASE_STORE_FILE`(绝对路径，keystore `.jks` 文件)
- `RELEASE_STORE_PASSWORD`(keystore 密码)
- `RELEASE_KEY_ALIAS`(key alias)
- `RELEASE_KEY_PASSWORD`(key 密码)

凭据 MUST **不**入库(keystore + 4 字段均在 `~/.gradle/gradle.properties`,**不**在项目 `gradle.properties` / `app/keystore.properties` / 环境变量)。

#### Scenario: 凭据不入 git 历史
- **WHEN** `git log --all --full-history -- "**/keystore*" "**/RELEASE_STORE_PASSWORD"` 跑
- **THEN** 0 匹配(keystore 文件 + 4 字段从不入库)

#### Scenario: docs/usage/signing.md 文档化 keytool 生成流程
- **WHEN** 读 `docs/usage/signing.md`
- **THEN** 包含 `keytool -genkey -v -keystore ... -alias ... -keyalg RSA -keysize 2048 -validity 10000` 完整命令 + `~/.gradle/gradle.properties` 4 占位 + 备份提示(keystore 一旦丢失无法重签名，需用原 keystore 升 versionCode)

### Requirement: APK cold start under 2s on mid-tier device

Release APK 在中等规格真机(Pixel 4a / 小米 6 / 同档)冷启动 MUST < 2s(含 Application.onCreate + MainActivity.onCreate + 首次 Composable 渲染到第一帧)。**AI 不能跑**(本机无 release APK)，用户本地验。

#### Scenario: 冷启动基线
- **WHEN** 用户在真机 `am force-stop com.yy.writingwithai` 后从 launcher icon 启动 release APK + 拿 stopwatch 测到首屏笔记列表可见
- **THEN** 耗时 < 2s(Pixel 4a 实测基线;其他机型按 CPU 性能等比缩放)

### Requirement: R8 mapping.txt retained for crash deobfuscation

`./gradlew :app:assembleRelease` 必须在 `app/build/outputs/mapping/release/mapping.txt` 输出混淆映射表，版本控制中**不**入库，但 CI 留 artifact 上传(`actions/upload-artifact@v4` 或类似)。

#### Scenario: mapping 文件存在
- **WHEN** 跑 `./gradlew :app:assembleRelease` 成功
- **THEN** `app/build/outputs/mapping/release/mapping.txt` 文件存在，含 R8 重命名映射

#### Scenario: mapping 不入库
- **WHEN** `cat .gitignore | grep -i mapping`
- **THEN** `app/build/outputs/mapping/` 在 gitignore(默认 AGP 行为，确认)
