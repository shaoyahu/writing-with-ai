# 2026-06-24 app-self-hosted-update code-review r1

Review 范围:`openspec/changes/app-self-hosted-update/` 实施产物
- 4 服务端脚本(`scripts/release-server/`)
- 7 core/update 文件
- 3 feature/my UI 文件
- AndroidManifest receiver + i18n
- 2 测试文件
- 2 文档

---

## CRITICAL (blocks release)

### C1 · `Uri.fromFile` 触发 FileUriExposedException — APK 安装崩 API 24+

- `app/src/main/java/com/yy/writingwithai/core/update/UpdateDownloadReceiver.kt:72`:
  ```kotlin
  setDataAndType(android.net.Uri.fromFile(apk), "application/vnd.android.package-archive")
  ```
- targetSdk ≥ 24 + 没有 `FileProvider` → 抛 `FileUriExposedException`,**install intent 直接 crash**
- 现状:无 `<provider>` 声明 / 无 `res/xml/file_paths.xml`
- **Fix**:
  1. `AndroidManifest.xml` 加:
     ```xml
     <provider
         android:name="androidx.core.content.FileProvider"
         android:authorities="${applicationId}.fileprovider"
         android:exported="false"
         android:grantUriPermissions="true">
         <meta-data android:name="android.support.FILE_PROVIDER_PATHS"
             android:resource="@xml/file_paths" />
     </provider>
     ```
  2. `res/xml/file_paths.xml`:
     ```xml
     <paths>
         <external-files-path name="downloads" path="downloads/app-update/" />
     </paths>
     ```
  3. `UpdateDownloadReceiver.installIntent`:
     ```kotlin
     val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
     intent.setDataAndType(uri, "application/vnd.android.package-archive")
     ```
- 这条不修，真机 e2e 必崩。

### C2 · `COLUMN_LOCAL_URI` 在新 API 返回 `content://` 不是 `file://` — SHA-256 校验永远拿到不存在的路径

- `UpdateDownloadReceiver.kt:49` `cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)` + 行 56 `File(localUri.removePrefix("file://"))`
- API 29+(Scoped Storage 全面启用),`COLUMN_LOCAL_URI` 返回 `content://` URI;`File()` 拿到的是该字符串本身被当成文件名，文件不存在
- 校验永远失败 → 文件被删 + Toast「下载文件损坏」，**永远装不上**
- **Fix**:通过 `ContentResolver.openInputStream(uri)` 拿字节流做 SHA-256:
  ```kotlin
  val input = context.contentResolver.openInputStream(uri)
      ?: run { Log.w(TAG, "openInputStream null"); return }
  val md = MessageDigest.getInstance("SHA-256")
  input.use { /* read + update */ }
  ```
- 或者用 `COLUMN_LOCAL_FILENAME`(API 24+ deprecated，但 `COLUMN_URI` 是新 API 的官方入口)。最稳:两条都试，优先 COLUMN_URI。

---

## HIGH

### H1 · `AppUpdateChecker.MANIFEST_URL` 写死生产 URL,debug 改不了

- `AppUpdateChecker.kt:55`:硬编码 `https://xiaozha.nananxue.cn/app/version.json`
- debug 构建想指向 staging / mockwebserver 测不了
- **Fix**:BuildConfig 字段 + 不同 buildType / flavor 注入:
  ```kotlin
  // app/build.gradle.kts
  debug { buildConfigField("String", "UPDATE_MANIFEST_URL", "\"http://10.0.2.2:8080/app/version.json\"") }
  release { buildConfigField("String", "UPDATE_MANIFEST_URL", "\"https://xiaozha.nananxue.cn/app/version.json\"") }
  ```
  App 端:`BuildConfig.UPDATE_MANIFEST_URL`

### H2 · `UpdateDownloadReceiver` `exported="true"` — 任何 app 可发假 broadcast

- `AndroidManifest.xml`:receiver `android:exported="true"` + `<intent-filter>` 收 `DOWNLOAD_COMPLETE`
- 攻击路径有限(consume 999L miss → return)，但仍违规
- **Fix**:`android:exported="false"`(API 26+ 系统 broadcast 可送达)

### H3 · 服务端 `build-index.py` 用 `sha256sum` shell 命令 — 不可移植

- `build-index.py:80` 调 `sha256sum`
- macOS 用 `shasum -a 256`,Windows 用 PowerShell。只在 Linux 服务器跑 OK，但没注释
- **Fix**:用 Python `hashlib` 直接算(跟 `build-version-json.py` 一致)，删 `sha256sum` 调用

