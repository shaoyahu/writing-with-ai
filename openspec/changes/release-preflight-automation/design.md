## Context

v1 内测 / release 发布前存在 4 项 grep preflight 检查(无 TODO 字符串 / apikey 无明文 / backup_rules 配置 / ktlint 0 violation)。当前为文档规范，发布前用户手跑 grep，易遗漏。KI-012 + R6 review <80 finding 标记 v1.1 修复:构建期自动校验，失败阻断 release 出包。

CLAUDE.md "AI 集成约定" 硬规则:apikey 仅本机加密存储，严禁进 Room / 明文 SharedPreferences / logcat / Auto Backup / `BuildConfig`。4 项 grep 是这套硬规则的工程化兜底。

## Goals / Non-Goals

**Goals:**
- `release` buildType 构建前自动跑 4 项 grep，失败阻断
- 错误信息含违反文件路径 + 行号，人工排查无歧义
- Task 单测覆盖 pass / fail 两条路径，不依赖真实 Android Framework
- 不引入新依赖(走 `ExecOperations` + 系统 grep/find，与现有 release-readiness grep 一致)

**Non-Goals:**
- 不改业务代码、不改 release manifest 内容(只校验，不修复)
- 不在 debug 构建期跑(用户内测阶段可能临时插入 TODO 调试，不应阻塞 debug)
- 不替代 `ktlintCheck` / `testDebugUnitTest` / `check`(这 3 个仍走 `./gradlew :app:check`)
- 不实现 release preflight 第 5 项 "known-issues ≥4 条"(那需要 KI 状态机校验，本 change 不做)

## Decisions

### D1: 自定义 Gradle Task 走 `app/build.gradle.kts` 内联而非 buildSrc 独立模块

**选内联**:`tasks.register("checkReleaseReadiness")` 直接写在 `app/build.gradle.kts` 末尾，Task 类不抽到 buildSrc 独立模块。

**否决 buildSrc**:项目当前无 buildSrc 模块，引入一个新模块成本 > 收益;Gradle Task 内联 + JVM 单测直接覆盖 `exec` 输出解析，可行;后续若 Task 数量增加，可迁移到 `buildSrc`。

### D2: 4 项 grep 走 `ExecOperations` 而非 Kotlin 文件 IO

**选 ExecOperations**:项目其他 release-readiness 校验脚本走 `exec` + `grep` + `find` 系统命令(`docs/usage/release-readiness.spec.md` §4.1 已规范)。统一范式，无需重新实现 grep 语法(正则 + 多文件 + 行号 = `grep -rn -E` 一行)。

**替代方案**:用 `java.io.File.readLines()` + Kotlin 正则解析。否决:要重新实现"递归遍历子目录 + 行号输出 + 多 pattern OR"，复杂度 > 直接调 grep。

### D3: ktlint 校验不放进 `checkReleaseReadiness`，改 `release.buildTypes` 同时 dependsOn 两个

**选双 dependsOn**:`release.buildTypes.dependsOn("checkReleaseReadiness", "ktlintCheck")`。

**否决内嵌**:`checkReleaseReadiness` 内 `exec ./gradlew :app:ktlintCheck` 会让 Gradle 跑 2 次 ktlint(Task 间依赖 `release` → `checkReleaseReadiness` → `ktlintCheck` + `release` 隐式拉 `check` → `ktlintCheck`)。双 dependsOn 让两者独立失败时分别报，Gradle 错误信息清晰，无重复跑。

### D4: 校验失败信息格式

固定格式:`Preflight FAILED [check-N]: file:line — pattern`，其中 `check-N` 是 1~4 编号(对应 proposal §What Changes 列出的 4 项)。方便人工 grep 错误信息 + 用户按编号查文档。

### D5: grep pattern 严格匹配

- `__TODO__`:`grep -rn '__TODO__' app/src/main/res/values-en/strings.xml`
- apikey 明文:`\bapikey\s*=\s*"[a-zA-Z0-9_-]{16,}"`(必须赋值 + 引号 + 长度 ≥16，排除注释 / 字符串中 `apikey` 字面量)
- backup_rules 存在:`test -f app/src/main/res/xml/backup_rules.xml && test -f app/src/main/res/xml/data_extraction_rules.xml`
- ktlint:由 `release.buildTypes.dependsOn("ktlintCheck")` 独立跑

## Risks / Trade-offs

- **[R1] 系统 grep 不在 Windows 默认 PATH** → Gradle Task 在 CI 跑(macOS / Linux)正常，Windows 跑 release 需 Git Bash 或 WSL。**Mitigation**:README + `docs/usage/release-readiness.spec.md` 注明 Windows 限制;CI 跑 macOS。
- **[R2] 误报——业务代码注释包含 `apikey` 字面量** → D5 用 `\bapikey\s*=\s*"..."` 严格匹配赋值 + 引号，不是裸字符串。
- **[R3] Task 单测不能完整覆盖 Gradle Task 注册** → 单测覆盖 Task 内部的 "grep 输出 → 失败列表" 解析逻辑;Task 注册本身由 `app/build.gradle.kts` 编译时校验。**Mitigation**:核心解析逻辑抽到独立 `internal` 函数 + 单测。
- **[R4] backup_rules / data_extraction_rules 文件存在但内容不符合预期** → 只检查存在性，内容合规由 R3 review 兜底。**Mitigation**:本 change 仅做存在性校验，与 [real-provider-integration] 推迟项对齐。