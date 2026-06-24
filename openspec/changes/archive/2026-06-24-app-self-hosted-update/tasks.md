# app-self-hosted-update · 任务清单

## 1. 服务端脚本(入 git)

- [x] 1.1 写 `scripts/release-server/build-version-json.py`(扫 APK 目录,生成 manifest JSON 到 stdout)
- [x] 1.2 写 `scripts/release-server/build-index.py`(扫 APK + release-notes 目录,生成 `index.html`)
- [x] 1.3 写 `scripts/release-server/index.html.template`(Jinja-like 占位符,build-index 填版本号/changelog/历史列表)
- [x] 1.4 写 `scripts/release-server/publish-release.sh`(scp + ssh + 串 4 步;`set -euo pipefail`;幂等)
- [x] 1.5 `chmod +x` 三个脚本(本机跑)

## 2. 服务端部署(SSH 到 xiaozha.nananxue.cn,人工)

- [ ] 2.1 `mkdir -p /var/www/xiaozha/app/download/release-notes`
- [ ] 2.2 上传 `build-version-json.py` 到服务器,`chmod +x`
- [ ] 2.3 上传 `build-index.py` 与 `index.html.template` 同上
- [ ] 2.4 写 Nginx `location /app/download/` 路由 + cache headers,reload
- [ ] 2.5 验:`curl -I https://xiaozha.nananxue.cn/app/download/` 返回 200

## 3. App 端 — 数据层

- [x] 3.1 写 `core/update/AppUpdateManifest.kt`(data class + `@Serializable`)
- [x] 3.2 写 `core/update/UpdateError.kt`(sealed class: Network/Parse/Http)
- [x] 3.3 写 `core/update/UpdateDeps.kt`(Hilt `@Module` 提供 OkHttp + JSON,或复用 `core/net`)

## 4. App 端 — 检查 + 下载

- [x] 4.1 写 `core/update/AppUpdateChecker.kt`(`suspend fun fetch(): Result<AppUpdateManifest>`;OkHttp GET;JSON decode;错误分类)
- [x] 4.2 写 `core/update/ApkDownloader.kt`(`fun start(manifest: AppUpdateManifest): Long` 返回 downloadId;`DownloadManager.enqueue`)
- [x] 4.3 写 `core/update/UpdateDownloadReceiver.kt`(BroadcastReceiver;SHA-256 校验;install intent;不匹配删文件 + Toast)
- [x] 4.4 `AndroidManifest.xml` 注册 `UpdateDownloadReceiver` + `<intent-filter>` 收 `ACTION_DOWNLOAD_COMPLETE`

## 5. App 端 — UI

- [x] 5.1 改 `feature/my/AboutScreen.kt`:加「检查更新」按钮 + 顶部 `Text("v{versionName} (code {versionCode})")`
- [x] 5.2 写 `feature/my/AboutViewModel.kt`(状态机 Idle/Checking/Available/UpToDate/Failed + 副作用发 Snackbar)
- [x] 5.3 写 `feature/my/UpdateDialog.kt`(Composable:versionName + releaseNotes + 下载/稍后 按钮)
- [x] 5.4 AppNav 加 `AboutScreen` 路由(若 bottom-tab-bar change 未含)

## 6. App 端 — 测试

- [x] 6.1 `app/src/test/.../AppUpdateCheckerTest.kt`(mockwebserver:200 + 新版 → manifest;远 versionCode 小 → 不弹;500 → Failed;JSON 损坏 → Failed)
- [ ] 6.2 `app/src/test/.../ApkDownloaderTest.kt`(mock DownloadManager;verify enqueue 调用)— **deferred**:DownloadManager 是系统 service,需要 Robolectric;当前 AppUpdateChecker 测试覆盖核心数据路径,后续可补
- [x] 6.3 `app/src/test/.../AboutViewModelTest.kt`(状态机:Idle → Checking → Available → Idle 关闭 dialog)

## 7. 文档

- [x] 7.1 写 `docs/usage/release-checklist.md`(5 步发版清单)
- [x] 7.2 写 `docs/usage/app-update-flow.md`(下载页 / version.json / App 流程图 + manifest schema 完整说明)

## 8. 验证

- [x] 8.1 `./gradlew :app:assembleDebug` 通过
- [x] 8.2 `./gradlew :app:ktlintCheck` 通过
- [x] 8.3 `./gradlew :app:testDebugUnitTest` 全绿(新增 2 个测试文件;ApkDownloaderTest deferred 见 6.2)
- [ ] 8.4 e2e 干跑:本地起 mockwebserver → App 拉到 manifest → 弹 dialog → mock DownloadManager → 校验 SHA-256 — **deferred**:AppUpdateCheckerTest 已覆盖核心 fetch + 错误分类;完整 instrumented test 待 Robolectric 接入
- [ ] 8.5 e2e 真机:发布一版 APK 到 `xiaozha.nananxue.cn/app/download/`(走 release-checklist)→ 真机装旧版 → 点「检查更新」→ 下载 → 安装成功 — **blocked on group 2**(需用户 SSH 上服务器部署)

## 9. 归档(交付后)

- [ ] 9.1 `openspec archive app-self-hosted-update`(change 进 archive)— **after 8.5**
- [ ] 9.2 `docs/progress.md` 追加「M5+ · 自托管分发上线」节点 — **after 9.1**