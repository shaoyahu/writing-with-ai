package com.yy.writingwithai.feature.my

/**
 * app-bottom-tab-bar · "我的" tab 入口的目标路由类型化清单。
 *
 * 由 `MyScreen.onNavigate` 用作参数,`AppShell` 内部翻译为对应 `@Serializable` route 实例。
 * 用 enum 而非裸 String key,编译期捕获拼写错误(review r1 M2 修)。
 */
enum class MeTabTarget {
    SettingsData,
    SettingsModelManagement,
    SettingsPromptTemplate,
    SettingsAliasManagement,

    /** 多用途 Settings hub(AI 关联开关 / 模型 / 提示词 / 别名 / 飞书同步日志) */
    Settings,

    /** 飞书 OAuth 授权(app_id / app_secret 输入 + 连接/断开) */
    FeishuAuth,

    /** 关于(版本号 + 检查更新) */
    About
}