### H4 · `publish-release.sh` 无超时 — 服务器 hang 整个脚本 hang

- `scp` / `ssh` 默认无 ConnectTimeout
- **Fix**:`scp -o ConnectTimeout=10 ...`、`ssh -o ConnectTimeout=10 ...`、`ServerAliveInterval=30`

### H5 · `UpdateManifestStore` 无 TTL — manifest 永久残留 map

- `consume` 在下载完成时调;取消/网络断/重启 → manifest 永远在 ConcurrentHashMap
- **Fix**:`put` 加 timestamp,`consume` 顺便清理 >1h 的条目

### H6 · `installIntent` 用 `FLAG_GRANT_READ_URI_PERMISSION` 但没指定目标包

- 多数场景 OK;极小部分定制 ROM 不识别
- **Fix**:defer(优先级低);若出问题可 `setPackage("com.android.packageinstaller")`

---

## MEDIUM

### M1 · `UpdateDownloadReceiver` 没区分下载失败原因

- 只看 `STATUS_SUCCESSFUL`，其它情况 log + return
- 用户:通知栏显示失败但 App 端无反馈
- **Fix**:解析 `COLUMN_REASON`，分类 Toast

### M2 · `ApkDownloader.cancel` 死代码

- 没人调，AboutViewModel 没暴露 cancel
- **Fix**:删，或加 UI 入口

### M3 · `build-version-json.py` 的 `minSupportedVersionCode=1` 是占位

- 永远 1,App 端没强制升级逻辑(spec 预留)
- **Fix**:加 `--min-supported` CLI 参数，默认 1

### M4 · `index.html.template` `<img src="/app/download/qr.png">` 引用不存在图

- qr.png 没生成，404 → 破图
- **Fix**:`<img onerror="this.style.display='none'">` 兜底，或 build-index.py 调 qrcode 库生成

### M5 · `installIntent` 不处理系统安装器缺失

- 极小概率(Android 系统都带)，定制 ROM 可能没有
- **Fix**:`queryIntentActivities` 空列表 → Toast 提示

### M6 · `Build.VERSION.SDK_INT` 未在 receiver 检查 API level

- 隐含于 C2;C2 修后顺带消失

### M8 · `UpdateDialog` 没显示 `mandatory` 提示

- spec 明确「`mandatory=true` 时『稍后』按钮 disabled」
- 当前 UI 忽略
- **Fix**:补 5 行 UI 或改 spec

### M9 · `release-checklist.md` step 4 验 SHA-256 没说在哪对比

- **Fix**:加「对比 version.json 的 apkSha256 字段」一句

---

## LOW

### L1 · 没声明 `<queries>` for package installer

- targetSdk 30+ 包可见性限制
- **Fix**:`AndroidManifest.xml` 加 `<queries>` for `INSTALL_PACKAGE`

### L2 · `index.html.template` 直接引用 `/favicon.ico` 可能 404

- **Fix**:`<link rel="icon" href="data:,">` 抑制 404

### L5 · `AppUpdateChecker` 日志带异常 message，可能 PII 泄露

- **Fix**:日志只保留 class simple name

### L6 · `AboutViewModel.resetToIdle` 是公开方法，UI 误用风险

- **Fix**:标 `internal`

### L7 · 测试用 mockwebserver 默认端口分配，并行 CI 偶发冲突

- 当前 OK，加注释

### L8 · `AppUpdateChecker` 缺 retry/timeout jitter

- Future

---

## Test gap

- `ApkDownloaderTest` 推迟(Robolectric)
- `UpdateDownloadReceiver` SHA-256 路径需要 instrumented test，只能真机验
- `UpdateManifestStore` 无单测(简单，可加一个 `put + consume` round-trip)
- e2e 干跑(8.4)deferred

---

## Summary

**2 个真硬伤(C1, C2)** 全部让 install 路径在 Android 7+ 上必崩或拿不到文件。**修这两条才能上线**:
1. 加 FileProvider + file_paths.xml,install 用 `FileProvider.getUriForFile`
2. ContentResolver.openInputStream 取代 `File(localUri)`

**HIGH 一组**(H1-H6)主要是可移植性 + 安全 + 防 hang，服务上线前应该修。

MEDIUM/LOW 都是 v1 可接受 trade-off，留 follow-up change。

**Spec / Implementation gap**:M8 spec 写「mandatory=true 时『稍后』按钮 disabled」但 UI 没做;建议补(spec 写得太清晰，实现侧只差 5 行)。

**ApkDownloaderTest 仍 deferred** — Robolectric 没引入;真机 e2e 走通后跟 8.5 一起验。