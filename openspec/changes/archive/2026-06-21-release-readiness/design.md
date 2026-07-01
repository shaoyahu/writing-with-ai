# Design: release-readiness

## Context

`main` 当前状态(2026-06-20):
- `assembleDebug` / `ktlintCheck` / `testDebugUnitTest` 全过(fix-m5-blockers 落地)
- `app/build.gradle.kts` `release { isMinifyEnabled = false }` + 无 `proguardFiles` + 无 `signingConfig` + 无 `proguard-rules.pro`
- 调试 APK ~20MB，无 R8 优化
- M0 设计 token 拍板:`allowBackup="false"`(保留)— v1 接受"备份关闭"换"apikey 不外流"

CLAUDE.md §"架构要点" v1 路线:`assembleDebug` + `assembleRelease` 都需能跑通。当前 release 路径**完全没配** — `assembleRelease` 会因无签名 + 无 minify 规则失败。v1 内测 APK 分发前必须补 release 配置。

签名凭据从 `~/.gradle/gradle.properties` 读(不入库，本机维护);AI 跑不通过 `assembleRelease`(无 keystore)，只验 debug 路径不破坏 + proguard 规则语法 + 配置正确性。

## Goals / Non-Goals

**Goals**:
- `./gradlew :app:assembleRelease` 出可分发 APK(用户本地配置签名后)
- R8 启用 + 资源压缩(APK 体积 < 10MB,debug ~20MB 对照)
- proguard-rules.pro 集中管 keep 规则(Hilt / Room / Compose / Glance / OkHttp / kotlinx-serialization)
- 签名配置从 `~/.gradle/gradle.properties` 读，keystore 不入库
- docs/usage/signing.md 文档化 keytool + gradle.properties 配置流程
- debug variant 行为不变(已 green)

**Non-Goals**:
- 不上架 Play Store / 国内应用市场(roadmap §0 拍板)
- 不跑真机 release APK 冷启动 / frame timing 基准(本机无 release keystore)
- 不写 release 凭据生成脚本(keystore 一次性，人工 `keytool` 一次)
- 不引入 Crashlytics / Firebase / 任何 release-only SDK
- 不动 `allowBackup="false"` 配置
- 不动代码(本 change 仅 gradle / 文档 / proguard 规则)

## Decisions

### D1 — `signingConfigs.release` 凭据从 `~/.gradle/gradle.properties` 读

```kotlin
// app/build.gradle.kts
android {
    signingConfigs {
        create("release") {
            val props = java.util.Properties().apply {
                val f = file(System.getProperty("user.home") + "/.gradle/gradle.properties")
                if (f.exists()) f.inputStream().use { load(it) }
            }
            storeFile = props.getProperty("RELEASE_STORE_FILE")?.let { file(it) }
            storePassword = props.getProperty("RELEASE_STORE_PASSWORD")
            keyAlias = props.getProperty("RELEASE_KEY_ALIAS")
            keyPassword = props.getProperty("RELEASE_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

**替代方案对比**:
- 方案 A(选):`~/.gradle/gradle.properties`(用户本地)→ 不入库，keystore 路径灵活(`~/keystore/` 或 `~/Documents/` 都能放)
- 方案 B(弃):`app/keystore.properties`(项目内，gitignore)→ 易误 commit;`.gitignore` 规则需精确
- 方案 C(弃):环境变量(`KEYSTORE_PASSWORD` 等)→ 用户每次开 IDE 需重设;CI 友好但本机开发不友好

**理由**:CLAUDE.md §"AI 集成约定" 强调 apikey 加密不入库 — 签名凭据同理。`~/.gradle/gradle.properties` 是 Android Studio / Gradle 默认凭据文件，生态成熟。

### D2 — proguard 规则分 5 组，集中 `proguard-rules.pro`

```pro
# ----- Hilt -----
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel
-keep,allowobfuscation @interface dagger.hilt.android.lifecycle.HiltViewModel
-keep @dagger.hilt.android.AndroidEntryPoint class *

# ----- Room -----
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# ----- Compose (M3 编译器默认 + Composable 反射) -----
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
-keep class com.yy.writingwithai.app.AppNavKt { *; }  // @Serializable data object / class

