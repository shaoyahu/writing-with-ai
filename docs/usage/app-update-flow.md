# app-self-hosted-update · 更新流程

## 架构

```
┌──────────────────┐                     ┌──────────────────────┐
│  xiaozha.nananxue.cn│                  │  Android App         │
│  (Nginx + 静态)  │                     │                      │
│                  │                     │                      │
│  /app/download/  │←── 浏览器扫码 ──→    │  「我的」→「关于」   │
│   index.html     │                     │   ↓                  │
│   *.apk          │                     │  AboutScreen         │
│   release-notes/ │                     │   ↓ 检查更新         │
│                  │                     │  AppUpdateChecker    │
│  /app/version.json│←── HTTPS GET ──→   │   (OkHttp + Json)    │
│                  │                     │   ↓ 解析             │
│  scripts/        │                     │  AppUpdateManifest   │
│   build-version-json.py │              │   ↓ 比对             │
│   build-index.py │                     │  AboutViewModel      │
│   publish-release.sh │                 │   ↓                  │
│                  │                     │  UpdateDialog        │
└──────────────────┘                     │   ↓ 用户点下载        │
                                         │  ApkDownloader       │
                                         │   (DownloadManager)  │
                                         │   ↓ 完成 broadcast   │
                                         │  UpdateDownloadReceiver │
                                         │   ↓ SHA-256 校验     │
                                         │  系统安装 intent     │
                                         └──────────────────────┘
```

## 协议 `/app/version.json`

```json
{
  "versionCode": 12,
  "versionName": "0.5.0",
  "apkUrl": "https://xiaozha.nananxue.cn/app/download/writing-with-ai-12.apk",
  "apkSize": 23456789,
  "apkSha256": "abc1234...",
  "releaseNotes": "fix: ...",
  "releasedAt": "2026-06-24T10:00:00+00:00",
  "minSupportedVersionCode": 1,
  "mandatory": false
}
```

字段约束:

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `versionCode` | int | ✓ | App 用 `BuildConfig.VERSION_CODE` 比对 |
| `versionName` | string | ✓ | 显示文案 |
| `apkUrl` | string (https) | ✓ | DownloadManager 用这个 |
| `apkSize` | int (bytes) | ✓ | 进度条 / UI 展示 |
| `apkSha256` | string (64 hex) | ✓ | 下载完成后校验文件完整性 |
| `releaseNotes` | string | ✗ (默认 "") | 多行 markdown |
| `releasedAt` | string (ISO8601) | ✗ | UI 展示 |
| `minSupportedVersionCode` | int | ✗ (默认 1) | 预留强制升级字段 |
| `mandatory` | bool | ✗ (默认 false) | 预留强制升级字段 |

## 单一可信源

服务端 manifest 不手工维护。`scripts/release-server/build-version-json.py` 每次跑都从
`/var/www/xiaozha/app/download/writing-with-ai-*.apk` 扫目录,**取最大 versionCode**,
重算 SHA-256,从 `release-notes/{N}.md` 读 changelog,生成新 JSON。

漂移不可能:
- 多余 APK → scanner 取最大,忽略
- 缺 symlink → scanner 不依赖 symlink
- 上传一半 → scanner 跑时已上传的 APK 已被扫到

## 安全

| 防御层 | 实现 |
| --- | --- |
| 传输加密 | HTTPS-only(`usesCleartextTraffic="false"` + spec 强制 `https://`) |
| 文件校验 | APK 下载完 `MessageDigest("SHA-256")` vs `manifest.apkSha256` |
| 中间人 | HTTPS + cert pinning 由系统 trust store 兜底 |
| 篡改 | 不一致 → 删文件 + Toast「下载文件损坏」 |
| 上传 | `scp` over SSH;keystore + 服务器 fail2ban(运维侧) |

## App 状态机

```
AboutUiState:
  Idle
    ↓ checkForUpdate()
  Checking
    ↓ fetch() 完成
  ├─ remote.versionCode > local → Available(manifest)
  │    ↓ startDownload()
  │  Downloading(downloadId, versionName)
  │    ↓ DownloadManager.ACTION_DOWNLOAD_COMPLETE
  │  → UpdateDownloadReceiver 校验 SHA-256
  │  → install intent (匹配) 或 Toast 报错(不匹配)
  ├─ remote.versionCode <= local → UpToDate(remoteVersionName)
  │    ↓ LaunchedEffect resetToIdle
  │  Idle (Snackbar「已是最新」)
  └─ fetch() 失败 → Failed(error)
       ↓ LaunchedEffect resetToIdle
     Idle (Snackbar「检查失败」)
```

## 权限

| 权限 | 用途 | 状态 |
| --- | --- | --- |
| `INTERNET` | OkHttp GET version.json | 已声明 |
| `DownloadManager` | 系统服务,无需权限 | 自动 |
| `RECEIVE_COMPLETED` (隐式) | ACTION_DOWNLOAD_COMPLETE broadcast | 系统授予 |
| `WRITE_EXTERNAL_STORAGE` | API 29+ DownloadManager 不用写外部存储 | 不需要 |

## 不在本 change 范围

- 强制更新(`mandatory` 字段已留,UI/逻辑下次实现)
- 灰度发布 / 多渠道分发
- iOS 端(本仓 Android-only)
- 自动备份 APK(后续 cron 任务)
- QR 码自动生成(占位图,人工补或后续 Python 脚本生成)