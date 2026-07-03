---
name: publish-release
description: 发布 APK 到 GitHub Releases + 更新服务器下载页。当用户说"发版"/"发布新版本"/"publish release"时触发。
---

发布 APK 到 GitHub Releases CDN，并更新服务器下载页的版本信息。

**默认行为**: 同时发布 **release** 和 **debug** 两个通道，共用相同的 versionCode 和 versionName。

## 前置检查

1. **确认 gh CLI 已登录**:
   ```bash
   gh auth status
   ```
   如果未登录，提示用户先运行 `gh auth login`。

2. **确认两个 APK 都已编译**:
   ```bash
   ls app/build/outputs/apk/release/app-release.apk
   ls app/build/outputs/apk/debug/app-debug.apk
   ```
   如果不存在，编译缺失的：
   ```bash
   export JAVA_HOME=/opt/homebrew/opt/openjdk@17
   ./gradlew :app:assembleRelease  # 或 :app:assembleDebug
   ```

3. **查询服务器当前版本**（用于推断新 versionCode）:
   ```bash
   curl -s https://xiaozha.nananxue.cn/app/release/version.json | python3 -c "import json,sys; print(json.load(sys.stdin)['versionCode'])"
   ```

## 执行步骤

### Step 1: 确认版本信息

向用户确认（如果未提供）:
- **versionCode**: 服务器最新版本 + 1
- **versionName**: 如 "0.3.0"

示例确认消息：
> 当前服务器 release 版本: code=2, name=0.2.0
> 将发布: versionCode=3, versionName=0.3.0
> 通道: release + debug（双通道）
> 确认发版？

### Step 2: 写 release notes

如果用户没有现成的 release notes 文件，基于最近 commits 生成或询问用户更新内容，写入 `release-notes/{versionCode}.md`：
```markdown
feat(xxx): 简短描述
fix(xxx): 修复描述
```

### Step 3: 发布两个通道

**先发 release，再发 debug**（index.html 第二次发布时会包含两个通道的信息）:

```bash
# Release 通道
./scripts/release-server/publish-release.sh \
  <versionCode> <versionName> \
  release-notes/<versionCode>.md \
  app/build/outputs/apk/release/app-release.apk \
  release

# Debug 通道
./scripts/release-server/publish-release.sh \
  <versionCode> <versionName> \
  release-notes/<versionCode>.md \
  app/build/outputs/apk/debug/app-debug.apk \
  debug
```

每个通道脚本会自动:
1. 创建 GitHub Release（tag: `v{code}` 或 `v{code}-debug`）
2. 上传 APK 到 GitHub Release
3. 本地生成 `version.json`（apkUrl 指向 GitHub CDN）
4. 本地生成 `index.html`（下载按钮指向 GitHub CDN）
5. scp 元数据到服务器

### Step 4: 验证

1. 确认两个通道的 version.json 已更新:
   ```bash
   curl -s https://xiaozha.nananxue.cn/app/release/version.json | python3 -m json.tool | grep apkUrl
   curl -s https://xiaozha.nananxue.cn/app/debug/version.json | python3 -m json.tool | grep apkUrl
   ```
   两个 `apkUrl` 都应以 `https://github.com/` 开头。

2. 确认下载页可访问:
   ```bash
   curl -sI https://xiaozha.nananxue.cn/app/ | head -1
   ```

3. 提示用户在手机上验证: 打开 App → 「我的」→ 「检查更新」

## 环境变量（可选覆盖）

| 变量 | 默认值 | 用途 |
|------|--------|------|
| `GITHUB_REPO` | `shaoyahu/writing-with-ai` | GitHub 仓库 |
| `RELEASE_SERVER` | `server`（SSH 别名） | SSH 服务器 |
| `REMOTE_BASE_DIR` | `/var/www/xiaozha/app` | 服务器分发目录 |

## 发布结果示例

| 通道 | GitHub Release | APK 大小 |
|------|----------------|---------|
| Release | `v{code}` | ~4-5 MB |
| Debug | `v{code}-debug` | ~20-25 MB |

下载页: https://xiaozha.nananxue.cn/app/

## 注意事项

- APK **不会**上传到服务器，所有下载流量由 GitHub CDN 承担
- 服务器只存 `version.json` + `index.html` + `release-notes/`
- GitHub Release tag 命名: release 用 `v{code}`，debug 用 `v{code}-debug`
- 脚本幂等：重跑覆盖同名文件和 GitHub Release assets
- 如果 release 编译失败（如 Lint ExtraTranslation），先修复错误再继续
