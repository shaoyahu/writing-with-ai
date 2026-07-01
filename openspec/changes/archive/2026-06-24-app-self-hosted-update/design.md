# app-self-hosted-update · 设计

## Context

`writing-with-ai` Android App 走自托管 APK 内测分发(roadmap §20:不上架应用市场)。当前无任何发版链路:每次出新版本靠人工 `adb install`，朋友内测要拿到 APK 才能装。需要:

1. **公开下载页**:浏览器可访问，扫码可下载，展示版本历史
2. **App 内检查更新**:已装用户启动后能感知新版本并自主升级

约束:
- 服务器:已有 `xiaozha.nananxue.cn`(Nginx + HTTPS，假设 cert 已有)
- App 端:最小化新依赖，优先复用现有 OkHttp + kotlinx.serialization
- 不引入 Play Store / F-Droid / Obtainium / 自建后端
- 单人项目，人工触发发布是合理默认

## Goals / Non-Goals

**Goals:**
- 单一可信源:APK 目录内容决定 manifest，无手工维护 manifest 的可能
- HTTPS-only 端到端传输
- SHA-256 校验防中间人 + 服务端错配
- 可选更新，不阻塞 UI，用户决定
- 发版流程 5 步清单，人手跑即可

**Non-Goals:**
- 强制更新(留 `mandatory` 字段占位，本次不消费)
- 灰度发布 / 多渠道分发
- iOS 版本
- APK split per ABI(发 universal APK)
- 上传鉴权 / 限流(roadmap 内测场景)
- 自动备份 APK(留 cron 任务脚本片段，不在本 change 范围)

## Decisions

### D1 · 服务端「目录即真相」

**选择**:`version.json` 不是手工维护的产物，而是 `build-version-json.py` 扫描 `/var/www/xiaozha/app/download/` 目录里所有 `writing-with-ai-{N}.apk` 派生的输出。

**理由**:
- 漂移不可能:无 APK → scanner 报错退出;有 APK → JSON 反映目录实情
- `latest.apk` 软链只是给人看的装饰，manifest 取最高 versionCode 即可
- 老 APK 留目录 → 历史版本列表自动正确

**替代方案**(已拒):
- 手工维护 JSON:漂移，需在 runbook 加额外对账步骤
- 后端 DB 跟踪版本:过设计，内测阶段无 ROI

### D2 · App 检查更新 = HTTPS GET + JSON parse

**选择**:`AppUpdateChecker.fetch()` 用 OkHttp `GET https://xiaozha.nananxue.cn/app/version.json`，用 `kotlinx.serialization` 解到 `AppUpdateManifest` data class。

**理由**:
- 复用现有 `core/net` OkHttp，新增零依赖
- HTTP 200 + JSON parse → 比 `BuildConfig.VERSION_CODE`
- 失败分类:`IOException` → `Failed(网络)`;4xx → `Failed(服务端)`;parse error → `Failed(格式)`

**替代方案**(已拒):
- ETag / 304:`version.json` 5min 缓存，App 端不强需;真要省流量留后续
- GraphQL / protobuf:overkill

### D3 · 下载用 `DownloadManager`

**选择**:`ApkDownloader.start()` 通过 `DownloadManager.enqueue()` 入队下载，系统级后台任务，无需 `WRITE_EXTERNAL_STORAGE` 权限。

**理由**:
- 系统级服务，UI 进程被杀不影响下载
- 进度条 / 完成广播原生支持
- 不引 WorkManager / OkHttp 流式保存

**替代方案**(已拒):
- OkHttp 流式 `Response.body.byteStream()` 写本地文件:权限复杂(API 29+ SAF)，断点续传难
- WorkManager:下载大文件不如 `DownloadManager` 原生支持

### D4 · SHA-256 校验在下载完成后

**选择**:`UpdateDownloadReceiver.onReceive(ACTION_DOWNLOAD_COMPLETE)` 拿到本地 APK 路径，用 `MessageDigest("SHA-256")` 算文件哈希，与 manifest `apkSha256` 字段比对，不一致则删除文件 + Toast 报错。

**理由**:
- 防止服务端错配 + 中间人篡改
- 不依赖签名证书(SHA-256 跟 keystore 改动解耦)
- 计算一次 ~50ms(20MB APK)，用户感知不到

**替代方案**(已拒):
- 校验 release 证书:跟 keystore 强绑定，keystore 换必须改 App 内置 cert，易错
- 流式边下边校验:实现复杂度高，增量场景省的那点时间不值

### D5 · 「我的」→「关于」→「检查更新」单按钮

