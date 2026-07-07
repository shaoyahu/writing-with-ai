package com.yy.writingwithai.feature.my

/**
 * app-bottom-tab-bar · "我的" tab 入口的目标路由类型化清单。
 *
 * 由 `MyScreen.onNavigate` 用作参数，`AppShell` 内部翻译为对应 `@Serializable` route 实例。
 * 用 enum 而非裸 String key，编译期捕获拼写错误(review r1 M2 修)。
 *
 * 5 条入口(对应 spec 4 Decision 4):
 * - 数据导入/导出 → `SettingsData`
 * - AI 模型管理 → `SettingsModelManagement`
 * - Prompt 模板 → `SettingsPromptTemplate`
 * - 实体别名 → `SettingsAliasManagement`
 * - 飞书同步 → `Settings`(已有 FeishuSyncLogSection)
 *
 * 关于条目为纯展示版本号，不 navigate，因此不在本 enum。
 */
enum class MeTabTarget {
    SettingsData,
    SettingsModelManagement,
    SettingsPromptTemplate,
    SettingsAliasManagement,

    /** 多用途 Settings hub(AI 关联开关 / 模型 / 提示词 / 别名 / 飞书同步日志) */
    Settings,

    /** animation-system-and-consent-redesign §11.1:动画风格设置入口。 */
    SettingsAnimationStyle,

    /** animation-switch-redesign-followup §6.2:动画详细设置入口(nav/tab 细分开关)。 */
    SettingsAnimationDetail,

    /** ux-2026-06-28 P6:飞书授权页专属路由(不再走 Settings hub) */
    SettingsFeishu,

    /** ux-2026-06-28 P6:笔记关联设置专属路由(从数据管理区直达) */
    SettingsNoteAssociation,

    /** language-switcher:APP 内语言切换入口 */
    SettingsLanguage,

    /** entity-management-and-ai-decompose §4.2:实体管理入口 */
    EntityManagement,

    /** entity-management-and-ai-decompose §5.3:开发者选项入口(仅开发者模式启用时显示) */
    DeveloperOptions
}
