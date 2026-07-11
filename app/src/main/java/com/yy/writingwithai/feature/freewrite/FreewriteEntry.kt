package com.yy.writingwithai.feature.freewrite

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * morning-freewrite · `feature/freewrite` 跨 feature 入口。
 *
 * 参照 `AiwritingEntry` 既有模式(自包含约束 — spec §"feature/freewrite/ package is self-contained"):
 * - 其他 feature / AppNav **仅** import `FreewriteEntry`,**不**直接 import `MorningFreewriteViewModel`
 *   / `MorningFreewriteScreen`(避免 layer 泄漏)
 * - `rememberMorningFreewriteViewModel()` 内部走 `hiltViewModel()`,VM scope 跟 caller NavBackStackEntry
 * - `MorningFreewriteRoute(date, onDismiss)` 是 AppNav 编排屏的契约,内部转调 `MorningFreewriteScreen`
 */
object FreewriteEntry {

    /** 给屏内部用:自己拿 VM(屏的 LocalViewModelStoreOwner.current)。 */
    @Composable
    fun rememberMorningFreewriteViewModel(date: String): MorningFreewriteViewModel =
        hiltViewModel<MorningFreewriteViewModel>()

    /**
     * 沉浸晨写屏的跨 feature 入口。`date` 是 ISO `yyyy-MM-dd`,由 Notifier 解析 route extra 后传入。
     * `onDismiss` 在屏 Saved/Failed/back 时调用,AppNav 通常接 `popBackStack()`。
     */
    @Composable
    fun MorningFreewriteRoute(date: String, onDismiss: () -> Unit) {
        // date 通过 hilt SavedStateHandle 透传更"干净",但此处 VM 构造已 OK;
        // 屏渲染仍把 date 传给 Composable(供「日期标题」展示),VM 不直接读。
        MorningFreewriteScreen(date = date, onDismiss = onDismiss)
    }
}
