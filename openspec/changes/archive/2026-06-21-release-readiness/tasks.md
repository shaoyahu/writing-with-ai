# Tasks: release-readiness

## 1. 改 `app/build.gradle.kts` release 块 + signingConfigs

- [x] 1.1 `app/build.gradle.kts` 现有 `android { ... }` 块加 `signingConfigs { create("release") { ... } }`(从 `~/.gradle/gradle.properties` 读 4 字段,见 design D1)
- [x] 1.2 `buildTypes { release { ... } }` 改:`isMinifyEnabled = true` + `isShrinkResources = true` + `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")` + `signingConfig = signingConfigs.getByName("release")`
- [x] 1.3 不动 `debug` 块(保持 M0+ 现状)
- [x] 1.4 不动 `lint { abortOnError = true }` 配置(已在 M3 验过)
- [x] 1.5 静态 grep 验:`grep -A 3 "create(\"release\")" app/build.gradle.kts` 看到 4 个 `props.getProperty("RELEASE_*")`

## 2. 新建 `app/proguard-rules.pro`

- [x] 2.1 Hilt 段:`-keep class dagger.hilt.**` + HiltViewModel + AndroidEntryPoint
- [x] 2.2 Room 段:`RoomDatabase` + `@Entity` + `@Dao`
- [x] 2.3 Compose 段:`compose.runtime.**` + `@Composable <methods>`
- [x] 2.4 Glance 段:`androidx.glance.**` + `GlanceAppWidget(Receiver)` 子类
- [x] 2.5 kotlinx-serialization 段:`-keepattributes *Annotation*, InnerClasses` + `KSerializer` 实现 + `core.ai.api.**` + `AppNavKt`
- [x] 2.6 App 入口段:`WritingApp` + `MainActivity`
- [x] 2.7 文件头加 5 行中文注释说明 5 段分组 + 引入方式(AGP `proguard-android-optimize.txt` + 本文件叠加)

## 3. 新建 `docs/usage/signing.md`

- [x] 3.1 文档头:`# Release 签名配置`(M5 polish-and-internal-release 拍板的 release-readiness 子任务)
- [x] 3.2 `keytool -genkey -v -keystore ~/keystore/release.keystore -alias writingwithai -keyalg RSA -keysize 2048 -validity 10000` 完整命令 + 提示「密码用密码管理器存,丢了无法重签名」
- [x] 3.3 `~/.gradle/gradle.properties` 4 占位说明:
  ```
  RELEASE_STORE_FILE=/Users/<用户名>/keystore/release.keystore
  RELEASE_STORE_PASSWORD=<keystore 密码>
  RELEASE_KEY_ALIAS=writingwithai
  RELEASE_KEY_PASSWORD=<key 密码>
  ```
- [x] 3.4 跑 `./gradlew :app:assembleRelease` → 出 `app/build/outputs/apk/release/app-release.apk` 步骤说明
- [x] 3.5 验证 APK 签名:`apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk`
- [x] 3.6 mapping.txt 备份提示(`app/build/outputs/mapping/release/mapping.txt` 入版本控制外备份,用于线上 crash stack trace 反混淆)
- [x] 3.7 v1 内测分发用 debug 签名兜底说明(无 release keystore 时 `assembleDebug` 出包走 debug 签名 + AndroidManifest `android:debuggable="true"` 标识,内测阶段 OK)

## 4. 验证 debug 路径不退化(AI 跑)

- [x] 4.1 `export JAVA_HOME=...` + `export ANDROID_HOME=...` + `./gradlew :app:assembleDebug` BUILD SUCCESSFUL
- [x] 4.2 `./gradlew :app:ktlintCheck` 0 violation
- [x] 4.3 `./gradlew :app:testDebugUnitTest` 全部 PASS
- [x] 4.4 `grep -E "isMinifyEnabled|isShrinkResources|proguardFiles|signingConfigs" app/build.gradle.kts` → 4 行命中,release 块配置正确
- [x] 4.5 `grep -E "^-keep" app/proguard-rules.pro | wc -l` ≥ 10(5 段 keep 至少 10 行)
- [x] 4.6 `grep -E "RELEASE_STORE_FILE|RELEASE_STORE_PASSWORD|RELEASE_KEY_ALIAS|RELEASE_KEY_PASSWORD" -r . --include="*.gradle.kts" --include="*.gradle" --include="*.properties"` 排除 `~/.gradle/gradle.properties`(本地)+ `gradle.properties` 注释占位 = 0 真实凭据命中

## 5. docs/progress.md 追加 + 准备 commit(不提交)

- [x] 5.1 读 `docs/progress.md` 顶部维护规则段
- [x] 5.2 追加 1 条:`2026-06-20 release-readiness change 落地(R8 + 资源压缩 + 签名 + proguard rules),AI 验 debug 路径不退化,assembleRelease 留用户本地验`,按时间倒序放最前
- [x] 5.3 **不** commit。等用户说"commit"或"提交"再走 `git commit`。

## 6. 用户跑 `assembleRelease` 验证(本机 AI 跑不了,仅描述)

- [ ] 6.1 用户配 `~/.gradle/gradle.properties` 4 占位
- [ ] 6.2 用户跑 `./gradlew :app:assembleRelease` BUILD SUCCESSFUL
- [ ] 6.3 用户 `ls -la app/build/outputs/apk/release/app-release.apk` 确认 APK 存在
- [ ] 6.4 用户 `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk` 验签名
- [ ] 6.5 用户装真机 + 走核心流程(创建笔记 / AI 扩写 / 模型管理 / widget)无 crash
- [ ] 6.6 用户验冷启动 < 2s(可选,首屏 stopwatch)
