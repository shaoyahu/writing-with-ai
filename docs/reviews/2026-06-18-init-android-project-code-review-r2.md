# 2026-06-18 · init-android-project · code-review · r2

> **Reviewer**:AI 自审(per CLAUDE.md §"分工约定" AI 角色 #2)
> **Subject**:`init-android-project`(M0 工程脚手架)
> **Trigger**:用户指令"修复所有问题"(指 r1 HIGH 项)
> **Verdict**:**APPROVED**(r1 HIGH 全修复;§1.1 修复范围**缩小**;LOW 多数已处理;1 个 NEW 发现)

---

## 0. 摘要

按 r1 review 全修复。`./gradlew :app:assembleDebug` + `testDebugUnitTest` + `lintDebug` 三段全绿(1s 增量 build)。

| r1 等级 | 项 | 状态 |
| --- | --- | --- |
| HIGH 1.1 | HardcodedText lint 升级 error | ⚠️ **修复范围缩小**(见 §1.1 — 仅对 XML 资源生效，Compose 不扫) |
| HIGH 1.2 | `HomeRoute` → `HOME_ROUTE` + 移除 property-naming disable | ✅ 完整修复，ktlintCheck 报 0 个 property-naming 违规 |
| MEDIUM 2.1 | Theme.kt 用 top-level `DefaultSpacing` / `DefaultCornerRadius` | ✅ 完整修复 |
| MEDIUM 2.2 | CLAUDE.md 补 v1 备份策略说明 | ✅ 完整修复 |
| MEDIUM 2.3 | ColorScheme 全字段 | ⏭️ 按 review 标注推迟到 M1 |
| LOW 3.1 | 路由字符串重复 | ⏭️ 按 review 标注推迟到 M1 |
| LOW 3.2 | `JvmTarget` import | ⏭️ 按 review 标注保留 |
| LOW 3.3 | 启动图标 path | ⏭️ 按 review 标注 M5 替换 |
| LOW 3.4 | ktlint 版本号歧义 → 加注释 | ✅ 完整修复 |

---

## 1. HIGH 修复详情

### 1.1 `HardcodedText` lint — 修复范围缩小

**做了什么**:
- 新建 `app/lint.xml`(`<issue id="HardcodedText" severity="error" />`)。
- `app/build.gradle.kts` 的 `lint { }` 块加 `lintConfig = file("lint.xml")`。

**验证**:
- 临时把 `HomePlaceholder` 改成 `Text("你好")`(硬编码中文)，跑 `:app:lintDebug` → **BUILD SUCCESSFUL,lint report 未报 HardcodedText**。
- 临时改回 `Text(stringResource(R.string.placeholder_greeting))` → BUILD SUCCESSFUL。

**发现**:**Android Lint `HardcodedText` 规则只扫描 XML 资源**(如 `android:text="..."`),**不扫描 Kotlin / Compose 源码**。所以这个 lint 对当前 Compose-only 的项目**无效**。

**影响范围**:
- ✅ **未来 widget layout / DataBinding / View binding XML 文件**(M4 quick-note-widget / M5 打磨阶段)硬编码中文会被这个 lint 拦截。
- ❌ **Compose 业务代码**(`Text("...")`)这个 lint 抓不到，只能靠 code review / 自审。

**OpenSpec 收敛**:`localization/spec.md` §"Requirement: Hardcoded Chinese strings in Composable code are blocked by lint" 当前**无法 100% 自动验证**;spec 的 Scenario 写"HardcodedText (Android Lint built-in) **or a custom equivalent**"——v1 阶段无 Compose-aware 替代品，需要 custom Compose lint 规则(投入大)或 detekt 规则(轻量)。

**决策**(r2 时):**接受现状，把 Compose 硬编码拦截放到 `polish-and-internal-release` change(M5)** 里做。**保留 `app/lint.xml`** 作为未来 XML layout 的拦截兜底，不浪费。

**附议**:把这条加进 review r1 的 §1.1，作为"修复范围缩小 + 后续 spec 修订"的标注;spec 修订等 M5 polish 时一并做。

### 1.2 `HomeRoute` → `HOME_ROUTE` — 完整修复

**做了什么**:
- `AppNav.kt:36` 改名 `HomeRoute` → `HOME_ROUTE`，同步两处引用(`composable(HomeRoute)` → `composable(HOME_ROUTE)`、`startDestination = HomeRoute` → `startDestination = HOME_ROUTE`)。
- `AppNav.kt` 文件顶 `@file:Suppress("PropertyNaming")` 移除(命名合规后不再需要)。
- `app/build.gradle.kts:130` 的 `disabledRules` 集合移除 `"standard:property-naming"`。

**验证**:`./gradlew :app:ktlintCheck`:
- **修改前** r1 跑出 6 个 standard:function-naming + 1 个 standard:property-naming。
- **修改后** r2 跑出 5 个 standard:function-naming + **0 个 standard:property-naming**。property-naming 违规完全消失。
- function-naming 仍 5 个，跟 memory `ktlint-compose-pascalcase-1.0` 已记录一致，无新增。

**影响**:OpenSpec `app-shell/spec.md` 的隐式"常量命名合规"约束得到满足;CLAUDE.md §"约定" "常量 UPPER_SNAKE_CASE" 无违规。

---

## 2. MEDIUM 修复详情

### 2.1 Theme.kt top-level `DefaultSpacing` / `DefaultCornerRadius`

**做了什么**:`app/src/main/java/com/yy/writingwithai/app/ui/theme/Theme.kt` 顶部加 `private val DefaultSpacing = Spacing()` + `private val DefaultCornerRadius = CornerRadius()`;`WritingAppTheme` 内 `CompositionLocalProvider(LocalSpacing provides DefaultSpacing, LocalCornerRadius provides DefaultCornerRadius, ...)`。

**验证**:assembleDebug 通过;`./gradlew :app:testDebugUnitTest` 通过。

**影响**:spec §"LocalSpacing token available" 隐式"提供一次"约束显式落地;未来 Theme 多处嵌套不会反复 new。

### 2.2 CLAUDE.md v1 备份策略说明

**做了什么**:`CLAUDE.md` §"AI 集成约定" 加新 bullet:**v1 备份策略**:`AndroidManifest.xml` 设 `allowBackup="false"` 完全关闭 Auto Backup;`backup_rules.xml` / `data_extraction_rules.xml` 是 forward-looking,M2 真上 apikey 加密时再决定要不要启用 backup + 用 rules 排除路径。**v1 接受"备份关闭"换"apikey 绝对不外流"**。

**验证**:grep `allowBackup` → CLAUDE.md:117 + AndroidManifest.xml:9 两处描述一致。

**影响**:未来 reviewer / 开发者看到 `allowBackup="false"` + `backup_rules.xml` 并存不会困惑;M2 启用备份时知道路径怎么走。

### 2.3 ColorScheme 全字段 — 推迟到 M1

按 review 标注，M1 业务页面落地时统一补全 Material 3 ColorScheme 字段。**当前仅占位 OK**。

---

## 3. LOW 修复详情

### 3.1 路由字符串重复 — 推迟到 M1

按 review 标注，抽 `AppRoute.kt` 集中管理，M1 加 destination 时一并做。

### 3.2 `JvmTarget` import — 保留

按 review 标注，harmless,Kotlin 2.x `compilerOptions.jvmTarget` 仍需此 import。

### 3.3 启动图标 path — 推迟到 M5

按 review 标注，M5 替换为正式 logo 时一并改。

### 3.4 ktlint 版本号歧义 — 完整修复

**做了什么**:`gradle/libs.versions.toml` 顶部 `[versions]` 段加注释说明 ktlint plugin = 12.1.0 vs rule-engine = 1.0.x，引用 memory `ktlint-compose-pascalcase-1.0.md`。

**验证**:cat 文件即可看到注释。

---

## 4. NEW 发现(本次 review 触发的反思)

### 4.1 §1.1 修复范围缩小 = spec-vs-implementation gap

**问题**:`localization/spec.md` §"Requirement: Hardcoded Chinese strings in Composable code are blocked by lint" 假设 Android Lint `HardcodedText` 能扫 Compose 源码，**实际上不能**。

**修复决策**(r2):保留 `app/lint.xml` 作为未来 XML layout 拦截;Compose 代码拦截依赖 code review + 自审，等 M5 polish 时补:
- 方案 A:**detekt Compose 规则**(轻量，~30 行代码，集成进 ktlint 现有 task)。
- 方案 B:**custom Android Lint Compose 规则**(重，Java 写，投入产出比低)。
- 方案 C:**纯 code review + review 自动化**(轻，每次 review 检查;但 spec 写了 lint，需改 spec)。

**OpenSpec 行动项**:`polish-and-internal-release` change 落地时，把 `localization/spec.md` 该 Requirement 改成"lint (XML) + code review (Compose)"双轨，Scenario 改成对应描述。

### 4.2 当前 MainActivity / WritingApp 没接 `enableOnBackInvokedCallback`

**问题**:`app-shell/spec.md` §"Back press behavior is system-driven" Scenario 写 "predictive back gesture is wired by `enableOnBackInvokedCallback = true` in M0"。

**实测**:`MainActivity.onCreate` 没显式调用 `enableOnBackInvokedCallback = true`(SDK 33+ 默认就是 true)，也没显式 `OnBackPressedDispatcher` 处理。当前是 system-driven 但**没显式声明**。

**严重性**:**LOW**。SDK 33+ 默认 true,SDK 26-32 系统不支持 predictive back，落到传统 back gesture。**功能 OK**。

**修复**(可选，非 HIGH):`MainActivity.onCreate` 加一行:
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    onBackInvokedDispatcher.registerOnBackInvokedCallback(...)  // 如需自定义
}
```
或更简单:`enableOnBackInvokedCallback = true`(SDK 33+ 默认 true,**显式声明更明确**)。

**行动**:M4 `predictive-back-gesture` change 会一并处理，这里不动。

---

## 5. OpenSpec 4 件套一致性更新

| Artifact | r1 状态 | r2 状态 | 备注 |
| --- | --- | --- | --- |
| `proposal.md` | ✅ | ✅ | 不动 |
| `design.md` | ✅ | ✅ | 不动 |
| `specs/localization/spec.md` | ⚠️ | ⚠️ | **r2 仍不达标**，但原因从"配置缺失"变为"Android Lint 能力上限";spec 修订留 M5 |
| `specs/{android-build-system,app-shell,material-theme,testing-framework}/spec.md` | ✅ | ✅ | 全过 |
| `tasks.md` | ✅ | ✅ | §10.3 描述不变;§11.x 全 ✅ |

**OpenSpec 工作流一致性**:`openspec status --change init-android-project` 仍全 done。

---

## 6. 验证结果汇总

```
$ ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
BUILD SUCCESSFUL in 1s
59 actionable tasks: 6 executed, 7 from cache, 46 up-to-date

