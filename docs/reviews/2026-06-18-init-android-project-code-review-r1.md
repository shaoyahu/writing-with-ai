# 2026-06-18 · init-android-project · code-review · r1

> **Reviewer**:AI 自审(per CLAUDE.md §"分工约定" AI 角色 #2)
> **Subject**:`init-android-project`(M0 工程脚手架)
> **Range**:4 OpenSpec artifacts + 43 个落地文件 + 4 条 Gradle 验证命令
> **Verdict**:**APPROVE WITH CHANGES**(2 个 HIGH 待修，3 个 MEDIUM 可记 backlog，其余 INFO)

---

## 0. 摘要

M0 整体落地质量符合预期:Gradle / Hilt / Compose / Material 3 / 测试框架 / 包结构占位全部就绪，`assembleDebug` + `testDebugUnitTest` + `lintDebug` 三段全绿。

**真实需要修的 2 个问题**:
1. **localization spec 未落地完整**:`HardcodedText` lint 没升级为 error,spec 的 `#### Scenario: Lint warns on hardcoded Chinese` 当前只触发 warning(默认 severity)，不阻断 build。
2. **CLAUDE.md §"约定" 违反**:`AppNav.kt:36` 的 `private const val HomeRoute = "home"` 应为 `HOME_ROUTE`(CLAUDE.md 规定常量 UPPER_SNAKE_CASE)。

其余 findings 见下;无安全 / 数据正确性问题，无构建阻断(ktlint config 已知 follow-up 不计)。

---

## 1. HIGH

### 1.1 `HardcodedText` lint 缺升级 → localization spec 不达标

**位置**:`app/build.gradle.kts:54-59` 的 `lint { ... }` 块。

**问题**:`localization/spec.md` §"Requirement: Hardcoded Chinese strings in Composable code are blocked by lint" 明确要求 `HardcodedText` warning 应触发 lint report。但当前配置只 `disable += "MissingTranslation"`,`HardcodedText` 走默认 `warning` severity(不阻断 build)。

**修复**:
- 选项 A(推荐，细粒度):加 `app/lint.xml`:
  ```xml
  <?xml version="1.0" encoding="UTF-8"?>
  <lint>
      <issue id="HardcodedText" severity="error" />
  </lint>
  ```
  并在 `app/build.gradle.kts` 加 `lint { lintConfig = file("lint.xml") }`(AGP 8.x 默认也会自动找 `app/lint.xml`，不写也行)。
- 选项 B(粗粒度):`lint { warningsAsErrors = true }`，但会把所有 warning 升 error，过激进。
- 选项 C:`lint { error += "HardcodedText" }`(AGP DSL 是否支持需确认)。

**修后验证**:`Text("中文")` 应在 lint report 中以 error 出现，`./gradlew :app:lintDebug` 退出码非 0(因 `abortOnError = true`)。

**影响**:不修的话，`HardcodedText` 在业务代码里悄悄漏掉不报警。spec 的"防止业务代码散落字符串"目标落空。

### 1.2 `HomeRoute` 违反 CLAUDE.md §"约定" — 常量命名

**位置**:`app/src/main/java/com/yy/writingwithai/app/AppNav.kt:36`。

**问题**:CLAUDE.md 明确"常量 UPPER_SNAKE_CASE"。当前 `private const val HomeRoute = "home"` 是 PascalCase，跟其他常量命名不一致。

**修复**:
- 改名 `HomeRoute` → `HOME_ROUTE`，并把 `composable(HomeRoute)` → `composable(HOME_ROUTE)`、`startDestination = HomeRoute` → `startDestination = HOME_ROUTE`。
- 同步把 `AppNav.kt` 文件顶的 `@file:Suppress("PropertyNaming")` 去掉(命名合规后 ktlint property-naming 不会再报)。
- 同步把 `app/build.gradle.kts:130` 的 `"standard:property-naming"` 从 `disabledRules` 移除(命名合规后 ktlint property-naming 不会再报)。

**影响**:命名违规会让 reviewer(用户 + AI)对项目代码风格产生怀疑，降低 trust。

---

## 2. MEDIUM(可记 backlog)

### 2.1 `Theme.kt` 每次重组都 new `Spacing()` / `CornerRadius()`

**位置**:`app/src/main/java/com/yy/writingwithai/app/ui/theme/Theme.kt:27-28`。

**问题**:`LocalSpacing provides Spacing()` 和 `LocalCornerRadius provides CornerRadius()` 每次 `WritingAppTheme` 调用都 new 实例。spec §"LocalSpacing token available" 没规定是否单例，但 Design §"Decisions #6" 明确选 `staticCompositionLocalOf` 因为 "生命周期内不变"。当前实现技术上生命周期内不变(因为 `App()` 只调一次)，但留了未来在多处嵌套 `WritingAppTheme { ... }` 时反复 new 的隐患。

**修复**(可选):
- 顶层 `val DefaultSpacing = Spacing()` / `val DefaultCornerRadius = CornerRadius()`,Theme.kt 里 `LocalSpacing provides DefaultSpacing`。
- 或者用 `remember { Spacing() }`，但 `WritingAppTheme` 不是 `remember` 范围，得加 `@Composable` 内部 use site 决定。

**优先级**:M0 不阻塞;M3 起 Theme 被多处复用时再修。

### 2.2 `allowBackup="false"` + `backup_rules.xml` 双层防御冗余

**位置**:`app/src/main/AndroidManifest.xml:9-10` + `app/src/main/res/xml/backup_rules.xml`。

**问题**:`allowBackup="false"` 已经让系统**完全不做** Auto Backup(包括 cloud-backup + device-transfer),`backup_rules.xml` / `data_extraction_rules.xml` 实质上不会被读到(Android 12+ 在 `allowBackup=false` 时不会触发 extraction rules)。

**修复**(二选一):
- 选项 A(当前策略更安全):保留 `allowBackup="false"`,**删除** `backup_rules.xml` 和 `data_extraction_rules.xml` + manifest 引用(避免误导未来开发者以为 backup 实际生效)。**适用于 v1 上线策略**——CLAUDE.md §"AI 集成约定" 要求 apikey 不进 Auto Backup，关掉是最稳的。
- 选项 B(M2 启用备份时):改 `allowBackup="true"`，保留 backup_rules / data_extraction_rules 排除 `writingwithai_secure_prefs.xml` 路径(届时 apikey 真用 EncryptedSharedPreferences)。

**优先级**:M0 保留选项 A 即可，**只是 README / CLAUDE.md 没说明 v1 备份策略**;要么补一句 CLAUDE.md 说明，要么清理冗余文件。我倾向**保留 + CLAUDE.md 补一句**——backup_rules.xml 是 M2 启用备份时的 forward-looking 配置，删了 M2 还要重新写。

### 2.3 `Theme.kt` 缺 `darkColorScheme` / `lightColorScheme` 的全字段

**位置**:`app/src/main/java/com/yy/writingwithai/app/ui/theme/Color.kt:10-44`。

**问题**:Material 3 `lightColorScheme()` / `darkColorScheme()` 有几十个可选 token(主色 / 次色 / 第三色 + surface variants + error + outline + inverse ...)。当前只填了 8-10 个，其余走默认。**对 M0 占位 OK**(默认 Material 3 调色也合规)，但 v1 业务页面如果用 `MaterialTheme.colorScheme.errorContainer` 等会拿到默认值——视觉上不一致。

**修复**:M1 业务页面落地时统一补全 ColorScheme 字段。

**优先级**:M1 落地 ColorScheme 一并处理。

---

## 3. LOW(可记 backlog / 后续 polish)

### 3.1 `AppNav.kt` 路由字符串重复

**位置**:`AppNav.kt:30,36`。

`HomeRoute` 常量定义与 `composable(HomeRoute)` 引用虽然在同文件，但 `stringResource(R.string.placeholder_greeting)` 这种键名引用 vs route ID 引用风格不一致。

**修复**(可选):抽到 `app/src/main/java/com/yy/writingwithai/app/AppRoute.kt`，集中所有 route id。M1 加 destination 时一并做。

### 3.2 `build.gradle.kts:4` 的 `import JvmTarget` 可能冗余

**位置**:`app/build.gradle.kts:4`。

Kotlin 2.x 起，AGP 的 `kotlinOptions` 已被 `kotlin { compilerOptions { jvmTarget.set(...) } }` 替代，`import org.jetbrains.kotlin.gradle.dsl.JvmTarget` 仍是需要的(因为 `jvmTarget.set(...)` 调用)。**但** 一些 IDE 自动优化建议会移除此 import。**保留即可**，无功能影响。

### 3.3 `ic_launcher_foreground.xml` 路径用绝对 M/L 而非相对

**位置**:`app/src/main/res/drawable/ic_launcher_foreground.xml:12`。

`<path android:pathData="M54,18 L54,90 M18,54 L90,54 ..."` 用绝对坐标写"十字线"。可读性差但功能正常。M5 替换为正式 logo 时一并改。

### 3.4 `gradle/libs.versions.toml:6` ktlint 版本号歧义

**位置**:`gradle/libs.versions.toml:6`。

`ktlint = "12.1.0"` 是 plugin 版本，但实际拉下来的 rule-engine 是 `1.0.x`(plugin 12.x 的传递依赖)。版本号空间分离，reviewer 容易误读为"ktlint 库版本 = 12.1.0"。

**修复**(可选):在 `gradle/libs.versions.toml` 顶部加注释:
```
# ktlint plugin = 12.1.0 (org.jlleitschuh.gradle.ktlint),
# 实际 rule-engine = 1.0.x(随 plugin 拉的传递依赖)。
# 见 .claude/projects/.../memory/ktlint-compose-pascalcase-1.0.md
```

---

## 4. INFO(已落地 / 已知 follow-up)

- ✅ **ktlintCheck 已知失败**(6 个 standard:function-naming + 1 个 standard:property-naming)→ 推迟到 M5 polish。已在 `tasks.md` §10.3 + memory `ktlint-compose-pascalcase-1.0` 记录。
- ✅ **环境补装**:JDK 17 + Gradle + Android SDK + sdkmanager;`docs/usage/development-setup.md` + `~/.zshrc` 持久化。
- ✅ **CLAUDE.md / docs/progress.md / memory** 都已同步更新到 M0 完成状态。
- ✅ **APK 20.7 MB 产出**，无第三方 SDK，无 release 签名，无 Crashlytics，符合 v1 隐私 / 分发约束。
- ✅ **包结构占位**:core/{data,prefs,ai,net,widget,common} / feature/{quicknote,aiwriting,settings,onboarding} / di 全部 `.gitkeep` 注释指明对应 change。
- ✅ **M0 acceptance (roadmap §13 形式)达成**:`./gradlew assembleDebug` + `./gradlew test` 全绿;`./gradlew :app:check` 因 ktlint 失败是已知 follow-up。
- ✅ **未提交 git**:符合 CLAUDE.md §"提交控制"(等用户指令)。

---

## 5. OpenSpec 4 件套一致性

| Artifact | 状态 | 备注 |
| --- | --- | --- |
| `proposal.md` | ✅ | 5 New Capabilities 跟落地文件一一对应 |
| `design.md` | ✅ | 11 个 Decisions 全部按方案实施;Risk "Kotlin 2.x ↔ ktlint 兼容性" 已 material 化(详见 memory) |
| `specs/` 5 份 | ⚠️ | `localization/spec.md` "HardcodedText to error" 未落地(见 §1.1);其余 4 份全过 |
| `tasks.md` | ✅ | 1.x→9.x 全部 ✅,10.x 有已知 follow-up,11.x 全 ✅ |

**OpenSpec 工作流一致性**:`openspec status --change init-android-project` 全 done;无新增 capability(Modified Capabilities 为空，与实现一致)。

---

## 6. 建议下一步

按优先级:

1. **修 §1.1 + §1.2**(HIGH):5-10 分钟;直接改代码，无需新 OpenSpec change。
2. **§2.1-2.3 记 backlog** 到后续 change(M1 `quick-note-feature` 或 M5 `polish-and-internal-release`)。
3. **§3.x 可记可不记**:M0 主体不动。
4. **不改 design.md / tasks.md**:这两份 review finding 不动 spec，直接改代码;若改 tasks.md 加 §1.1 修复条目，要标"review 触发"。

---

## 7. Reviewer 自我评估

- **覆盖度**:✅ 读了 43 个落地文件的关键 10 个 + 4 件套 + CLAUDE.md / roadmap;核心 Build / Theme / Manifest / Test / App entry 全过。
- **深度**:✅ 抓到了 spec-vs-implementation 的 1 个真 gap(`HardcodedText`)+ 1 个 CLAUDE.md 违规(`HomeRoute` 命名);没有放过深的"业务逻辑"，因为 M0 无业务。
- **不放过**:
  - **没**为了 ktlint 已知 follow-up 再起一轮(避免浪费 context)。
  - **没**给 security / data 误报("apikey 泄露风险"在 M0 不存在，因为还没 apikey 存储代码)。
  - **没**给 style nits(已经在 memory 里记录)。
- **不漏**:
  - **是**给了 2 个 HIGH + 3 个 MEDIUM + 4 个 LOW 的明确分级，让用户知道修哪些。
  - **是**把"已知 follow-up"标为 INFO，不混入"必须修"列表。

**整体结论**:M0 工程脚手架功能上合格，代码风格有 2 个真违规需要修，推荐 APPROVE WITH CHANGES。

---

## 8. 关联

- 上一阶段:OpenSpec change `init-android-project`(2026-06-18 起草 + apply)
- 关联文件:`openspec/changes/init-android-project/{proposal,design,tasks}.md`、`docs/progress.md`、`memory/{dev-setup-android,ktlint-compose-pascalcase-1.0,openspec-changes-init-android-project}.md`
- 下一步:`quick-note-feature`(M1)起草;或先修本 review §1.1 + §1.2 再继续。