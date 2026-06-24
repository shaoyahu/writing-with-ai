# app-self-hosted-update · 自托管应用分发与远程更新

**状态**:研究 / 提案阶段(未起草 OpenSpec change 之前先看一遍方案)

## 目标

- **下载页**:`https://xiaozha.nananxue.cn/app/download/` 静态页,展示最新版本 + changelog + APK 下载按钮 + 历史版本列表
- **远程更新**:App「我的」tab → 「关于」 → 「检查更新」拉 manifest,有新版弹窗,用户决定下载安装

## 决策摘要(已与用户对齐)

| 决策点 | 选择 |
| --- | --- |
| 服务器栈 | 静态 HTML + Nginx(简单) |
| 更新 UX | 可选,用户决定,不阻塞 |
| 路径 | `/app/download/` 静态页 + `/app/version.json` 版本接口 |

## 方案对比

### A · 自托管静态页 + 自定义下载(选)

- 静态 `index.html` + `version.json` + `app-release.apk` 放在 Nginx 站点目录
- App `GET version.json` → 比 `BuildConfig.VERSION_CODE` → 弹 dialog → `DownloadManager` 下 APK → 安装
- 优点:零外部依赖;内测自用完美;changelog / 二维码都能加
- 缺点:每次发版手工 `scp` / `rsync` 上传;HTTPS 证书靠 xiaozha.nananxue.cn 已有的

### B · Google Play In-App Update

- 需上架 Play Store。**roadmap §20 + §488 明确「不上架任何应用市场」** → 排除。

### C · F-Droid 自托管 / Obtainium

- Obtainium 拉 GitHub release。要 GitHub repo 公开 + 走 GitHub 流量,与「不上架但自托管」略冲突(且公开源码)。
- 排除(架构不一致)。

### D · 自建后端(Express / FastAPI)+ DB

- 支持灰度、统计、强制更新。复杂度高,内测阶段无 ROI。
- 排除(过设计)。

**结论**:选 A。

## 协议设计

### `GET /app/version.json`

```json
{
  "versionCode": 12,
  "versionName": "0.5.0",
  "minSupportedVersionCode": 5,
  "releaseNotes": "fix(sync): updateDoc 第二次同步 404\nfeat: 我的 tab",
  "apkUrl": "https://xiaozha.nananxue.cn/app/download/writing-with-ai-12.apk",
  "apkSize": 23456789,
  "apkSha256": "abcd1234...",
  "releasedAt": "2026-06-24T10:00:00Z",
  "mandatory": false
}
```

字段语义:
- `versionCode` int — 大于本地则提示更新
- `minSupportedVersionCode` int — 小于则强制升级(roadmap 可选,先实现不强制)
- `releaseNotes` string — 多行 markdown,UI 渲染
- `apkUrl` string — 完整 https URL,App 用 DownloadManager 直接拉
- `apkSize` long — 预填进度条 / 校验
- `apkSha256` string — App 下载完成后校验 APK 哈希(防中间人 + 服务端错配)
- `releasedAt` string ISO8601 — UI 显示
- `mandatory` bool — `true` 则强制(download page 与 App dialog 都提示「必须升级」)

### 下载页 HTML 骨架

```html
<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <title>写作助手 · 下载</title>
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <link rel="icon" href="/favicon.ico">
  <style>...</style>
</head>
<body>
  <header>
    <h1>写作助手 with AI</h1>
    <p class="version">v0.5.0 (code 12) · 2026-06-24</p>
  </header>
  <main>
    <a class="btn-download" href="/app/download/writing-with-ai-12.apk" download>
      下载 APK (22 MB)
    </a>
    <details>
      <summary>更新日志</summary>
      <pre>fix(sync): updateDoc 第二次同步 404
feat: 我的 tab</pre>
    </details>
    <h2>历史版本</h2>
    <ul>
      <li><a href="/app/download/writing-with-ai-11.apk">v0.4.3</a> · 2026-06-15</li>
      ...
    </ul>
    <p class="qr">扫码下载 ↓</p>
    <img src="/app/download/qr.png" alt="下载二维码">
  </main>
</body>
</html>
```

手写 HTML + CSS 即可,不引前端框架。部署:`scp` 到 Nginx 站点根目录 `/var/www/xiaozha/app/download/`。

## App 侧架构

### 新文件

```
app/src/main/java/com/yy/writingwithai/core/update/
├── AppUpdateManifest.kt         # data class (versionCode/apkUrl/sha256/...)
├── AppUpdateChecker.kt          # suspend fun fetch(): Manifest? (HttpURLConnection)
├── ApkDownloader.kt             # DownloadManager wrapper + InstallReceiver
└── AppUpdateModule.kt           # Hilt @Provides(若需要)

app/src/main/java/com/yy/writingwithai/feature/my/
├── AboutScreen.kt               # 现有入口 + 「检查更新」按钮
└── AboutViewModel.kt            # 拉 manifest + 控制 dialog 状态
```

### 网络层

- 复用 `core/net/OkHttpClient`(若无,新建)
- HTTPS-only(`usesCleartextTraffic="false"` 已设)
- 加 `network_security_config.xml` 允许 `xiaozha.nananxue.cn`(默认允许)
- 5s connect / 10s read timeout(manifest 轻量)

### 检查更新流程

