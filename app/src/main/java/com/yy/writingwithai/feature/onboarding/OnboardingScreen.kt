@file:Suppress("FunctionNaming", "LongMethod")

package com.yy.writingwithai.feature.onboarding

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.yy.writingwithai.R
import com.yy.writingwithai.app.ui.theme.LocalCornerRadius
import com.yy.writingwithai.app.ui.theme.LocalSpacing
import java.util.Locale

/**
 * onboarding-consent-card-redesign · 条款页卡片式重写。
 *
 * 内部 UI 重写为卡片式:
 * - 头部:品牌头部(primaryContainer)
 * - 进度条:`ConsentProgressBar(progress)` 0→1 平滑
 * - 卡片列表:`parseGroupedMarkdown` 5 H2 → 5 `ConsentSectionCard`，首张默认 expanded
 * - 底部栏:`ConsentBottomBar(scrolledToBottom)` tween(300) containerColor 过渡
 *
 * 对外签名 0 改动([scrolledToBottom] / [onScrolledToBottomChange] / [onAccept] / [onReject]),
 * `OnboardingRoute` 不需调整。
 *
 * spec: openspec/changes/onboarding-consent/specs/onboarding-consent/spec.md +
 * openspec/changes/onboarding-consent/specs/consent-page-redesign/spec.md
 */
@Composable
fun OnboardingScreen(
    scrolledToBottom: Boolean,
    onScrolledToBottomChange: (Boolean) -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val context = LocalContext.current
    val policy = remember { loadPrivacyPolicyOrNull(context) }
    val spacing = LocalSpacing.current
    val cornerRadius = LocalCornerRadius.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 品牌头部:primaryContainer 背景 + 大字标题
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(spacing.lg)
        ) {
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(spacing.sm))
            Text(
                text = stringResource(R.string.onboarding_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        if (policy == null) {
            // D4 + §2.1:加载失败 fallback — 进度条(0f)+ 单段错误文案 + 底部栏(accept disabled)
            ConsentPolicyLoadFailedBody(scrolledToBottom = scrolledToBottom, onAccept = onAccept, onDecline = onReject)
        } else {
            // 1.3-1.9 卡片路径
            ConsentPolicyCardBody(
                policy = policy,
                scrolledToBottom = scrolledToBottom,
                onScrolledToBottomChange = onScrolledToBottomChange,
                onAccept = onAccept,
                onDecline = onReject
            )
        }
    }
}

@Composable
private fun ColumnScope.ConsentPolicyLoadFailedBody(
    scrolledToBottom: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val spacing = LocalSpacing.current
    val cornerRadius = LocalCornerRadius.current
    Spacer(modifier = Modifier.height(spacing.sm))
    ConsentProgressBar(progress = 0f, modifier = Modifier.padding(horizontal = spacing.md))
    Spacer(modifier = Modifier.height(spacing.sm))
    Surface(
        shape = RoundedCornerShape(cornerRadius.md),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = spacing.md)
    ) {
        Text(
            text = stringResource(R.string.onboarding_policy_load_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(spacing.md)
        )
    }
    // §2.1:scrolledToBottom=false 走默认 → accept disabled,decline enabled
    ConsentBottomBar(
        scrolledToBottom = scrolledToBottom,
        onAccept = onAccept,
        onDecline = onDecline
    )
}