# ----- App 入口 -----
-keep class com.yy.writingwithai.app.WritingApp
-keep class com.yy.writingwithai.app.MainActivity
```

**替代方案对比**:
- 方案 A(选):每库独立 `-keep` 段，带中文注释说明 → 易 review
- 方案 B(弃):`-keep class ** { *; }` 全 keep → R8 失效，APK 不缩
- 方案 C(弃):用第三方 proguard 库(`com.guardsquare:proguard-gradle`)→ 引入新依赖，版本 catalog 改动，scope 蔓延

**理由**:每个 keep 段都对应一个 CLAUDE.md / build.gradle 显式声明的依赖，scope 最小。

### D3 — `assembleRelease` 验证边界:本机 AI 跑 + 用户跑

本机无 release keystore → AI 跑 `assembleRelease` 必失败(签名读不到)，但**配置正确性**可静态验:
- `app/build.gradle.kts` `signingConfigs.release` 块存在 + 4 个属性从 `gradle.properties` 读
- `proguardFiles` 含 `proguard-android-optimize.txt` + `proguard-rules.pro`
- `app/proguard-rules.pro` 文件存在 + 关键 keep 段都在
- `docs/usage/signing.md` 文档存在

**AI 能跑**:静态 grep + 语法验证(读 build.gradle.kts 解析)
**用户跑**:`./gradlew :app:assembleRelease` 实跑 + 安装到真机 + 走核心流程 → 验 R8 没误 keep / 资源压缩没误删 / 签名 APK 能装

**协议**:`proposal.md` "验证" 段已写明"本机 AI 跑不通过，只能验 debug 路径不破坏"。

### D4 — `isShrinkResources = true` 单独不依赖 `isMinifyEnabled`?

`isShrinkResources` 在 R8 (即 `isMinifyEnabled = true`) 启用时才能跑。AGP 8.x 文档:`isShrinkResources = true` 隐含 `isMinifyEnabled = true`。同时设是显式表达，可读性更好，无副作用。

## Risks / Trade-offs

- [R1] 签名凭据从 `~/.gradle/gradle.properties` 读 → 用户必须先配，否则 `assembleRelease` 失败。→ 缓解:`docs/usage/signing.md` 详细写 keytool 步骤 + 占位说明。
- [R2] R8 keep 规则不全 → release APK 运行时崩(NPE / ClassNotFound)。→ 缓解:用 ProGuard 官方 Android optimize 模板 + 各库官方 keep 规则;release 后第一时间真机 smoke test(fix-m5-blockers 的 MockWebServer 端到端测试是好的 smoke 候选)。
- [R3] `isShrinkResources` 误删资源 → Glance widget `widget_initial.xml` / `widget_preview.xml` 不可见。→ 缓解:`res/raw/keep.xml` 显式列;`strings.xml` 自动 keep。
- [R4] 5 个 keep 段会随依赖升级失效。→ 缓解:依赖升级时 review keep 规则;CI 跑 `assembleRelease` + 真机 smoke 兜底。
- [R5] 本机 AI 不能跑 `assembleRelease` 端到端验 → 漏 keep / 漏资源的问题只能 release 后真机发现。→ 缓解:文档显式标"实跑需用户本地配签名";proposal "验证" 段已声明此 caveat。
- [R6] `proguardFiles` 包含 `proguard-android-optimize.txt`(AGP 默认) + 项目 `proguard-rules.pro`。optimize 模板比 default 模板激进(优化更多)，可能误 keep 误优化。→ 缓解:沿用 AGP 默认推荐，出问题再切 default。

## Migration Plan

1. **改 `app/build.gradle.kts`**:加 `signingConfigs.release` + `release {}` 块 4 字段;不动 debug 块。
2. **新建 `app/proguard-rules.pro`**:5 段 keep，带中文注释。
3. **新建 `docs/usage/signing.md`**:`keytool` 步骤 + `gradle.properties` 4 占位 + 备份提示。
4. **验证**(AI 跑):
   - `./gradlew :app:assembleDebug` 仍 BUILD SUCCESSFUL
   - `./gradlew :app:ktlintCheck` 仍 0 violation
   - `./gradlew :app:testDebugUnitTest` 仍全部 PASS
   - `grep` 静态验 `proguardFiles` 引用 + `signingConfigs.release` 4 字段
5. **回滚**:本 change 集中 `app/build.gradle.kts` + 2 个新文件，`git revert <commit>` 即可;debug 路径完全独立。
6. **用户跑(本机无 keystore 不验，仅描述)**:`./gradlew :app:assembleRelease` + `app/build/outputs/apk/release/app-release.apk` 装真机 + 走核心流程(创建笔记 / AI 扩写 / 模型管理 / widget)。

## Open Questions

- OQ1:`build.gradle.kts` 用 `Properties().apply { file(...) }` 读 `~/.gradle/gradle.properties`，但 `gradle.properties` 在 `~/.gradle/` 而**不是**项目根 — 路径硬编码 `System.getProperty("user.home")` 跨平台 OK 但 Gradle 推荐用 `gradleUserHomeDir` 属性。两者等价，选 hardcode 简单可读。
- OQ2:keystore 路径用绝对路径(`RELEASE_STORE_FILE=/Users/.../release.keystore`)还是相对 `~/.gradle/` 路径(`RELEASE_STORE_FILE=keystore/release.keystore`)?绝对路径灵活(keystore 放 `~/Documents/keystore/` 也行)，用户易记;Gradle 推荐相对。选绝对路径(用户文档示例也是绝对)。
- OQ3:`isShrinkResources` 配合 Glance widget 资源，需不需要 `res/raw/keep.xml` 列 widget 资源?在 Android 13+ launcher widget picker 显示需 previewLayout，压缩后保留策略默认正确，**大概率不需要** `keep.xml`。apply 时 grep 验 widget_initial / widget_preview 在 APK `aapt2 dump` 输出里。