```
AboutViewModel.checkUpdate()
  ├─ AppUpdateChecker.fetch() → Manifest?
  ├─ 若 null:Toast "检查失败,稍后重试"
  ├─ 若 remote.versionCode <= local:Toast "已是最新"
  └─ 若 remote.versionCode > local:
       └─ AboutScreen 显示 UpdateDialog
            ├─ 显示 versionName + releaseNotes
            ├─ 「下载」按钮 → ApkDownloader.start(manifest.apkUrl)
            ├─ 「稍后」按钮 → 关闭 dialog
            └─ 「取消」按钮 → 关闭 dialog

ApkDownloader.start()
  ├─ DownloadManager.enqueue(request with manifest.apkUrl + title)
  ├─ DownloadCompleteReceiver.onReceive
  │    ├─ 校验 SHA-256(用 manifest.apkSha256)
  │    │    ├─ 不一致:Toast "下载文件损坏",删文件
  │    │    └─ 一致:intent = Intent(ACTION_VIEW).setDataAndType(Uri, "application/vnd.android.package-archive")
  │    │         .setFlags(FLAG_GRANT_READ_URI_PERMISSION | FLAG_ACTIVITY_NEW_TASK)
  │    └─ startActivity(intent) 触发系统安装器
  └─ Manifest 声明 RECEIVE_COMPLETED 权限(下载完成 broadcast)
```

### 签名校验(防中间人)

App 内置 release 证书 SHA-256(从 `signing.md` 流程读取,写 `BuildConfig.RELEASE_CERT_SHA256` 或 `local.properties`)。下载完成后:

```kotlin
val pm = context.packageManager
val pkgInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES)
val sig = pkgInfo.signatures[0].toByteArray()
val md = MessageDigest.getInstance("SHA-256")
val sha256 = md.digest(sig).joinToString("") { "%02x".format(it) }
if (sha256 != BuildConfig.RELEASE_CERT_SHA256) {
    // 删除 + 提示,不安装
}
```

> 简化:仅校验 APK SHA-256(`manifest.apkSha256`),不走 cert(因为 cert 受 keystore 改动影响)。

### DownloadManager vs WorkManager

- `DownloadManager`:系统级,后台下载,支持进度,无存储权限。**首选**。
- `WorkManager`:可控性强,但下载大文件不如 `DownloadManager` 简单。

### 必填权限

```xml
<uses-permission android:name="android.permission.INTERNET" />     <!-- 已声明 -->
<!-- DownloadManager 7.0+ 不需要 WRITE_EXTERNAL_STORAGE -->
<!-- DownloadCompleteReceiver 不需要额外权限,DownloadManager 内部 broadcast -->
```

## 服务器侧脚本

`scripts/publish-release.sh`(或 CI 步骤):

```bash
#!/usr/bin/env bash
set -euo pipefail

VERSION_CODE=$1
VERSION_NAME=$2
NOTES_FILE=$3
APK=$4
SERVER=root@xiaozha.nananxue.cn
REMOTE_DIR=/var/www/xiaozha/app/download

scp "$APK" "$SERVER:$REMOTE_DIR/writing-with-ai-${VERSION_CODE}.apk"
scp "$NOTES_FILE" "$SERVER:$REMOTE_DIR/release-notes/${VERSION_CODE}.md"

# 更新 latest 软链
ssh "$SERVER" "cd $REMOTE_DIR && ln -sfn writing-with-ai-${VERSION_CODE}.apk latest.apk"

# 重生成 version.json
ssh "$SERVER" "$REMOTE_DIR/build-version-json.py > $REMOTE_DIR/version.json"
```

`build-version-json.py` 扫目录里所有 `writing-with-ai-{N}.apk` 拼 manifest,读同目录 `release-notes/{N}.md` 填 releaseNotes,算 SHA-256。

## OpenSpec change 起草计划

创建 `openspec/changes/app-self-hosted-update/`:

- `proposal.md` — 概述:用户决定自托管分发,App 内置「检查更新」
- `design.md` — 协议 + manifest schema + 下载页 HTML 骨架 + App 流程 + 签名校验策略
- `tasks.md` — 服务器:写 index.html + version.json 生成脚本 + publish-release.sh;App:写 AppUpdateChecker + ApkDownloader + AboutViewModel + 单测
- `specs/app-update/spec.md` — 新 capability,Req/Invariant

**子任务勾选**:
1. [server] 写 `/app/download/index.html` 静态页 + 历史版本列表
2. [server] 写 `build-version-json.py` 扫 apk 目录生成 manifest
3. [server] 写 `publish-release.sh` 简化发版流程
4. [app] 写 `AppUpdateManifest` data class
5. [app] 写 `AppUpdateChecker`(HTTPS GET + JSON 解析 + 错误分类)
6. [app] 写 `ApkDownloader`(DownloadManager + 安装 intent)
7. [app] 写 `AboutViewModel.checkUpdate()`(状态机:Idle/Checking/Available/UpToDate/Failed)
8. [app] 写 `AboutScreen.UpdateDialog` Composable
9. [app] 单测 `AppUpdateCheckerTest`(mockwebserver)
10. [app] 单测 `ApkDownloaderTest`(mock DownloadManager)
11. [app] 更新 `network_security_config.xml`(如需要,默认允许)
12. [app] e2e:发一版,真机点检查更新,走通

## 风险与决策点

1. **iOS 暂不考虑**(本仓 Android-only,roadmap §0)
2. **多架构 APK split**(`abiFilters`):现阶段只发 universal APK;后续如需按 arch 分发,在 manifest 加 `abiFilters` 字段
3. **回滚**:老 APK 留 `release-notes/{N}.md` + apk 留目录,manifest `minSupportedVersionCode` 控制最低版本
4. **服务端监控**:Nginx access log 自带;无额外监控
5. **CDN**:xiaozha.nananxue.cn 国内,直连够用;如遇慢,后续再上 CDN

## 下一步

等用户确认本方案后,起 OpenSpec change `app-self-hosted-update`,走 `/opsx:propose`。