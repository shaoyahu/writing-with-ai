## Why

v1 内测 / release 发布前 4 项 grep preflight 检查(无 TODO 字符串 / apikey 无明文 / backup_rules 配置 / ktlint 0 violation)目前是文档规范,无构建阻断。R6 review <80 finding + KI-012 标记 v1.1 修复。手动 grep 容易漏,自动化构建期校验价值大,且为后续 release preflight 4 项检查项中"known-issues ≥4 条"做基建。

## What Changes

- 新增自定义 Gradle Task `checkReleaseReadiness`,跑 4 项 grep:
  1. `values-en/strings.xml` 无 `__TODO__` 占位
  2. `app/src/main/java/com/yy/writingwithai/` 路径下无明文 `sk-` / `apikey=` 字面量
  3. `AndroidManifest.xml` `android:allowBackup="false"` + `res/xml/{backup_rules,data_extraction_rules}.xml` 配置存在
  4. ktlint 0 violations(委托 `./gradlew :app:ktlintCheck` + 解析输出)
- `app/build.gradle.kts` `release.buildTypes` 块加 `dependsOn("checkReleaseReadiness")`,release 构建前自动跑校验
- 校验失败时 Gradle 构建失败 + 错误信息指出违反项 + 文件路径 + 行号
- 新增 Task 单测(JVM,不动 Android Framework)

## Capabilities

### New Capabilities

- `release-preflight`: 自定义 Gradle Task + 4 项 grep 校验 + release buildType 钩子

### Modified Capabilities

- `release-readiness`: 增加 1 个 Requirement: release 构建前 MUST 自动跑 `checkReleaseReadiness` Task(覆盖原 4.1 "文档规范"为"构建阻断");新增 2 个 Scenario(`Task exists and passes` / `any preflight check fails blocks release`)

## Impact

- `app/build.gradle.kts` — `release.buildTypes` 加 `dependsOn("checkReleaseReadiness")`;新增 Task 注册
- 新建 `app/buildSrc/src/main/kotlin/CheckReleaseReadinessTask.kt` — 自定义 Task 实现,4 项 grep 用 `exec` + `grep` 命令调用(沿用项目内 grep 调用模式)
- 新建 `app/src/test/java/com/yy/writingwithai/buildlogic/CheckReleaseReadinessTaskTest.kt` — JVM 单测,准备 fixture 目录验证 pass / fail
- 不引入新依赖(`grep` + `find` 走系统命令,与现有 release-readiness grep 一致)
- 不改业务代码、不改 release manifest 内容