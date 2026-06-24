# Release 发布清单

每次发版走这 5 步,顺序不可乱。中断后重跑幂等。

## 1. 编译 release APK

```bash
./gradlew :app:assembleRelease
```

产物:`app/build/outputs/apk/release/app-release.apk`

keystore 配置见 [`signing.md`](signing.md)。第一次跑会报错缺凭据,先按 signing.md 配 `~/.gradle/gradle.properties`。

## 2. 写 release notes

写 `release-notes/{versionCode}.md`,多行 markdown:

```markdown
feat(my): 检查更新 + 下载页
fix(sync): updateDoc 第二次同步 404
chore: 升级 compose-bom
```

存到本机 `release-notes/` 目录,脚本会 `scp` 到服务器 `release-notes/` 子目录。

## 3. publish-release.sh 一行发版

```bash
./scripts/release-server/publish-release.sh 12 0.5.0 release-notes/12.md app/build/outputs/apk/release/app-release.apk
```

参数:`<versionCode> <versionName> <notes.md> <app-release.apk>`

脚本串 4 步(scp APK / scp notes / ln -sfn / rebuild manifest + index.html),任一失败立即退出。

环境变量覆盖(可选):
- `RELEASE_SERVER`(默认 `root@xiaozha.nananxue.cn`)
- `REMOTE_DOWNLOAD_DIR`(默认 `/var/www/xiaozha/app/download`)

## 4. 验服务端

```bash
curl -I https://xiaozha.nananxue.cn/app/download/      # → 200
curl https://xiaozha.nananxue.cn/app/version.json | jq # → versionCode/12/versionName/0.5.0
curl https://xiaozha.nananxue.cn/app/download/writing-with-ai-12.apk -o /tmp/test.apk
sha256sum /tmp/test.apk                                  # 对比 version.json 的 apkSha256
```

## 5. 真机 e2e

```bash
./gradlew :app:installDebug   # 装旧版
```

手机/模拟器:
1. 打开 App → 「我的」tab → 「关于」
2. 点「检查更新」
3. 应弹 dialog,显示新版本号 + release notes
4. 点「下载」,通知栏出现下载进度
5. 下载完 → 自动跳系统安装器 → 装上新版

## 失败回滚

新版有问题:
```bash
ssh root@xiaozha.nananxue.cn 'cd /var/www/xiaozha/app/download && rm writing-with-ai-12.apk && ./build-version-json.py > version.json'
```

manifest 自动回退到下一最高 versionCode(11)。

## 不做的事

- 不上架应用市场(roadmap §20)
- 不自动备份 APK(后续 cron 任务再说)
- 不发 split APK per ABI(只发 universal)
- 不强制更新(`mandatory` 字段预留,本次 App 端不消费)