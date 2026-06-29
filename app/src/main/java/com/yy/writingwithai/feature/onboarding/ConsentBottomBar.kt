@file:Suppress("FunctionNaming", "LongMethod")

package com.yy.writingwithai.feature.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yy.writingwithai.R

/**
 * animation-system-and-consent-redesign §8.3:底部操作栏(同意 / 拒绝 + 解锁提示)。
 *
 * - 同意按钮 [enabled]=false(滚动未到底部)→ alpha 0.38 + 灰 containerColor;
 *   [enabled]=true → alpha 1.0 + 主题 primary containerColor;
 *   用 [animateColorAsState] + tween(300) 过渡(spec §2.6)
 * - 拒绝按钮始终 enabled(spec:合规拒绝流程不可被灰显阻塞)
 * - 上方提示语"请滚动到底部以继续" / 滚动到底后切换成"已阅读全部条款"
 */
@Composable
fun ConsentBottomBar(
    scrolledToBottom: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val targetContainer: Color = if (scrolledToBottom) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val containerColor by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = tween(durationMillis = 300),
        label = "ConsentBottomBar.containerColor"
    )
    val targetContent: Color = if (scrolledToBottom) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val contentColor by animateColorAsState(
        targetValue = targetContent,
        animationSpec = tween(durationMillis = 300),
        label = "ConsentBottomBar.contentColor"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(
                if (scrolledToBottom) {
                    R.string.consent_bottom_ready
                } else {
                    R.string.consent_bottom_hint
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDecline,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.consent_decline))
            }
            Button(
                onClick = onAccept,
                enabled = scrolledToBottom,
                colors = ButtonDefaults.buttonColors(
                    containerColor = containerColor,
                    contentColor = contentColor
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.consent_accept))
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}
