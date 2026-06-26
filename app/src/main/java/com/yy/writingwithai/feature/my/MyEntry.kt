package com.yy.writingwithai.feature.my

/**
 * app-bottom-tab-bar · 「我的」tab 跨 feature 引用入口。
 *
 * 暴露 `ROUTE_ME` 字符串给 `app/AppNav.kt` 引用,`MyScreen` 自身的渲染逻辑
 * 不通过 `Entry` 暴露(同 `OnboardingEntry` / `ModelManagementEntry` / `AiwritingEntry` 模式)。
 */
object MyEntry {
    /** 与 `app/AppNav.kt` 中 `@Serializable data object Me` 的字符串标识保持一致。 */
    const val ROUTE_ME: String = "me"
}
