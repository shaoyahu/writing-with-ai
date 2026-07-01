# android-build-system Delta Spec (fix-m5-blockers)

## REMOVED Requirements

### Requirement: ktlint disabled rules via legacy `ktlint_disabled_rules` property
**Reason**: ktlint rule-engine 1.0+ 不认 `disabledRules` 旧 SetProperty 写法(`config/ktlint/.editorconfig` + 项目根 `.editorconfig` 启动打 18+ 行 warning，且实际 disabled rule 不生效 → ktlint 跑出 ~580 处违规)。改用 ktlint 1.0 per-rule 写法或集中到 `app/build.gradle.kts` 的 `ktlint {}` 块。

**Migration**:
- 删 `config/ktlint/.editorconfig` 中 `[*]\nktlint_disabled_rules = ...` 整段
- 删项目根 `.editorconfig` 同名 property(若存在)
- 改 ktlint 1.0 per-rule:`[*.{kt,kts}]\nktlint_standard_function-naming = disabled`(仅 `function-naming` 因 Compose PascalCase 硬冲突需禁，见 memory `ktlint-compose-pascalcase-1.0`;`multiline-expression-wrapping` 等其余 disabled rules 已经在 `app/build.gradle.kts` 的 `ktlint { disabledRules.set(...) }` 集中管，不要重复)
- 验 `./gradlew :app:ktlintCheck` 不再打 obsolete property warning，且 disabled rules 仍生效

## MODIFIED Requirements

### Requirement: ktlint static check passes with zero violations

The project MUST run ktlint in CI-equivalent mode and produce no errors; the rule set lives in `config/ktlint/.editorconfig`(per-rule 写法)+ `app/build.gradle.kts` 的 `ktlint {}` 块(集中 disabled rules)。

> **2026-06-20 全量扫描**(main 477 + test 109 = ~580 违规，top 5:`indent` 183, `trailing-comma-on-call-site` 142, `argument-list-wrapping` 34, `function-signature` 30, `trailing-comma-on-declaration-site` 23)。修复路径:
> 1. 跑 `./gradlew :app:ktlintFormat` 自动修大部分
> 2. 手工修 `app/src/main/java/com/yy/writingwithai/app/ui/theme/Type.kt` 整文件 indent(12 → 8)
> 3. 手工拆 `app/src/test/java/com/yy/writingwithai/feature/aiwriting/streaming/AiActionViewModelTest.kt:47,73,94,112,128,148,165` 多参数单行(8 参构造调 `AiActionViewModel(...)` 必须每参一行)
> 4. 验 `./gradlew :app:ktlintCheck` 0 violation

#### Scenario: ktlintCheck exits clean
- **WHEN** the user runs `./gradlew :app:ktlintCheck`
- **THEN** the task exits with status 0 AND no `error` lines are reported AND no `obsolete property 'ktlint_disabled_rules'` warning

#### Scenario: ktlint rules centralized
- **WHEN** a developer inspects ktlint configuration
- **THEN** ktlint rule overrides live in:
- (a) `config/ktlint/.editorconfig` — 用 ktlint 1.0 per-rule 写法(单 disabled rule 用 property)
- (b) `app/build.gradle.kts` 的 `ktlint { disabledRules.set(setOf(...)) }` 块 — 集中 disabled rules
- 不存在带 obsolete `ktlint_disabled_rules` 属性的 `.editorconfig`

#### Scenario: Type.kt indent 正确
- **WHEN** 读 `app/src/main/java/com/yy/writingwithai/app/ui/theme/Type.kt`
- **THEN** 所有 property 缩进为 4 的倍数(2 / 4 / 8 / 12 等)，无 `Unexpected indentation (12) (should be 8)` 类违规
