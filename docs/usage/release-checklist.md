# Release 发布清单

每次发版走这 5 步，顺序不可乱。中断后重跑幂等。

## 1. 编译 release APK

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew :app:assembleRelease
```

产物:`app/build/outputs/apk/release/app-release.apk`

keystore 配置见 [`signing.md`](signing.md)。第一次跑会报错缺凭据，先按 signing.md 配 `~/.gradle/gradle.properties`。

## 2. 写 release notes

写 `release-notes/{versionCode}.md`，多行 markdown:

```markdown
feat(my): 检查更新 + 下载页
fix(sync): updateDoc 第二次同步 404
chore: 升级 compose-bom
```

## 3. 一行发版

```bash
./scripts/release-server/publish-release.sh 12 0.5.0 release-notes/12.md app/build/outputs/apk/release/app-release.apk
```

参数:`<versionCode> <versionName> <notes.md> <app-release.apk> [debug|release]`

脚本串行执行:
1. 创建 GitHub Release + 上传 APK（CDN 托管）
2. 本地生成 version.json（apkUrl 指向 GitHub Releases）
3. 本地生成 index.html（下载按钮指向 GitHub Releases）
4. scp version.json + index.html + release notes 到服务器
5. **APK 不上传到服务器**，下载流量由 GitHub CDN 承担

环境变量覆盖(可选):
- `GITHUB_REPO`(默认 `shaoyahu/writing-with-ai`)
- `RELEASE_SERVER`(默认 `server`，SSH 别名)
- `REMOTE_BASE_DIR`(默认 `/var/www/xiaozha/app`)

## 4. 验服务端

```bash
curl https://xiaozha.nananxue.cn/app/release/version.json | python3 -m json.tool
# 确认 apkUrl 以 https://github.com/ 开头
curl -sI https://xiaozha.nananxue.cn/app/ | head -1  # → 200
```

## 5. 真机 e2e

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew :app:installDebug   # 装旧版
```

手机/模拟器:
1. 打开 App → 「我的」tab → 「关于」
2. 点「检查更新」
3. 应弹 dialog，显示新版本号 + release notes
4. 点「下载」，通知栏出现下载进度
5. 下载完 → 自动跳系统安装器 → 装上新版

## 失败回滚

新版有问题:
```bash
gh release delete v12 --yes --repo shaoyahu/writing-with-ai
# 然后重新发旧版:
./scripts/release-server/publish-release.sh 11 0.4.0 release-notes/11.md app-release.apk
```

## Debug 通道

发测试版:
```bash
./scripts/release-server/publish-release.sh 12 0.5.0 release-notes/12.md app/build/outputs/apk/debug/app-debug.apk debug
```

GitHub tag 会自动加 `-debug` 后缀(如 `v12-debug`)，与 release 通道共存。

## 不做的事

- 不上架应用市场(roadmap §20)
- 不自动备份 APK(后续 cron 任务再说)
- 不发 split APK per ABI(只发 universal)
- 不强制更新(`mandatory` 字段预留，本次 App 端不消费)
