# Release 签名配置

v1 内测 APK 分发需要 release 签名;M5 polish-and-internal-release 拍板的 release-readiness 子任务。

## 概览

`app/build.gradle.kts` `signingConfigs.release` 从 `~/.gradle/gradle.properties` 读 4 凭据(keystore 不入库，本机维护):

- `RELEASE_STORE_FILE` — keystore `.jks` 文件绝对路径
- `RELEASE_STORE_PASSWORD` — keystore 密码
- `RELEASE_KEY_ALIAS` — key alias
- `RELEASE_KEY_PASSWORD` — key 密码

## 步骤

### 1. 生成 release keystore

```bash
keytool -genkey -v \
  -keystore ~/keystore/release.keystore \
  -alias writingwithai \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

提示:

- **密码用密码管理器存**(1Password / Bitwarden / Keychain)— 丢了无法重签名，用户所有 APK 升级必须用原 keystore 升 versionCode
- `validity 10000` = ~27 年，远超应用生命周期
- 备份 keystore 到 1Password / 加密 USB / 离线存储;**不**仅在 ~/keystore/

### 2. 配 `~/.gradle/gradle.properties`

```properties
# writing-with-ai · release 签名(本机配置，不入 git)
RELEASE_STORE_FILE=/Users/<用户名>/keystore/release.keystore
RELEASE_STORE_PASSWORD=<keystore 密码>
RELEASE_KEY_ALIAS=writingwithai
RELEASE_KEY_PASSWORD=<key 密码>
```

### 3. 跑 `assembleRelease`

```bash
./gradlew :app:assembleRelease
```

产物:

- `app/build/outputs/apk/release/app-release.apk` — 签名 APK
- `app/build/outputs/mapping/release/mapping.txt` — R8 混淆映射表(线上 crash 反混淆用)

### 4. 验证 APK 签名

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

应输出 keystore 证书 SHA-256 / 有效期 / Subject。

### 5. mapping.txt 备份

`mapping.txt` 每次 release 都会更新，**必须**存到 1Password 备注 / 加密备份。线上 crash stack trace 拿到的混淆名要用对应版本 mapping 反混淆才可读。

## v1 内测分发兜底

无 release keystore 时，用 debug 签名出包:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

debug APK 在 AndroidManifest 标 `android:debuggable="true"`，内测阶段可接受;v1 上 v1.1 release 后切 release 签名。

## 常见问题

- **`assembleRelease` 失败提示缺 `RELEASE_STORE_FILE`**:检查 `~/.gradle/gradle.properties` 是否存在 + 路径绝对 + 4 字段都填
- **`assembleRelease` 失败提示 `Keystore was tampered with, or password was incorrect`**:keystore 密码错;重新跑 keytool 或从备份恢复
- **APK 装上后 R8 误 keep 漏类 → 运行时 NPE**:补 `app/proguard-rules.pro` 对应段，然后 `assembleRelease` 重跑
- **`isShrinkResources` 误删 widget 资源**:加 `app/src/main/res/raw/keep.xml` 列 widget 资源，或 widget XML 用 `tools:keep="@drawable/widget_preview"`
- **换电脑 / 同事接手**:keystore + 4 凭据 0 移交链路必须文档化;1Password 共享 vault 是推荐做法

## 与 CLAUDE.md §"AI 集成约定" 一致性

签名凭据处理跟 apikey 同款:
- **不入库**(`~/.gradle/gradle.properties` 本地)
- **不**走项目 `gradle.properties`
- **不**走 `BuildConfig`(避免代码静态分析拿到)
- **不**走 logcat / crashlytics
- **v1 接受"备份关闭"换"凭据绝对不外流"**(`allowBackup="false"` 全局配置保留)