@Composable
private fun ColumnScope.ConsentPolicyCardBody(
    policy: String,
    scrolledToBottom: Boolean,
    onScrolledToBottomChange: (Boolean) -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    // D2:summaryResolver 闭包内置，按 H2 关键词匹配 5 个 stringRes
    val summaryResolver: (String) -> Int? = { title ->
        when {
            "第三方" in title || "third" in title.lowercase() -> R.string.consent_section_third_party_summary
            "AI" in title || "ai" in title.lowercase() -> R.string.consent_section_ai_summary
            "撤回" in title || "withdraw" in title.lowercase() -> R.string.consent_section_withdraw_summary
            "数据" in title || "data" in title.lowercase() -> R.string.consent_section_data_summary
            "联系" in title || "contact" in title.lowercase() -> R.string.consent_section_contact_summary
            else -> null
        }
    }
    val sections = remember(policy) { parseGroupedMarkdown(policy, summaryResolver) }

    // D1:首张默认 expanded,Set<Int> 支持多张同展
    var expandedSet by remember { mutableStateOf(setOf(0)) }

    val listState = rememberLazyListState()

    // D3:produceState + snapshotFlow 双层;avgItemSize 由 visibleItemsInfo 实时算
    // lint:ProduceStateDoesNotAssignValue 误报 — value 在 .collect { ... } 嵌套 lambda 内赋值，
    // lint 不能跨 lambda 边界追踪 State delegate 写入。
    @Suppress("ProduceStateDoesNotAssignValue")
    val scrollProgress by produceState(0f, listState) {
        snapshotFlow { listState.layoutInfo }.collect { info ->
            value = computeScrollProgress(
                firstVisibleItemIndex = info.visibleItemsInfo.firstOrNull()?.index ?: 0,
                offset = info.visibleItemsInfo.firstOrNull()?.offset ?: 0,
                avgItemSize = info.visibleItemsInfo
                    .sumOf { it.size }
                    .toFloat() / info.visibleItemsInfo.size.coerceAtLeast(1).toFloat(),
                totalItems = info.totalItemsCount
            )
        }
    }

    // D5:双条件 OR — 防短文一键同意
    // 真机 UX 修复:当所有卡片适合 viewport 时，LazyColumn 不可滚动，firstVisible 恒为 0,
    // 旧条件 `firstVisible > 0 && lastVisible >= total - 1` 永远不成立，按钮卡死。
    // 改为:只要 last item 可见即视为"到达底部"(内容已全部展示)。
    val canAccept =
        remember(listState, sections) {
            derivedStateOf {
                val info = listState.layoutInfo
                val total = info.totalItemsCount
                if (total <= 1) return@derivedStateOf false
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                lastVisible >= total - 1 || scrollProgress >= 0.999f
            }
        }

    LaunchedEffect(canAccept.value) {
        if (canAccept.value != scrolledToBottom) {
            onScrolledToBottomChange(canAccept.value)
        }
    }

    Spacer(modifier = Modifier.height(LocalSpacing.current.sm))
    // 1.7:进度条挂载 — 品牌头部下、卡片列表上
    ConsentProgressBar(
        progress = scrollProgress,
        modifier = Modifier.padding(horizontal = LocalSpacing.current.md)
    )
    Spacer(modifier = Modifier.height(LocalSpacing.current.sm))

    // 卡片列表 Surface 包裹(沿用 ui-redesign-v2 视觉)
    Surface(
        shape = RoundedCornerShape(LocalCornerRadius.current.md),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = LocalSpacing.current.md)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("privacy_policy_list"),
            contentPadding = PaddingValues(vertical = LocalSpacing.current.sm),
            verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
        ) {
            // 1.6:items(sections) 渲染 ConsentSectionCard;首张默认 expanded
            items(items = sections, key = { it.id }) { section ->
                ConsentSectionCard(
                    section = section,
                    expanded = section.id in expandedSet,
                    onToggle = {
                        expandedSet = if (section.id in expandedSet) {
                            expandedSet - section.id
                        } else {
                            expandedSet + section.id
                        }
                    }
                )
            }
        }
    }

    // 1.8:底部栏挂载
    ConsentBottomBar(
        scrolledToBottom = scrolledToBottom,
        onAccept = onAccept,
        onDecline = onDecline
    )
}

/**
 * 加载隐私条款。失败返回 null(由调用方走 fallback 路径)。
 *
 * D4:不再 `getOrElse { e.javaClass.simpleName + ... }` 把异常当内容返回(用户看到
 * "IOException: ..." 误导)。新路径显式区分"成功" / "失败":
 * - zh 加载失败 → 尝试 en
 * - en 也失败 → 返回 null
 *
 * 暴露为 `internal` 是为了让 `OnboardingScreenIntegrationTest` 能在 JVM 单测里覆盖失败路径
 * (Robolectric 提供 Application context,assets 目录为空 → 两次 open 都抛 IOException → 返回 null)。
 */
internal fun loadPrivacyPolicyOrNull(context: Context): String? {
    val lang = Locale.getDefault().language.lowercase()
    val primary = if (lang == "en") "privacy_policy_en.md" else "privacy_policy_zh.md"
    val primaryResult = readAssetOrNull(context, primary)
    if (primaryResult != null) return primaryResult
    val fallbackResult = readAssetOrNull(context, "privacy_policy_en.md")
    return fallbackResult
}

private fun readAssetOrNull(context: Context, fileName: String): String? = runCatching {
    context.assets.open(fileName).bufferedReader(Charsets.UTF_8).use { it.readText() }
}.getOrNull()

/**
 * 计算滚动比例(0→1)。
 *
 * D3 公式:`(firstVisibleItemIndex + offset / avgItemSize) / max(total - 1, 1)`
 * - `firstVisibleItemIndex`:最上方可见 item 的 index
 * - `offset`:该 item 已滚过的像素
 * - `avgItemSize`:可见 item 平均高度
 * - `totalItems`:LazyColumn 总 item 数
 *
 * 边界:
 * - `totalItems == 1` → 分母 `max(0, 1) = 1` → 返回 0.0(短文)
 * - `offset == 0 && firstVisible == 0` → 返回 0.0
 * - `firstVisible == total - 1` → 返回 1.0
 */
internal fun computeScrollProgress(
    firstVisibleItemIndex: Int,
    offset: Int,
    avgItemSize: Float,
    totalItems: Int
): Float {
    if (totalItems <= 1) return 0f
    val denom = (totalItems - 1).coerceAtLeast(1).toFloat()
    val safeAvg = if (avgItemSize > 0f) avgItemSize else 1f
    return (firstVisibleItemIndex + offset / safeAvg) / denom
}
