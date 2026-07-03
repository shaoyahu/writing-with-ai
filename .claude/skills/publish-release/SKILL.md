---
name: publish-release
description: 发布 APK 到 GitHub Releases + 更新服务器下载页。当用户说"发版"/"发布新版本"/"publish release"时触发。
---

发布 APK 到 GitHub Releases CDN，并更新服务器下载页的版本信息。

**Input**: 可选指定 `<versionCode> <versionName> <channel>`。如果未指定，从 `BuildConfig` 和对话上下文推断，或提示用户输入。

## 前置检查

1. **确认 gh CLI 已登录**:
   ```bash
   gh auth status
   ```
   如果未登录，提示用户先运行 `gh auth login`。

2. **确认 APK 已编译**:
   - Release 通道: 检查 `app/build/outputs/apk/release/app-release.apk` 是否存在
   - Debug 通道: 检查 `app/build/outputs/apk/debug/app-debug.apk` 是否存在
   - 如果不存在，先编译:
     ```bash
     export JAVA_HOME=/opt/homebrew/opt/openjdk@17
     ./gradlew :app:assembleRelease  # 或 :app:assembleDebug
     ```

## 执行步骤

### Step 1: 收集发布信息

向用户确认以下信息（如果未提供）:
- **versionCode**: 当前 `BuildConfig.VERSION_CODE` + 1（或用户指定）
- **versionName**: 如 "0.3.0"
- **channel**: `release` 或 `debug`（默认 release）
- **release notes**: 询问用户更新内容，或读取 `release-notes/` 下已有的文件

### Step 2: 写 release notes

如果用户没有现成的 release notes 文件，根据用户描述的内容，写入 `release-notes/{versionCode}.md`，格式：
```markdown
feat(xxx): 简短描述
fix(xxx): 修复描述
```

### Step 3: 执行发布脚本

```bash
./scripts/release-server/publish-release.sh \
  <versionCode> <versionName> \
  release-notes/<versionCode>.md \
  app/build/outputs/apk/<channel>/app-<channel>.apk \
  <channel>
```

脚本会自动完成:
1. 创建 GitHub Release（tag: `v{code}` 或 `v{code}-debug`）
2. 上传 APK 到 GitHub Release
3. 本地生成 `version.json`（apkUrl 指向 GitHub CDN）
4. 本地生成 `index.html`（下载按钮指向 GitHub CDN）
5. scp 元数据到服务器

### Step 4: 验证

1. 确认 version.json 已更新:
   ```bash
   curl -s https://xiaozha.nananxue.cn/app/<channel>/version.json | python3 -m json.tool
   ```
   检查 `apkUrl` 以 `https://github.com/` 开头。

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

## 注意事项

- APK **不会**上传到服务器，所有下载流量由 GitHub CDN 承担
- 服务器只存 `version.json` + `index.html` + `release-notes/`
- GitHub Release tag 命名: release 用 `v{code}`，debug 用 `v{code}-debug`
- 脚本幂等：重跑覆盖同名文件和 GitHub Release assets
