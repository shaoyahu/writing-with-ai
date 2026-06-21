## Why

M5 polish + fix-m5-blockers 落地后,`main` 已 green(`assembleDebug` / `ktlintCheck` / `testDebugUnitTest` 全过),但 `app/build.gradle.kts` `release { isMinifyEnabled = false }` + 无 `proguard-rules.pro` + 无 release 签名配置 → 生产构建缺 R8 混淆 + 资源压缩 + 签名,v1 内测发 APK 之前必须收口。本 change 把 release 路径配齐,让 `./gradlew :app:assembleRelease` 出可分发的 APK。

## What Changes

- **`app/build.gradle.kts` `release {}` 块**:`isMinifyEnabled = true` + `isShrinkResources = true` + `signingConfig = signingConfigs.release`(从 `~/.gradle/gradle.properties` 读 `RELEASE_STORE_FILE` / `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD`)
- **新建 `app/proguard-rules.pro`**:
  - Hilt keep: `-keep class dagger.hilt.** { *; }` + `-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel`
  - Room keep: `-keep class * extends androidx.room.RoomDatabase` + `-keep @androidx.room.Entity class *`
  - Compose keep: Compose 编译器默认规则 + 反射 `Composable` 注解保留
  - Glance keep: `-keep class androidx.glance.** { *; }` + `GlanceAppWidgetReceiver` 子类
  - OkHttp / Okio keep:默认规则(OkHttp 4.x 自带)
  - kotlinx-serialization keep:`-keepattributes *Annotation*, InnerClasses` + `@Serializable` 类 / 伴生 `Companion` / `serializer()` 保留
  - 应用主类 + 入口 Activity keep
- **`gradle.properties` 注释**:`RELEASE_STORE_FILE` / `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD` 4 个占位(签名 keystore 不入库;`~/.gradle/gradle.properties` 由用户本地维护)
- **新建 `docs/usage/signing.md`**:`keytool` 生成 release keystore 步骤 + `~/.gradle/gradle.properties` 配置 + 备份提示 + v1 内测分发用 debug 签名兜底
- **`app/build.gradle.kts` `lintOptions {}` 升 `lint { }`**:`warningsAsErrors = false` 保持 + `abortOnError = true` 保持(已在);新增 `release` variant Lint 跑通(避免 release 构建被 lint 阻断)
- **APK 体积 / 启动时间 sanity check**:`./gradlew :app:assembleRelease` 出 APK 记录 `app/build/outputs/apk/release/app-release.apk` 大小;冷启动 < 2s(Pixel 4a 实测基线,本机不测,留 spec 描述)
- **加 R8 mapping 验证**:`./gradlew :app:assembleRelease` 跑通 + R8 mapping 输出到 `app/build/outputs/mapping/release/mapping.txt`;CI 留 mapping 上传 artifact 兜底

**非 BREAKING**:仅 release variant 配置变更,debug variant 行为不变;用户 0 代码改动感知。

## Capabilities

### New Capabilities

- **`release-readiness`**:release variant 构建 + 混淆 + 资源压缩 + 签名 + mapping 输出

### Modified Capabilities

- **`android-build-system`**:release variant 配齐(`isMinifyEnabled = true` / `isShrinkResources = true` / `signingConfig` / `proguardFiles`);R8 规则集中在 `proguard-rules.pro`

## Impact

- **修改**:`app/build.gradle.kts`(release block + lint block)
- **新增**:`app/proguard-rules.pro` + `docs/usage/signing.md`
- **构建产物**:`app/build/outputs/apk/release/app-release.apk`(R8 启用后大小预期 < 10MB,debug ~20MB 对照)+ `app/build/outputs/mapping/release/mapping.txt`
- **不涉及**:`allowBackup="false"` 保留(CLAUDE.md §"AI 集成约定"v1 接受"备份关闭"换"apikey 不外流");apikey 加密存储不变;代码 / 测试 / spec 行为均不变;debug 路径完全不动
- **不负责**:
  - Play Store / 国内应用市场上架(roadmap §0 拍板"v1 APK only 不上架")
  - 端到端混淆验证(本机无 release keystore,留用户自行跑 `./gradlew :app:assembleRelease` 验)
  - 性能基准(Cold start / frame timing)— 留真机测试

## 验证(全部跑过才算完成)

1. `./gradlew :app:assembleRelease` BUILD SUCCESSFUL(用户本地跑,需配置 `~/.gradle/gradle.properties` 签名;AI 跑不通过,只能验 debug 路径不破坏)
2. `app/proguard-rules.pro` 静态检查(grep 无 typo + keep 规则不漏关键类)
3. `./gradlew :app:assembleDebug` 仍 BUILD SUCCESSFUL(debug 路径不退化)
4. `./gradlew :app:ktlintCheck` 仍 0 violation
5. `./gradlew :app:testDebugUnitTest` 仍全部 PASS