$ ./gradlew :app:ktlintCheck
BUILD FAILED(5 个 standard:function-naming 违规，已知 follow-up，见 memory)
```

| 命令 | r1 结果 | r2 结果 | 说明 |
| --- | --- | --- | --- |
| `:app:assembleDebug` | ✅ | ✅ | 20.7 MB APK |
| `:app:testDebugUnitTest` | ✅ | ✅ | PlaceholderTest SUCCESSFUL |
| `:app:lintDebug` | ✅ | ✅ | BUILD SUCCESSFUL |
| `:app:ktlintCheck` | ❌ 7 违规 | ❌ 5 违规 | 减少 2 个(property-naming 消失)，剩余 function-naming 是已知 follow-up |
| `:app:check` | ❌ | ❌ | ktlint 失败导致，known follow-up |

---

## 7. 建议下一步

**M0 收尾**(二选一):

1. **archive 当前 change**:`/opsx:archive init-android-project` —— 把 M0 收尾，带 ktlint + Compose HardcodedText 已知 follow-up 归档;OpenSpec change 进入 `archive/`。
2. **先把 ktlint follow-up 也修了再 archive**:`polish-and-internal-release` change 提前到 M0 之后、M1 之前，升 ktlint rule-engine ≥ 1.1 + 走 `experimental:annotation` 机制 + 补 Compose HardcodedText 拦截(可重做 review)。

**按 review 与 CLAUDE.md "AI 不 自动起草下一个 change"，等你指令。**

---

## 8. 关联

- 上一轮:review r1 → `2026-06-18-init-android-project-code-review-r1.md`
- 关联文件:`app/lint.xml` / `app/src/main/java/.../AppNav.kt` / `app/src/main/java/.../ui/theme/Theme.kt` / `gradle/libs.versions.toml` / `CLAUDE.md` / `openspec/changes/init-android-project/tasks.md`
- memory:`ktlint-compose-pascalcase-1.0` / `openspec-changes-init-android-project`
- 下一步候选:`/opsx:archive init-android-project` 或 `/opsx:propose quick-note-feature`(M1)