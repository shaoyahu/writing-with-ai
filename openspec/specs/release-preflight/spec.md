# release-preflight Specification

## Purpose

release build 前的预检查，避免常见 release 阻塞项（TODO 占位文本、apikey 明文、缺失 backup_rules.xml、ktlint 违规）进入发版流程。**实装路径选择**:`Gradle Task `:app:checkReleaseReadiness`` 改为发布 `scripts/release-server/publish-release.sh` 内嵌的 5 步（gh auth / upload / 元数据生成 / scp / verify） + `docs/usage/release-checklist.md` 步骤 1-2 的人工跑 `./gradlew :app:assembleRelease` + `./gradlew :app:ktlintCheck` + `./gradlew :app:testDebugUnitTest` 兜底。

Synced from OpenSpec change `release-preflight-automation`(2026-07-03)。

## Requirements

### Requirement: 发布发布前 4 项 preflight 必须有结果

发布 APK 之前 MUST 出现以下 4 项 preflight 的"OK / 失败"明确结论:

1. **TODO 占位文本扫描**:`values-en/strings.xml` 不应含 `TODO` / `FIXME` / `[TBD]` 等占位字面量(避免漏翻译警报上线)
2. **apikey 明文扫描**:`app/src/main/java` 不应含 `sk-` / `Bearer sk-` / 真实样例 apikey 字面量(避免误提交凭证)
3. **backup_rules.xml 存在**:`res/xml/backup_rules.xml` 或 `data_extraction_rules.xml` 至少存在其一(AndroidManifest `android:fullBackupContent` 引用)
4. **ktlint 0 violations**:`./gradlew :app:ktlintCheck` 退出码 0

任一项失败 MUST 阻断发布动作(`.publish-release.sh` 流程中止并打印失败项 + `file:line`)。

#### Scenario: 全部 4 项通过
- **WHEN** 发布 release 通道 APK 之前
- **THEN** 4 项 preflight 全 OK,`./gradlew :app:assembleRelease` 继续

#### Scenario: TODO 占位文本阻断发布
- **WHEN** `values-en/strings.xml` 含 `TODO` 字面量
- **THEN** `publish-release.sh` 在 [2/5] 阶段中止,stderr 打印 `preflight fail: [TODO placeholder] file=values-en/strings.xml:42`

#### Scenario: apikey 明文扫描阻断
- **WHEN** `app/src/main/java` 含 `sk-1234567890abcdef` 字面量
- **THEN** `publish-release.sh` 中止,stderr 打印 `preflight fail: [plaintext apikey] file=...:line`

#### Scenario: 缺失 backup_rules.xml 阻断
- **WHEN** `res/xml/backup_rules.xml` 与 `data_extraction_rules.xml` 都不存在
- **THEN** `publish-release.sh` 中止,stderr 打印 `preflight fail: [missing backup_rules.xml]`

#### Scenario: ktlint violation 阻断
- **WHEN** `./gradlew :app:ktlintCheck` 退出码非 0
- **THEN** `publish-release.sh` 在 [2/5] 之前中止,stderr 打印 `preflight fail: [ktlint violations, see ktlintCheck report]`

#### Scenario: Debug 通道跳过 preflight
- **WHEN** 发布 debug 通道 APK(`channel=debug`)
- **THEN** preflight 全跳过(debug 通道不计入 release 阻塞项,允许发测试包带 TODO 占位)

### Requirement: 等价实装路径已固化为发布脚本

**实装现状**:本次 preflight 未以 `:app:checkReleaseReadiness` Gradle Task 形式实现;改为**publish-release.sh 内嵌的前置 sanity check**:
- `[0/5]` 检查 `gh auth status`
- `[2/5]` 验证 `version.json` 是合法 JSON(`python3 -c "import json; json.load(open(...))"`)
- 在 `[0/5]` 之前 user 手动跑 `./gradlew :app:assembleRelease` + `ktlintCheck` + `testDebugUnitTest`(在 `docs/usage/release-checklist.md` §1 §5.1-5.3)

**后续推进**:v1.1 在 `app/build.gradle.kts` 加 `:app:checkReleaseReadiness` Gradle Task 把 4 项 grep 自动化(见 `docs/usage/known-issues.md` KI-012,状态 `[open]` → v1.1)。

#### Scenario: publish-release.sh 入口校验 gh
- **WHEN** 跑 `./scripts/release-server/publish-release.sh <code> <name> notes.md <apk> release`
- **THEN** 脚本顶部 `if ! gh auth status &>/dev/null; then echo "error: gh not authenticated"; exit 1; fi`,未授权 user 中止脚本

#### Scenario: publish-release.sh validate generated version.json
- **WHEN** `[2/5]` 阶段本地 `build-version-json-local.py` 输出 version.json
- **THEN** 脚本调 `python3 -c "import json; json.load(open(VERSION_JSON))"` 校验合法,失败 exit 1

#### Scenario: docs/usage/release-checklist.md 文档化手动 preflight
- **WHEN** 跑发布流程前 user 查 `docs/usage/release-checklist.md` §1-5
- **THEN** 步骤 1 列明 `./gradlew :app:assembleRelease` 编译验证;步骤 5 列明 ktlint + 单测

### Requirement: 已知 issue KI-012 状态维持在 `[open]`(待 v1.1)

`docs/usage/known-issues.md` KI-012（标题: "app/build.gradle.kts release buildType 缺 prebuild 校验任务"）状态 MUST 保持 `[open]` 直到 v1.1 实现 `:app:checkReleaseReadiness` Gradle Task。不在本 change archive 时改 `[resolved]` —— 这是对本 change 实际进展的诚实记录。

#### Scenario: KI-012 仍 open
- **WHEN** user 查看 `docs/usage/known-issues.md`
- **THEN** KI-012 状态 = `[open]`,fix plan 仍指 v1.1
