## Why

App 当前无内部分发链路,新版本只能靠 `adb install` 或手动传 APK,内测体验差。roadmap §20/§488 明确「不上架任何应用市场,只发 APK 文件」,所以需要自托管下载页 + App 内远程更新检查,让用户装一次就能持续收到新版本。

## What Changes

- **新增静态下载页**:`https://xiaozha.nananxue.cn/app/download/`,展示最新版本号、changelog、APK 下载按钮、历史版本列表、扫码下载二维码
- **新增版本接口**:`https://xiaozha.nananxue.cn/app/version.json`,由服务端脚本扫 APK 目录生成,作为客户端检查更新的真源
- **新增服务端脚本**:
  - `build-version-json.py`:扫目录里 `writing-with-ai-{N}.apk`,按 versionCode 取最大生成 manifest(纯 stdout 输出,Python 3 标准库)
  - `build-index.py`:扫描 APK 目录重生成 `index.html` 历史版本列表
  - `publish-release.sh`:scp APK + release notes + 重跑 scanner,串 4 步,人工触发
- **新增 Nginx 站点配置**:`location /app/download/` 路由 + APK 不缓存 + version.json 5min 缓存
- **新增 App 能力**:
  - `core/update/AppUpdateChecker`:`suspend fun fetch()` 拉 `version.json` 解析为 `AppUpdateManifest`
  - `core/update/ApkDownloader`:`DownloadManager.enqueue` + 完成 broadcast 校验 SHA-256 + 触发系统安装 intent
  - `feature/my/AboutScreen`:加「检查更新」按钮 + `UpdateDialog` Composable
  - `feature/my/AboutViewModel`:状态机 `Idle / Checking / Available / UpToDate / Failed`
- **新增 release runbook**:`docs/usage/release-checklist.md`,5 步发版清单

## Capabilities

### New Capabilities

- `app-update`:App 远程更新能力(检查 + 下载 + 安装 + SHA-256 校验);新 capability,无现有同名 spec

### Modified Capabilities

(无现有 capability 的需求级别变更;此 change 纯新增)

## Impact

- **新增文件**:
  - `scripts/release-server/build-version-json.py`
  - `scripts/release-server/build-index.py`
  - `scripts/release-server/publish-release.sh`
  - `scripts/release-server/index.html.template`
  - `docs/usage/release-checklist.md`
  - `app/src/main/java/com/yy/writingwithai/core/update/AppUpdateManifest.kt`
  - `app/src/main/java/com/yy/writingwithai/core/update/AppUpdateChecker.kt`
  - `app/src/main/java/com/yy/writingwithai/core/update/ApkDownloader.kt`
  - `app/src/main/java/com/yy/writingwithai/core/update/UpdateDownloadReceiver.kt`
  - `app/src/main/java/com/yy/writingwithai/core/update/UpdateDeps.kt`(Hilt module)
  - `app/src/main/java/com/yy/writingwithai/feature/my/AboutScreen.kt`(改)
  - `app/src/main/java/com/yy/writingwithai/feature/my/AboutViewModel.kt`(改)
  - `app/src/main/java/com/yy/writingwithai/feature/my/UpdateDialog.kt`
  - `app/src/test/java/com/yy/writingwithai/core/update/AppUpdateCheckerTest.kt`
  - `app/src/test/java/com/yy/writingwithai/core/update/ApkDownloaderTest.kt`
- **修改文件**:
  - `app/src/main/AndroidManifest.xml`:`AboutScreen` 注册 + DownloadCompleteReceiver 注册
  - `app/build.gradle.kts`:如有 DI 依赖调整(预期无)
- **服务端**:`xiaozha.nananxue.cn` 服务器加 `/var/www/xiaozha/app/download/` 目录 + Nginx `location` 配置 + `build-version-json.py` 可执行权限
- **依赖**:**无新增第三方依赖**,OkHttp 复用 `core/net`,JSON 用 `kotlinx.serialization`(已引入),下载用系统 `DownloadManager`(零依赖)
- **运行时权限**:无新增(`INTERNET` 已声明,`DownloadManager` 自带权限,`RECEIVE_COMPLETED` 由系统 broadcast 隐式授予)