**选择**:`AboutScreen` 顶部加「检查更新」按钮，点了触发 `AboutViewModel.checkUpdate()`，状态机 `Idle → Checking → {Available | UpToDate | Failed}`,`Available` 时弹 `UpdateDialog`。

**理由**:
- 内测用户手动触发 vs 启动自动检查:避免每次启动弹窗打扰
- `AboutScreen` 已存在(bottom-tab-bar change 已建)，只改 1 个文件

**替代方案**(已拒):
- 启动自动静默检查 + 顶部 banner:实现复杂，要监听冷热启动 + 后台任务
- WorkManager 周期检查:过度，内测阶段不值

### D6 · 服务端脚本用 Python 3 stdlib

**选择**:`build-version-json.py` / `build-index.py` 纯 Python 3 stdlib(`hashlib`/`pathlib`/`json`)，无 `pip install`。

**理由**:
- 服务器环境假设已有 Python 3(Nginx 通常配)，无部署负担
- 单文件可读，后续改 sha256 字段加一行就行

**替代方案**(已拒):
- Bash + `sha256sum`:处理版本排序、`stat` 取 mtime 不如 Python 直白
- Node:增加 Node 依赖，不必要

### D7 · `publish-release.sh` 在用户本机跑

**选择**:`publish-release.sh` 在开发者本机跑，通过 `scp` / `ssh` 推到 `xiaozha.nananxue.cn`。

**理由**:
- 服务器上不发版逻辑(无状态);发版逻辑在用户机，跟 keystore 一起，少一处凭据管理
- 本机可重跑(断点续传友好)

**替代方案**(已拒):
- 服务器上发版:要 SSH 进服务器跑，凭据外移，审计弱

## Risks / Trade-offs

| 风险 | 缓解 |
| --- | --- |
| 漏发布 → 用户装的版本比下载页旧，App 检查更新说"已是最新" | `AboutScreen` 常驻显示 `versionName`,runbook 5 步必走 |
| 上传 APK 损坏 → 用户装了闪退 | SHA-256 校验 + APK 必走 `apksigner verify` CI step |
| 服务器被攻击，恶意 APK 投放 | HTTPS + 私有域名访问控制(Nginx `allow`/`deny` 可选)+ APK 命名固定 `writing-with-ai-{N}.apk` 难撞 |
| Nginx 站点被入侵，篡改 `version.json` | 上传脚本用 `scp` 走 SSH，密钥对 + 服务器 fail2ban |
| App 旧版本不再被支持 | manifest `minSupportedVersionCode` 字段已留，本次不强制;后续发版可设高 |
| `DownloadManager` 在国产 ROM 行为差异 | 走标准 API，适配主流厂商;极小概率失败提示用户用浏览器下载 `latest.apk` |
| iOS 用户 | roadmap 本仓 Android-only，本 change 不覆盖 |

## Migration Plan

**部署顺序**:

1. **服务端准备**(一次性):
   - SSH 到 xiaozha.nananxue.cn
   - `mkdir -p /var/www/xiaozha/app/download/release-notes`
   - 写 Nginx `location /app/download/` 配置，reload
   - 拷贝 `scripts/release-server/build-version-json.py` 上服务器，`chmod +x`

2. **首次发布**:
   - `./gradlew :app:assembleRelease` 出 `app-release.apk`
   - 写 `release-notes/12.md`
   - `./scripts/release-server/publish-release.sh 12 0.5.0 release-notes/12.md app-release.apk`
   - `curl https://xiaozha.nananxue.cn/app/version.json` 验证字段

3. **App 更新发布**:
   - 走 `/opsx:apply app-self-hosted-update` 实现 App 端代码
   - `./gradlew :app:assembleRelease` 出新版
   - 重复步骤 2

**回滚策略**:
- 删除最新 APK 文件 + 重新跑 `build-version-json.py` → manifest 退回上一版
- `minSupportedVersionCode` 后续可强制升级，本次不动

## Open Questions

1. **Nginx 站点根目录**:假设 `/var/www/xiaozha/app/download/`，用户需确认实际路径
2. **HTTPS 证书**:假设 `xiaozha.nananxue.cn` 已有 cert，本 change 不涉及 cert 申请
3. **QR 码**:`index.html` 里 `qr.png` 需手生成一次(指向 `latest.apk`)，或用 `qrcode` Python 库扫 URL 生成 → 本 change 留占位图，跑通后人工补
4. **`mandatory` 字段**:本次服务端发，App 端不消费，等下次发版若真要强制再补 App 逻辑