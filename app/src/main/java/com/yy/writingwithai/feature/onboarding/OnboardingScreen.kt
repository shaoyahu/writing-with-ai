@file:Suppress("FunctionNaming")

package com.yy.writingwithai.feature.onboarding

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yy.writingwithai.R
import java.util.Locale

/**
 * M4-4 OnboardingScreen — 隐私条款全屏页。
 *
 * spec: openspec/changes/onboarding-consent/specs/onboarding-consent/spec.md
 * "Privacy policy rendered as Markdown with scroll-to-bottom unlock"。
 *
 * Markdown 渲染:M4-4 design D6 决定不引外部 lib(避免版本 resolve + Robolectric 噪声),
 * 写一个 ~80 行的 Markdown 子集渲染器 [SimpleMarkdown],覆盖 # / ## / 段落 / `-` 列表 / `**粗**`。
 * 真 Markdown 完整特性(M3 polish / v1.x 升级)再考虑 compose-markdown。
 */
@Composable
fun OnboardingScreen(
    scrolledToBottom: Boolean,
    onScrolledToBottomChange: (Boolean) -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val context = LocalContext.current
    val policy = remember { loadPrivacyPolicy(context) }
    val blocks = remember(policy) { parseSimpleMarkdown(policy) }

    val listState = rememberLazyListState()
    val canAccept =
        remember(listState, blocks) {
            derivedStateOf {
                val info = listState.layoutInfo
                val total = info.totalItemsCount
                if (total <= 1) return@derivedStateOf false
                val firstVisible = info.visibleItemsInfo.firstOrNull()?.index ?: -1
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                // r1 M3 修:既要"已滚到底"(lastVisible >= total - 1)又要"实际滚过起点"
                // (firstVisible > 0) — 避免短文(total<=1 或 0 滚动)一键同意。
                firstVisible > 0 && lastVisible >= total - 1
            }
        }

    LaunchedEffect(canAccept.value) {
        if (canAccept.value != scrolledToBottom) {
            onScrolledToBottomChange(canAccept.value)
        }
    }

    // ui-redesign-v2 · 品牌头部 + 条款 Surface 卡片包裹
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
                .padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        // ui-redesign-v2 · 条款区域 Surface 卡片包裹
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // L2 fix: Surface 内 LazyColumn 用 fillMaxSize,避免无效的 weight(1f)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("privacy_policy_list"),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = blocks, key = { it.id }) { block ->
                    MarkdownBlockView(block)
                }
            }
        } // Surface close
        if (!scrolledToBottom) {
            Text(
                text = stringResource(R.string.onboarding_scroll_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onAccept,
                enabled = scrolledToBottom,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("accept_button"),
                colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(stringResource(R.string.onboarding_accept))
            }
            OutlinedButton(
                onClick = onReject,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.onboarding_reject))
            }
        }
    }
}

private fun loadPrivacyPolicy(context: Context): String = runCatching {
    val lang = Locale.getDefault().language.lowercase()
    val fileName = if (lang == "en") "privacy_policy_en.md" else "privacy_policy_zh.md"
    context.assets.open(fileName).bufferedReader(Charsets.UTF_8).use { it.readText() }
}.getOrElse { e ->
    e.javaClass.simpleName + ": " + e.message.orEmpty()
}
