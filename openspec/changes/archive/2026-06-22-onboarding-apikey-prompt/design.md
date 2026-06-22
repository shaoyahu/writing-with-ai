## Context

现有 `OnboardingScreen` 只让用户勾隐私条款,完成即进入主界面。用户对「为什么填 apikey / 不填能用啥 / 消耗多少 token / 多少钱」完全没概念。AI 能力(扩写/润色/整理/实体抽取/语义兜底)首次调用没有任何前置教育。

约束(roadmap §3.3 / §9 / CLAUDE.md):
- 隐私条款 / apikey 教育状态分别持久化,可独立重置
- 已有 `ConsentStore` 走 DataStore Preferences;`ack_apikey_prompt_v1` 复用相同机制
- 不弹无关弹窗污染 UX;教育只触发一次
- 重置入口在设置页,便于回学或 QA

## Goals / Non-Goals

**Goals:**
- 隐私同意后追加 `ApikeyPromptScreen` 全屏页
- 列出 4 类 AI 能力 + token / 人民币参考成本
- 「我已知晓」勾选后写 `ack_apikey_prompt_v1 = true`,不再重复弹
- AI 操作入口(扩写/润色/整理/实体抽取)首次触发时若未 ack → 拦截 → 弹教育页 → 确认后放行
- 设置页「重置 apikey 教育提示」入口

**Non-Goals:**
- 实时 token 成本显示(provider 计费口径不同,本 change 只给参考范围)
- 强制用户必须填 apikey(填不填都是用户选择)
- 强制同意 apikey 条款(只展示,不签新协议)

## Decisions

### D1 · 两页式 Onboarding flow

把现有 OnboardingScreen 拆成两个串联的 Composable:
1. `ConsentScreen`(原 `OnboardingScreen` 改名)— 隐私条款 + 「同意并继续」按钮
2. `ApikeyPromptScreen`— 能力清单 + 成本说明 + 「我已知晓」勾选 + 「去填 apikey」按钮(跳转设置页)

`AppNav` 启动条件判断:
```
if (!consent.consentAccepted) → navigate(consent)
else if (!userPrefs.ackApikeyPromptV1) → navigate(apikey_prompt)
else → navigate(home)
```

不引入新 NavRoute 嵌套;两个路由平铺,顺序由 AppNav 决定。

### D2 · 能力 + 成本写死常量

```kotlin
object ApikeyPromptContent {
    data class Ability(val name: String, val inputTokens: String, val outputTokens: String, val rmbRange: String)
    val abilities = listOf(
        Ability("扩写 / 润色 / 整理", "500-1500", "1000-3000", "¥0.005-0.02"),
        Ability("实体抽取", "300-600", "100-300", "¥0.001-0.005"),
        Ability("语义兜底关联", "2000-4000", "200-500", "¥0.01-0.05")
    )
    val disclaimer = "以上为参考值,实际以 provider 账单为准。本应用不经手费用。"
}
```

常量写在 `core/prefs/ApikeyPromptContent.kt`,UI 只渲染,不参与任何逻辑。

### D3 · 拦截触发点

`AiGateway.streamWritingOp(...)` 在调用前,新增一层守卫:

```kotlin
fun streamWritingOp(op, sourceText, ...): Flow<AiStreamEvent> {
    return flow {
        if (!userPrefs.ackApikeyPromptV1.first()) {
            emit(Failed(AiError.UserConsentRequired("请先阅读 apikey 教育提示"), recoverable = true))
            return@flow
        }
        emitAll(coreAiGateway.streamWritingOp(op, sourceText, ...))
    }
}
```

**Why in gateway**:统一拦截,任何调用方(详情页 AI 操作 / entity extractor / semantic linker)自动覆盖,不漏。

拦截 → UI 弹 ApikeyPromptScreen dialog(不是 navigate,而是 dialog 弹窗,因为已安装老用户从 AI 入口触发,不在 Onboarding flow)。

### D4 · 重置入口

设置页「隐私与安全」section 加 1 行:
> 重置 apikey 教育提示 → 点击清 `ack_apikey_prompt_v1 = false` → 下次 AI 调用拦截

不重置 `consent_accepted`(隐私条款是法律层面,不能重置)。

### D5 · i18n

新增 key 集合:
- `apikey_prompt_title` / `_subtitle` / `_ability_*` / `_cost_*` / `_disclaimer` / `_ack_checkbox` / `_goto_settings` / `_confirm`
- `reset_apikey_prompt` / `_confirm_dialog_message`

zh + en 双语,英文版先占位 TODO,翻译在 M5 polish 阶段。

## Risks / Trade-offs

| Risk | Mitigation |
| --- | --- |
| 拦截在 gateway 层,可能误拦截 feature 测试用例 | 提供 `bypassConsent: Boolean = false` 参数,测试 / internal call bypass |
| 成本常量过期(provider 调价) | 写死常量 + 注释「参考值,以 provider 账单为准」;真调价留 v2 接入 provider 价格 API |
| 拦截 dialog 弹窗嵌套在 Composable 内,可能与现有 dialog 冲突 | 用顶层 `LocalDialogController` Compose local,统一管理 dialog stack |
| 「重置 apikey 教育提示」被误触 | 设置页二次确认 dialog |

## Migration Plan

1. 升 `ConsentStore` 不动(独立 key)
2. `UserPrefsStore` 增 `ack_apikey_prompt_v1` 键(默认 false)
3. `AiGateway` 接口签名增 `bypassConsent` 默认 false
4. `CoreAiGateway` 实现拦截逻辑
5. UI 落地 Onboarding 改造 + 设置页 + 拦截 dialog
6. **回滚**:旧 APK 装回,DataStore 多余 key 自然忽略,无 schema 迁移

## Open Questions

- apikey 教育页是否要在用户填了 apikey 后才显示?(目前规划:先显示教育,后填 apikey)— 用户随时可跳设置填
- 是否要给一个「跳过 apikey 教育」按钮?(目前规划:必须勾「我已知晓」才能进主界面)— 用户硬要跳过只能装老版本